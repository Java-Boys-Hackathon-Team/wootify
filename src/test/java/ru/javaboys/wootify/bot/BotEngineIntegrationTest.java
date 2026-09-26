package ru.javaboys.wootify.bot;

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
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.DesiredState;
import ru.javaboys.wootify.entity.StopAction;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.test_support.BotFixtures;
import ru.javaboys.wootify.test_support.IntegrationTest;
import ru.javaboys.wootify.test_support.ScriptedStrategy;
import ru.javaboys.wootify.test_support.ScriptedStrategy.Outcome;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Жизненный цикл ботов: запуск, остановка, сбои, перезапуски сервера, владение при нескольких экземплярах.
 * Каждый {@link BotSupervisor} в тесте изображает отдельный экземпляр приложения.
 */
@IntegrationTest
class BotEngineIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    UnconstrainedDataManager dataManager;
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

    private final List<BotSupervisor> supervisors = new ArrayList<>();
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        ScriptedStrategy.reset();
        symbol = BotFixtures.symbol(dataManager, "PERP_TEST_USDC");
    }

    @AfterEach
    void tearDown() {
        supervisors.forEach(BotSupervisor::shutdownBots);
        jdbc.update("DELETE FROM BOT WHERE NAME LIKE 'scripted-%'");
    }

    private BotSupervisor instance(String id) {
        BotRunner.Dependencies d = dependencies;
        BotSupervisor supervisor = new BotSupervisor(runtime, events, new BotRunner.Dependencies(d.loader(),
                d.strategies(), d.gateways(), d.marketData(), d.instruments(), d.events(), d.runtime(),
                d.properties().forInstance(id), d.auth(), d.clock()));
        supervisors.add(supervisor);
        return supervisor;
    }

    private Bot bot(Consumer<Bot> customizer) {
        return BotFixtures.bot(dataManager, "scripted-bot", symbol, customizer, s -> {
        });
    }

    private Map<String, Object> row(Bot bot) {
        return jdbc.queryForMap("SELECT * FROM BOT_RUNTIME WHERE BOT_ID = ?", bot.getId());
    }

    private BotStatus status(Bot bot) {
        return BotStatus.fromId((String) row(bot).get("STATUS"));
    }

    private void awaitStatus(Bot bot, BotStatus expected) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(status(bot)).isEqualTo(expected));
    }

    private List<String> eventMessages(Bot bot) {
        return jdbc.queryForList("SELECT MESSAGE FROM BOT_EVENT WHERE BOT_ID = ? ORDER BY CREATED_AT", String.class,
                bot.getId());
    }

    @Test
    void runtimeRowIsCreatedWithNewBot() {
        Bot bot = bot(b -> {
        });
        assertThat(status(bot)).isEqualTo(BotStatus.STOPPED);
        assertThat(row(bot).get("DESIRED_STATE")).isEqualTo("STOPPED");
    }

    @Test
    void startsTicksAndStopsWithRequestedAction() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());

        control.start(bot.getId());
        awaitStatus(bot, BotStatus.RUNNING);
        await().atMost(WAIT).until(() -> script.ticks.get() >= 2);
        Map<String, Object> running = row(bot);
        assertThat(running.get("OWNER_INSTANCE")).isEqualTo("node-a");
        assertThat(running.get("LAST_HEARTBEAT")).isNotNull();
        assertThat((String) running.get("STATUS_MESSAGE")).startsWith("Такт");
        assertThat(script.instancesStarted).allMatch(name -> name.startsWith("bot-scripted-bot"));

        control.stop(bot.getId(), StopAction.CANCEL_ORDERS);
        awaitStatus(bot, BotStatus.STOPPED);
        assertThat(script.stops).containsExactly(StopAction.CANCEL_ORDERS);
        assertThat(row(bot).get("OWNER_INSTANCE")).isNull();
        assertThat(eventMessages(bot)).anyMatch(m -> m.equals("Бот запущен"))
                .anyMatch(m -> m.startsWith("Бот остановлен"));
    }

    @Test
    void transientErrorsDoNotStopTheBotAndAreVisible() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        script.onTick = n -> n <= 3 ? Outcome.TRANSIENT : Outcome.OK;

        control.start(bot.getId());
        await().atMost(WAIT).until(() -> script.ticks.get() >= 5);
        assertThat(status(bot)).isEqualTo(BotStatus.RUNNING);
        assertThat(script.starts.get()).isEqualTo(1);
        assertThat((String) row(bot).get("LAST_ERROR")).contains("Нет цен");

        Map<String, Object> warning = jdbc.queryForMap(
                "SELECT LEVEL_, REPEAT_COUNT FROM BOT_EVENT WHERE BOT_ID = ? AND MESSAGE LIKE 'Временная ошибка%'",
                bot.getId());
        assertThat(warning.get("LEVEL_")).isEqualTo("WARN");
        assertThat((Integer) warning.get("REPEAT_COUNT")).isEqualTo(3);
        assertThat(eventMessages(bot)).contains("Работа восстановлена после временных ошибок");
    }

    @Test
    void crashedBotIsRestartedWithBackoffUntilAttemptsAreExhausted() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> b.setMaxRestarts(2));
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        script.onTick = n -> Outcome.FATAL;

        control.start(bot.getId());
        awaitStatus(bot, BotStatus.FAILED);
        assertThat(script.starts.get()).isEqualTo(3);
        assertThat((String) row(bot).get("LAST_ERROR")).contains("Сбой стратегии (тест)");
        assertThat(eventMessages(bot))
                .filteredOn(m -> m.startsWith("Бот упал и будет перезапущен")).hasSize(2);
        assertThat(eventMessages(bot)).anyMatch(m -> m.startsWith("Бот упал, попытки перезапуска исчерпаны"));
        String details = jdbc.queryForObject("SELECT DETAILS FROM BOT_EVENT WHERE BOT_ID = ? AND MESSAGE LIKE 'Бот упал, попытки%'",
                String.class, bot.getId());
        assertThat(details).contains("IllegalStateException").contains("ScriptedStrategy.tick");

        // Ручной запуск после исправления причины сбрасывает счётчик попыток.
        script.onTick = n -> Outcome.OK;
        control.start(bot.getId());
        awaitStatus(bot, BotStatus.RUNNING);
        assertThat(row(bot).get("RESTART_COUNT")).isEqualTo(0);
    }

    @Test
    void configurationErrorFailsImmediatelyWithoutRestarts() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> b.setMaxRestarts(5));
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        script.onTick = n -> Outcome.CONFIG;

        control.start(bot.getId());
        awaitStatus(bot, BotStatus.FAILED);
        assertThat(script.starts.get()).isEqualTo(1);
        assertThat(eventMessages(bot)).anyMatch(m -> m.startsWith("Ошибка конфигурации"));
    }

    @Test
    void startIsRejectedForInvalidConfiguration() {
        Bot bot = bot(b -> b.setTradingMode(ru.javaboys.wootify.entity.TradingMode.LIVE));
        assertThatThrownBy(() -> control.start(bot.getId()))
                .isInstanceOf(BotConfigurationException.class)
                .hasMessageContaining("аккаунт и ключ API");
        assertThat(row(bot).get("DESIRED_STATE")).isEqualTo("STOPPED");
    }

    @Test
    void completedStrategyStopsTheBot() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.script(bot.getId()).onTick = n -> n >= 2 ? Outcome.COMPLETE : Outcome.OK;

        control.start(bot.getId());
        await().atMost(WAIT).until(() -> eventMessages(bot).contains("Стратегия завершила работу, бот остановлен"));
        assertThat(status(bot)).isEqualTo(BotStatus.STOPPED);
        assertThat(row(bot).get("DESIRED_STATE")).isEqualTo("STOPPED");
        assertThat(ScriptedStrategy.script(bot.getId()).ticks.get()).isEqualTo(2);
    }

    @Test
    void gracefulServerRestartSuspendsAndResumesBotsWithoutStopActions() {
        BotSupervisor before = instance("node-a");
        before.start();
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        control.start(bot.getId());
        awaitStatus(bot, BotStatus.RUNNING);

        before.stop();
        assertThat(status(bot)).isEqualTo(BotStatus.SUSPENDED);
        assertThat(row(bot).get("DESIRED_STATE")).isEqualTo("RUNNING");
        assertThat(script.stops).isEmpty();

        BotSupervisor after = instance("node-a");
        after.start();
        awaitStatus(bot, BotStatus.RUNNING);
        assertThat(script.starts.get()).isEqualTo(2);
        assertThat(script.stops).isEmpty();
        assertThat(eventMessages(bot)).contains("Бот продолжил работу после перезапуска сервера");
    }

    @Test
    void botOfCrashedInstanceIsTakenOverOnlyAfterLeaseExpires() {
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        // Экземпляр dead-node упал посреди работы: состояние RUNNING, аренда ещё действует.
        jdbc.update("UPDATE BOT_RUNTIME SET DESIRED_STATE = 'RUNNING', STATUS = 'RUNNING', OWNER_INSTANCE = 'dead-node', "
                    + "LEASE_UNTIL = CURRENT_TIMESTAMP + INTERVAL '2 second' WHERE BOT_ID = ?", bot.getId());

        BotSupervisor b = instance("node-b");
        b.reconcile();
        assertThat(script.starts.get()).isZero();

        b.start();
        awaitStatus(bot, BotStatus.RUNNING);
        await().atMost(WAIT).until(() -> script.starts.get() == 1);
        assertThat(row(bot).get("OWNER_INSTANCE")).isEqualTo("node-b");
        assertThat(eventMessages(bot)).contains("Бот продолжил работу после перезапуска сервера");
    }

    @Test
    void sameInstanceReclaimsItsBotsImmediatelyAfterCrash() {
        Bot bot = bot(b -> {
        });
        jdbc.update("UPDATE BOT_RUNTIME SET DESIRED_STATE = 'RUNNING', STATUS = 'RUNNING', OWNER_INSTANCE = 'node-a', "
                    + "LEASE_UNTIL = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE BOT_ID = ?", bot.getId());

        instance("node-a").start();
        awaitStatus(bot, BotStatus.RUNNING);
        await().atMost(WAIT).until(() -> ScriptedStrategy.script(bot.getId()).starts.get() == 1);
    }

    @Test
    void onlyOneOfConcurrentInstancesRunsTheBot() throws Exception {
        List<BotSupervisor> nodes = List.of(instance("node-1"), instance("node-2"), instance("node-3"));
        List<Bot> bots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Bot bot = bot(b -> {
            });
            bots.add(bot);
            runtime.requestStart(bot.getId());
        }
        nodes.forEach(BotSupervisor::start);

        for (Bot bot : bots) {
            awaitStatus(bot, BotStatus.RUNNING);
        }
        Thread.sleep(2000);
        for (Bot bot : bots) {
            assertThat(ScriptedStrategy.script(bot.getId()).starts.get()).as("starts of %s", bot.getName()).isEqualTo(1);
        }
        long running = nodes.stream().mapToLong(n -> n.runningBots().size()).sum();
        assertThat(running).isEqualTo(bots.size());
    }

    @Test
    void runnerStopsWithoutTouchingOrdersWhenOwnershipIsLost() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        control.start(bot.getId());
        awaitStatus(bot, BotStatus.RUNNING);

        jdbc.update("UPDATE BOT_RUNTIME SET OWNER_INSTANCE = 'usurper', LEASE_UNTIL = CURRENT_TIMESTAMP + INTERVAL '1 hour' "
                    + "WHERE BOT_ID = ?", bot.getId());
        await().atMost(WAIT).until(() -> a.runningBots().isEmpty());
        assertThat(script.stops).isEmpty();
        assertThat(row(bot).get("OWNER_INSTANCE")).isEqualTo("usurper");
        assertThat(eventMessages(bot)).anyMatch(m -> m.contains("потерял владение"));
    }

    @Test
    void stoppingBotWaitingForRestartRunsStopActionWithoutRunningStrategy() {
        Bot bot = bot(b -> {
        });
        ScriptedStrategy.Script script = ScriptedStrategy.script(bot.getId());
        jdbc.update("UPDATE BOT_RUNTIME SET DESIRED_STATE = 'RUNNING', STATUS = 'RESTARTING', RESTART_COUNT = 1, "
                    + "NEXT_RESTART_AT = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE BOT_ID = ?", bot.getId());

        control.stop(bot.getId(), StopAction.CLOSE_POSITION);
        instance("node-a").start();
        awaitStatus(bot, BotStatus.STOPPED);
        assertThat(script.stops).containsExactly(StopAction.CLOSE_POSITION);
        assertThat(script.ticks.get()).isZero();
    }

    @Test
    void stoppingIdleBotWithKeepOrdersJustMarksItStopped() {
        Bot bot = bot(b -> {
        });
        jdbc.update("UPDATE BOT_RUNTIME SET DESIRED_STATE = 'RUNNING', STATUS = 'SUSPENDED' WHERE BOT_ID = ?", bot.getId());
        control.stop(bot.getId(), StopAction.KEEP_ORDERS);
        instance("node-a").reconcile();
        assertThat(status(bot)).isEqualTo(BotStatus.STOPPED);
        assertThat(ScriptedStrategy.script(bot.getId()).stops).isEmpty();
        assertThat(row(bot).get("DESIRED_STATE")).isEqualTo(DesiredState.STOPPED.getId());
    }

    @Test
    void runningBotCannotBeEditedOrDeleted() {
        BotSupervisor a = instance("node-a");
        a.start();
        Bot bot = bot(b -> {
        });
        control.start(bot.getId());
        awaitStatus(bot, BotStatus.RUNNING);
        assertThat(control.isEditable(bot.getId())).isFalse();
        assertThatThrownBy(() -> control.delete(bot.getId())).isInstanceOf(IllegalStateException.class);

        control.stop(bot.getId(), StopAction.KEEP_ORDERS);
        awaitStatus(bot, BotStatus.STOPPED);
        assertThat(control.isEditable(bot.getId())).isTrue();
        control.delete(bot.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BOT_RUNTIME WHERE BOT_ID = ?", Integer.class, bot.getId()))
                .isZero();
        assertThat(dataManager.load(Bot.class).id(bot.getId()).optional()).isEmpty();
        UUID unused = UUID.randomUUID();
        assertThat(control.isEditable(unused)).isTrue();
    }
}
