package ru.javaboys.wootify.bot.strategy.dca;

import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.javaboys.wootify.bot.engine.BotControlService;
import ru.javaboys.wootify.bot.engine.BotEventService;
import ru.javaboys.wootify.bot.engine.BotRunner;
import ru.javaboys.wootify.bot.engine.BotRuntimeRepository;
import ru.javaboys.wootify.bot.engine.BotSupervisor;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotCycle;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.CycleCloseReason;
import ru.javaboys.wootify.entity.CycleStatus;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderRole;
import ru.javaboys.wootify.entity.OrderStatus;
import ru.javaboys.wootify.entity.StopAction;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.entity.TradeType;
import ru.javaboys.wootify.test_support.BotFixtures;
import ru.javaboys.wootify.test_support.IntegrationTest;
import ru.javaboys.wootify.test_support.ManualMarketData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Стратегия DCA от начала до конца: настоящий движок, настоящая стратегия, симулятор биржи и котировки,
 * которые задаёт тест. Инструмент: шаг цены 0.01, шаг объёма 0.001, минимальная стоимость 5.
 */
@IntegrationTest
class DcaStrategyIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    UnconstrainedDataManager dm;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BotControlService control;
    @Autowired
    BotRuntimeRepository runtime;
    @Autowired
    BotEventService events;
    @Autowired
    BotRunner.Dependencies dependencies;
    @Autowired
    ManualMarketData marketData;

    private final List<BotSupervisor> supervisors = new ArrayList<>();
    private String ticker;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        ticker = "PERP_T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "_USDC";
        symbol = BotFixtures.symbol(dm, ticker);
        price("100");
    }

    @AfterEach
    void tearDown() {
        supervisors.forEach(BotSupervisor::shutdownBots);
        jdbc.update("DELETE FROM BOT WHERE NAME LIKE 'dca-%'");
        marketData.remove(Network.MAINNET, ticker);
    }

    // ------------------------------------------------------------------ сценарии

    @Test
    void longCycleAveragesDownAndTakesProfit() {
        Bot bot = bot(b -> {
        }, s -> s.setMaxCycles(1));
        node().start();
        control.start(bot.getId());

        // Базовый ордер по ask 100.01, сетка 98.00 и 96.00, фиксация прибыли 100.01 * 1.01 -> 101.02.
        awaitActive(bot, OrderRole.SAFETY, 2);
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);
        BotCycle cycle = cycle(bot);
        assertThat(cycle.getBasePrice()).isEqualByComparingTo("100.01");
        assertThat(activeOrder(bot, OrderRole.TAKE_PROFIT).getPrice()).isEqualByComparingTo("101.02");
        assertThat(activeOrder(bot, OrderRole.TAKE_PROFIT).getQuantity()).isEqualByComparingTo("0.333");

        // Цена падает до первого страховочного: средняя снижается, фиксация прибыли переставляется.
        price("97.9");
        await().atMost(WAIT).untilAsserted(() -> {
            Order tp = activeOrder(bot, OrderRole.TAKE_PROFIT);
            assertThat(tp.getQuantity()).isEqualByComparingTo("0.673");
            assertThat(tp.getPrice()).isEqualByComparingTo("99.99");
        });
        assertThat(cycle(bot).getFilledSafetyOrders()).isEqualTo(1);

        // Отскок: фиксация прибыли исполняется, оставшаяся сетка отменяется, цикл закрыт с прибылью.
        price("100.5");
        awaitCycleClosed(bot);
        BotCycle closed = cycle(bot);
        assertThat(closed.getCloseReason()).isEqualTo(CycleCloseReason.TAKE_PROFIT);
        assertThat(closed.getExitQty()).isEqualByComparingTo(closed.getEntryQty());
        BigDecimal expectedPnl = closed.getExitProceeds().subtract(closed.getEntryCost()).subtract(closed.getFees());
        assertThat(closed.getRealizedPnl()).isPositive()
                .isEqualByComparingTo(expectedPnl.setScale(8, RoundingMode.HALF_UP));
        assertThat(orders(bot)).filteredOn(o -> o.getRole() == OrderRole.SAFETY && o.getGridLevel() == 2)
                .singleElement().extracting(Order::getStatus).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders(bot)).noneMatch(o -> o.getStatus() == OrderStatus.OPEN);

        // Задан один цикл: бот завершил работу сам.
        await().atMost(WAIT).untilAsserted(() -> assertThat(status(bot)).isEqualTo(BotStatus.STOPPED));
        assertThat(eventMessages(bot)).anyMatch(m -> m.startsWith("Цикл #1 закрыт (фиксация прибыли)"));
    }

    @Test
    void shortCycleTakesProfitWhenPriceFalls() {
        Bot bot = bot(b -> {
        }, s -> {
            s.setDirection(TradeType.SHORT);
            s.setMaxCycles(1);
        });
        node().start();
        control.start(bot.getId());

        // Базовый ордер по bid 99.99, сетка выше: 99.99 * 1.02 -> 101.99 и 99.99 * 1.04 -> 103.99 (вверх).
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);
        assertThat(cycle(bot).getBasePrice()).isEqualByComparingTo("99.99");
        assertThat(orders(bot)).filteredOn(o -> o.getRole() == OrderRole.SAFETY)
                .extracting(Order::getPrice).usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("101.99"), new BigDecimal("103.99"));
        assertThat(activeOrder(bot, OrderRole.TAKE_PROFIT).getPrice()).isEqualByComparingTo("98.99");

        price("98");
        awaitCycleClosed(bot);
        assertThat(cycle(bot).getRealizedPnl()).isPositive();
    }

    @Test
    void stopLossClosesPositionByMarket() {
        Bot bot = bot(b -> {
        }, s -> {
            s.setStopLossPercent(new BigDecimal("6"));
            s.setMaxCycles(1);
        });
        node().start();
        control.start(bot.getId());
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);
        assertThat(cycle(bot).getStopLossPrice()).isEqualByComparingTo("94.00");

        price("93");
        awaitCycleClosed(bot);
        BotCycle closed = cycle(bot);
        assertThat(closed.getCloseReason()).isEqualTo(CycleCloseReason.STOP_LOSS);
        assertThat(closed.getExitQty()).isEqualByComparingTo(closed.getEntryQty());
        assertThat(closed.getRealizedPnl()).isNegative();
        assertThat(orders(bot)).filteredOn(o -> o.getRole() == OrderRole.CLOSE)
                .singleElement().extracting(Order::getStatus).isEqualTo(OrderStatus.CLOSED);
    }

    @Test
    void resumesMidCycleAfterServerRestartWithoutDuplicateOrders() {
        Bot bot = bot(b -> {
        }, s -> s.setMaxCycles(1));
        BotSupervisor before = node();
        before.start();
        control.start(bot.getId());
        awaitActive(bot, OrderRole.SAFETY, 2);
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);

        // Сервер остановлен. Пока его нет, цена доходит до первого страховочного ордера.
        before.stop();
        assertThat(status(bot)).isEqualTo(BotStatus.SUSPENDED);
        price("97.9");
        await().atMost(WAIT).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM PAPER_ORDER WHERE SYMBOL = ? AND STATUS = 'FILLED'", Integer.class, ticker) == 2);

        // Сервер запущен снова: бот видит исполнение и переставляет фиксацию прибыли, не дублируя ордера.
        node().start();
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(activeOrder(bot, OrderRole.TAKE_PROFIT).getQuantity()).isEqualByComparingTo("0.673"));
        List<Order> all = orders(bot);
        assertThat(all).filteredOn(o -> o.getRole() == OrderRole.BASE).hasSize(1);
        assertThat(all).filteredOn(o -> o.getRole() == OrderRole.SAFETY && o.getGridLevel() == 1).hasSize(1);
        assertThat(all).filteredOn(o -> o.getRole() == OrderRole.SAFETY && o.getGridLevel() == 2).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BOT_CYCLE WHERE BOT_ID = ?", Integer.class, bot.getId()))
                .isEqualTo(1);
        assertThat(eventMessages(bot)).contains("Бот продолжил работу после перезапуска сервера");

        price("100.5");
        awaitCycleClosed(bot);
        assertThat(cycle(bot).getRealizedPnl()).isPositive();
    }

    @Test
    void stopWithClosePositionCancelsGridAndClosesCycle() {
        Bot bot = bot(b -> {
        }, s -> {
        });
        node().start();
        control.start(bot.getId());
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);

        control.stop(bot.getId(), StopAction.CLOSE_POSITION);
        await().atMost(WAIT).untilAsserted(() -> assertThat(status(bot)).isEqualTo(BotStatus.STOPPED));
        BotCycle closed = cycle(bot);
        assertThat(closed.getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(closed.getCloseReason()).isEqualTo(CycleCloseReason.MANUAL);
        assertThat(closed.getExitQty()).isEqualByComparingTo("0.333");
        assertThat(orders(bot)).noneMatch(o -> o.getStatus() == OrderStatus.OPEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PAPER_ORDER WHERE SYMBOL = ? AND STATUS = 'NEW'",
                Integer.class, ticker)).isZero();
    }

    @Test
    void stopWithCancelOrdersKeepsPositionAndRestoresGridOnNextStart() {
        Bot bot = bot(b -> {
        }, s -> {
        });
        node().start();
        control.start(bot.getId());
        awaitActive(bot, OrderRole.SAFETY, 2);
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);

        control.stop(bot.getId(), StopAction.CANCEL_ORDERS);
        await().atMost(WAIT).untilAsserted(() -> assertThat(status(bot)).isEqualTo(BotStatus.STOPPED));
        assertThat(cycle(bot).getStatus()).isEqualTo(CycleStatus.OPEN);
        assertThat(cycle(bot).getEntryQty()).isEqualByComparingTo("0.333");
        assertThat(orders(bot)).noneMatch(o -> o.getStatus() == OrderStatus.OPEN);

        control.start(bot.getId());
        awaitActive(bot, OrderRole.SAFETY, 2);
        awaitActive(bot, OrderRole.TAKE_PROFIT, 1);
        assertThat(cycle(bot).getNumber()).isEqualTo(1);
        assertThat(orders(bot)).filteredOn(o -> o.getRole() == OrderRole.BASE).hasSize(1);
        assertThat(activeOrder(bot, OrderRole.TAKE_PROFIT).getClientOrderId()).endsWith("-1");
    }

    @Test
    void tooSmallDepositFailsWithReadableReason() {
        Bot bot = bot(b -> {
        }, s -> {
            s.setDeposit(new BigDecimal("12"));
            s.setOrdersCount(5);
        });
        node().start();
        control.start(bot.getId());
        await().atMost(WAIT).untilAsserted(() -> assertThat(status(bot)).isEqualTo(BotStatus.FAILED));
        assertThat((String) jdbc.queryForObject("SELECT LAST_ERROR FROM BOT_RUNTIME WHERE BOT_ID = ?", String.class,
                bot.getId())).contains("Базовый ордер").contains("меньше минимальной");
        assertThat(orders(bot)).isEmpty();
    }

    // ------------------------------------------------------------------ вспомогательное

    private BotSupervisor node() {
        BotRunner.Dependencies d = dependencies;
        BotSupervisor supervisor = new BotSupervisor(runtime, events, new BotRunner.Dependencies(d.loader(),
                d.strategies(), d.gateways(), d.marketData(), d.instruments(), d.events(), d.runtime(),
                d.properties().forInstance("dca-node"), d.auth(), d.clock()));
        supervisors.add(supervisor);
        return supervisor;
    }

    private Bot bot(Consumer<Bot> botCustomizer, Consumer<ru.javaboys.wootify.entity.DcaSettings> settings) {
        return BotFixtures.bot(dm, "dca-bot", symbol, botCustomizer, settings);
    }

    private void price(String mid) {
        marketData.setMid(Network.MAINNET, ticker, mid);
    }

    private BotStatus status(Bot bot) {
        return BotStatus.fromId(jdbc.queryForObject("SELECT STATUS FROM BOT_RUNTIME WHERE BOT_ID = ?", String.class,
                bot.getId()));
    }

    private BotCycle cycle(Bot bot) {
        return dm.load(BotCycle.class)
                .query("select c from BotCycle c where c.bot.id = :bot order by c.number desc")
                .parameter("bot", bot.getId()).maxResults(1).one();
    }

    private void awaitCycleClosed(Bot bot) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(cycle(bot).getStatus()).isEqualTo(CycleStatus.CLOSED));
    }

    private List<Order> orders(Bot bot) {
        return dm.load(Order.class).query("select o from Order_ o where o.bot.id = :bot order by o.createdDate")
                .parameter("bot", bot.getId()).fetchPlan(FetchPlan.BASE).list();
    }

    private Optional<Order> findActive(Bot bot, OrderRole role) {
        return orders(bot).stream()
                .filter(o -> o.getRole() == role && (o.getStatus() == OrderStatus.OPEN)).reduce((a, b) -> b);
    }

    private Order activeOrder(Bot bot, OrderRole role) {
        return findActive(bot, role).orElseThrow(() -> new AssertionError("Нет активного ордера " + role));
    }

    private void awaitActive(Bot bot, OrderRole role, int count) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(orders(bot))
                .filteredOn(o -> o.getRole() == role && o.getStatus() == OrderStatus.OPEN).hasSize(count));
    }

    private List<String> eventMessages(Bot bot) {
        return jdbc.queryForList("SELECT MESSAGE FROM BOT_EVENT WHERE BOT_ID = ? ORDER BY CREATED_AT", String.class,
                bot.getId());
    }
}
