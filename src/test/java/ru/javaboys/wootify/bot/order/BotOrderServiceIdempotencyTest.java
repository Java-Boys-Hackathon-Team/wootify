package ru.javaboys.wootify.bot.order;

import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.javaboys.wootify.bot.strategy.StrategyContext;
import ru.javaboys.wootify.entity.*;
import ru.javaboys.wootify.exchange.ExchangeException;
import ru.javaboys.wootify.exchange.ExchangeGateway;
import ru.javaboys.wootify.exchange.ExchangeOrder;
import ru.javaboys.wootify.exchange.MarketSymbol;
import ru.javaboys.wootify.exchange.PlaceOrderCommand;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;
import ru.javaboys.wootify.exchange.marketdata.Quote;
import ru.javaboys.wootify.exchange.paper.PaperExchange;
import ru.javaboys.wootify.exchange.paper.PaperExchangeGateway;
import ru.javaboys.wootify.test_support.BotFixtures;
import ru.javaboys.wootify.test_support.IntegrationTest;
import ru.javaboys.wootify.test_support.ManualMarketData;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Сбои между записью ордера в БД и ответом биржи не приводят ни к потере, ни к дублю ордера.
 */
@IntegrationTest
class BotOrderServiceIdempotencyTest {

    @Autowired
    UnconstrainedDataManager dm;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BotOrderService orders;
    @Autowired
    PaperExchange paper;
    @Autowired
    ManualMarketData marketData;

    private String ticker;
    private Bot bot;
    private BotCycle cycle;

    /** Шлюз, который может потерять ответ после размещения или упасть до него. */
    static final class FlakyGateway implements ExchangeGateway {
        final ExchangeGateway delegate;
        volatile boolean failBeforeSend;
        volatile boolean loseResponse;
        int placed;

        FlakyGateway(ExchangeGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public ExchangeOrder placeOrder(PlaceOrderCommand command) {
            if (failBeforeSend) {
                throw new ExchangeException.Transient("соединение сброшено до отправки", null);
            }
            ExchangeOrder result = delegate.placeOrder(command);
            placed++;
            if (loseResponse) {
                throw new ExchangeException.Transient("таймаут чтения ответа", null);
            }
            return result;
        }

        @Override
        public Optional<ExchangeOrder> findOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId) {
            return delegate.findOrder(symbol, clientOrderId, exchangeOrderId);
        }

        @Override
        public void cancelOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId) {
            delegate.cancelOrder(symbol, clientOrderId, exchangeOrderId);
        }

        @Override
        public void setLeverage(MarketSymbol symbol, int leverage) {
        }
    }

    private StrategyContext context(ExchangeGateway gateway) {
        MarketSymbol symbol = MarketSymbol.of(bot.getSymbol());
        return new StrategyContext() {
            public Bot bot() { return bot; }
            public MarketSymbol symbol() { return symbol; }
            public ExchangeGateway exchange() { return gateway; }
            public Quote quote() { return marketData.freshQuote(Network.MAINNET, ticker); }
            public InstrumentInfo instrument() { return null; }
            public Clock clock() { return Clock.systemUTC(); }
            public void status(String message) { }
            public void info(EventCategory category, String message) { }
            public void warn(EventCategory category, String message) { }
            public Optional<String> loadState() { return Optional.empty(); }
            public void saveState(String json) { }
            public boolean stopRequested() { return false; }
        };
    }

    @BeforeEach
    void setUp() {
        ticker = "PERP_I" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "_USDC";
        marketData.setMid(Network.MAINNET, ticker, "100");
        Symbol symbol = BotFixtures.symbol(dm, ticker);
        bot = BotFixtures.bot(dm, "idem-bot", symbol, b -> {
        }, s -> {
        });
        cycle = dm.create(BotCycle.class);
        cycle.setBot(bot);
        cycle.setNumber(1);
        cycle.setStatus(CycleStatus.OPEN);
        cycle.setDirection(TradeType.LONG);
        cycle.setStartedAt(OffsetDateTime.now());
        cycle.setEntryQty(BigDecimal.ZERO);
        cycle.setEntryCost(BigDecimal.ZERO);
        cycle.setExitQty(BigDecimal.ZERO);
        cycle.setExitProceeds(BigDecimal.ZERO);
        cycle.setFees(BigDecimal.ZERO);
        cycle.setFilledSafetyOrders(0);
        cycle = dm.save(cycle);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM BOT WHERE NAME LIKE 'idem-bot%'");
    }

    private int paperOrders() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM PAPER_ORDER WHERE SYMBOL = ?", Integer.class, ticker);
    }

    @Test
    void lostResponseIsRecoveredByClientOrderIdWithoutDuplicate() {
        FlakyGateway gateway = new FlakyGateway(new PaperExchangeGateway(paper, Network.MAINNET));
        gateway.loseResponse = true;
        StrategyContext ctx = context(gateway);

        assertThatThrownBy(() -> orders.place(ctx, cycle, OrderRole.SAFETY, 1, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("1"), new BigDecimal("95"), false))
                .isInstanceOf(ExchangeException.Transient.class);
        Order local = orders.ordersOf(cycle).getFirst();
        assertThat(local.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(paperOrders()).isEqualTo(1);

        gateway.loseResponse = false;
        Order synced = orders.sync(ctx, local);
        assertThat(synced.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(synced.getOrderlyOrderId()).isNotNull();
        assertThat(gateway.placed).isEqualTo(1);
        assertThat(paperOrders()).isEqualTo(1);
    }

    @Test
    void orderNotSentBeforeCrashIsResentWithSameClientOrderId() {
        FlakyGateway gateway = new FlakyGateway(new PaperExchangeGateway(paper, Network.MAINNET));
        gateway.failBeforeSend = true;
        StrategyContext ctx = context(gateway);

        assertThatThrownBy(() -> orders.place(ctx, cycle, OrderRole.BASE, 0, OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("1"), null, false))
                .isInstanceOf(ExchangeException.Transient.class);
        Order local = orders.ordersOf(cycle).getFirst();
        assertThat(paperOrders()).isZero();

        gateway.failBeforeSend = false;
        Order synced = orders.sync(ctx, local);
        assertThat(synced.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(synced.getClientOrderId()).isEqualTo(local.getClientOrderId());
        assertThat(synced.getAverageExecutedPrice()).isEqualByComparingTo("100.01");
        assertThat(paperOrders()).isEqualTo(1);
    }

    @Test
    void cancellingOrderThatNeverReachedExchangeJustMarksItCancelled() {
        FlakyGateway gateway = new FlakyGateway(new PaperExchangeGateway(paper, Network.MAINNET));
        gateway.failBeforeSend = true;
        StrategyContext ctx = context(gateway);
        assertThatThrownBy(() -> orders.place(ctx, cycle, OrderRole.SAFETY, 2, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("1"), new BigDecimal("90"), false));
        gateway.failBeforeSend = false;

        Order cancelled = orders.cancel(ctx, orders.ordersOf(cycle).getFirst());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paperOrders()).isZero();
    }
}
