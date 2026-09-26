package ru.javaboys.wootify.bot.engine;

import io.jmix.core.security.SystemAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.javaboys.wootify.bot.strategy.StrategyProvider;
import ru.javaboys.wootify.bot.strategy.StrategyRegistry;
import ru.javaboys.wootify.bot.strategy.TickResult;
import ru.javaboys.wootify.bot.strategy.TradingStrategy;
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.entity.StopAction;
import ru.javaboys.wootify.exchange.ExchangeGateway;
import ru.javaboys.wootify.exchange.ExchangeGatewayFactory;
import ru.javaboys.wootify.exchange.instrument.InstrumentService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Исполняет одного бота в собственном потоке: загрузка конфигурации, цикл тактов стратегии,
 * обработка сбоев и сигналов остановки.
 * <p>
 * Реакция на сбои:
 * <ul>
 *     <li>временный сбой - запись в журнал, пауза, повтор такта; если сбои не проходят дольше
 *     {@code transientErrorLimit}, это считается падением;</li>
 *     <li>ошибка конфигурации - сразу FAILED, перезапуск бесполезен;</li>
 *     <li>прочие исключения - падение: перезапуск с нарастающей паузой, после исчерпания попыток FAILED.</li>
 * </ul>
 */
public final class BotRunner implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BotRunner.class);
    private static final Duration STOP_RETRY_PAUSE = Duration.ofSeconds(2);
    private static final Duration FIRST_QUOTE_WAIT = Duration.ofSeconds(10);

    public record Dependencies(BotLoader loader, StrategyRegistry strategies, ExchangeGatewayFactory gateways,
                        MarketDataService marketData, InstrumentService instruments, BotEventService events,
                        BotRuntimeRepository runtime, BotEngineProperties properties, SystemAuthenticator auth,
                        Clock clock) {
    }

    private final BotHandle handle;
    private final BotRuntimeRepository.Control before;
    private final Dependencies d;
    private final UUID botId;
    private final String instance;

    private Bot bot;
    private TradingStrategy strategy;
    private DefaultStrategyContext ctx;

    BotRunner(BotHandle handle, BotRuntimeRepository.Control before, Dependencies dependencies) {
        this.handle = handle;
        this.before = before;
        this.d = dependencies;
        this.botId = handle.botId();
        this.instance = dependencies.properties().instanceId();
    }

    @Override
    public void run() {
        MDC.put("botId", botId.toString());
        try {
            d.auth().runWithSystem(this::execute);
        } catch (Throwable e) {
            log.error("Bot {} runner terminated unexpectedly", botId, e);
        } finally {
            MDC.remove("botId");
        }
    }

    private void execute() {
        try {
            prepare();
        } catch (Throwable e) {
            if (handle.mode() == BotRuntimeRepository.ClaimMode.FINALIZE_STOP) {
                failStop(e);
            } else {
                crash(e);
            }
            return;
        }
        if (handle.mode() == BotRuntimeRepository.ClaimMode.FINALIZE_STOP) {
            StopAction action = before.requestedStopAction() != null ? before.requestedStopAction() : bot.getStopAction();
            stop(action, false);
            return;
        }
        if (!d.runtime().markRunning(botId, instance)) {
            onLost();
            return;
        }
        d.events().info(botId, EventCategory.LIFECYCLE, startMessage());
        awaitFirstQuote();
        loop();
    }

    private void prepare() {
        bot = d.loader().load(botId).orElseThrow(() -> new BotConfigurationException("Бот не найден"));
        Thread.currentThread().setName("bot-" + bot.getName().replaceAll("[^\\p{L}\\p{N}_.-]", "_"));
        StrategyProvider provider = d.strategies().provider(bot);
        List<String> problems = provider.validate(bot);
        if (!problems.isEmpty()) {
            throw new BotConfigurationException(String.join("; ", problems));
        }
        ExchangeGateway gateway = d.gateways().forBot(bot);
        strategy = provider.create(bot);
        ctx = new DefaultStrategyContext(bot, gateway, d.marketData(), d.instruments(), d.events(), d.runtime(),
                instance, handle, d.clock());
    }

    /**
     * Подписка на цены инструмента оформляется при первом обращении; даём первой котировке прийти,
     * чтобы старт не начинался с временной ошибки.
     */
    private void awaitFirstQuote() {
        Instant deadline = d.clock().instant().plus(FIRST_QUOTE_WAIT);
        while (d.marketData().latestQuote(bot.getNetwork(), bot.getSymbol().getWoofiTicker()).isEmpty()
               && d.clock().instant().isBefore(deadline) && handle.signal() == null) {
            try {
                handle.awaitSignal(Duration.ofMillis(250));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String startMessage() {
        if (before.restartCount() > 0) {
            return "Бот перезапущен после сбоя (попытка " + before.restartCount() + " из " + bot.getMaxRestarts() + ")";
        }
        if (before.status() == BotStatus.SUSPENDED || before.status() == BotStatus.RUNNING
            || before.status() == BotStatus.STARTING) {
            return "Бот продолжил работу после перезапуска сервера";
        }
        return "Бот запущен";
    }

    private void loop() {
        boolean started = false;
        Instant transientSince = null;
        boolean restartCountReset = before.restartCount() == 0;
        Instant runStart = d.clock().instant();
        Duration tick = Duration.ofSeconds(Math.max(1, bot.getTickIntervalSec()));

        while (true) {
            BotHandle.Signal signal = handle.signal();
            if (signal != null) {
                onSignal(signal);
                return;
            }
            try {
                if (!started) {
                    strategy.start(ctx);
                    started = true;
                }
                TickResult result = strategy.tick(ctx);
                if (transientSince != null) {
                    d.events().info(botId, EventCategory.SYSTEM, "Работа восстановлена после временных ошибок");
                    d.runtime().clearError(botId, instance);
                    transientSince = null;
                }
                if (!d.runtime().heartbeat(botId, instance, ctx.statusMessage())) {
                    throw new OwnershipLostException();
                }
                handle.progress();
                if (!restartCountReset && d.clock().instant().isAfter(runStart.plus(d.properties().healthyRunDuration()))) {
                    d.runtime().resetRestartCount(botId, instance);
                    restartCountReset = true;
                }
                if (result == TickResult.COMPLETED) {
                    d.runtime().markCompleted(botId, instance, ctx.statusMessage());
                    d.events().info(botId, EventCategory.LIFECYCLE, "Стратегия завершила работу, бот остановлен");
                    return;
                }
            } catch (OwnershipLostException e) {
                handle.signal(new BotHandle.Lost());
                continue;
            } catch (Throwable e) {
                if (handle.signal() instanceof BotHandle.Lost) {
                    continue;
                }
                if (!Failures.isTransient(e)) {
                    crash(e);
                    return;
                }
                Instant now = d.clock().instant();
                transientSince = transientSince == null ? now : transientSince;
                String message = Failures.message(e);
                d.events().warn(botId, EventCategory.SYSTEM, "Временная ошибка: " + message);
                d.runtime().recordError(botId, instance, Failures.truncate(message, 4000),
                        Failures.truncate("Ожидание: " + message, 1000));
                handle.progress();
                if (Duration.between(transientSince, now).compareTo(d.properties().transientErrorLimit()) > 0) {
                    crash(new IllegalStateException("Временные ошибки не проходят дольше "
                                                    + d.properties().transientErrorLimit().toMinutes() + " мин: " + message, e));
                    return;
                }
            }
            try {
                handle.awaitSignal(tick);
            } catch (InterruptedException e) {
                handle.signal(new BotHandle.Shutdown());
            }
        }
    }

    private void onSignal(BotHandle.Signal signal) {
        switch (signal) {
            case BotHandle.Lost lost -> onLost();
            case BotHandle.Shutdown shutdown -> {
                d.runtime().markSuspended(botId, instance);
                d.events().info(botId, EventCategory.LIFECYCLE,
                        "Бот приостановлен: сервер останавливается. Работа продолжится после запуска");
            }
            case BotHandle.Stop stop -> stop(stop.action(), true);
        }
    }

    private void onLost() {
        d.events().warn(botId, EventCategory.LIFECYCLE,
                "Экземпляр " + instance + " потерял владение ботом; поток остановлен без действий над ордерами");
    }

    /**
     * Остановка по запросу пользователя. Временные сбои при отмене ордеров повторяются до
     * {@code stopTimeout}: оставить ордера висеть без присмотра хуже, чем подождать.
     */
    private void stop(StopAction action, boolean markStopping) {
        if (markStopping && !d.runtime().markStopping(botId, instance)) {
            onLost();
            return;
        }
        Instant deadline = d.clock().instant().plus(d.properties().stopTimeout());
        while (true) {
            try {
                strategy.stop(ctx, action);
                break;
            } catch (Throwable e) {
                if (handle.signal() instanceof BotHandle.Lost) {
                    onLost();
                    return;
                }
                if (!Failures.isTransient(e) || d.clock().instant().isAfter(deadline)) {
                    failStop(e);
                    return;
                }
                d.events().warn(botId, EventCategory.SYSTEM, "Остановка: временная ошибка, повтор: " + Failures.message(e));
                try {
                    Thread.sleep(STOP_RETRY_PAUSE);
                } catch (InterruptedException ie) {
                    failStop(e);
                    return;
                }
            }
        }
        d.runtime().markStopped(botId, instance, null);
        d.events().info(botId, EventCategory.LIFECYCLE, "Бот остановлен (" + describe(action) + ")");
    }

    private void failStop(Throwable e) {
        String message = "Не удалось выполнить остановку: " + Failures.message(e);
        d.runtime().markFailed(botId, instance, Failures.truncate(message, 4000));
        d.events().error(botId, EventCategory.LIFECYCLE, message, e);
    }

    private void crash(Throwable e) {
        String message = Failures.message(e);
        if (Failures.isConfiguration(e)) {
            d.runtime().markFailed(botId, instance, Failures.truncate(message, 4000));
            d.events().error(botId, EventCategory.LIFECYCLE, "Ошибка конфигурации, бот остановлен: " + message, null);
            return;
        }
        int maxRestarts = bot != null ? bot.getMaxRestarts() : 0;
        BotStatus status = d.runtime().markCrashed(botId, instance, Failures.truncate(message, 4000), maxRestarts,
                d.properties().restartBackoff(), d.properties().maxRestartBackoff());
        if (status == null) {
            onLost();
        } else if (status == BotStatus.FAILED) {
            d.events().error(botId, EventCategory.LIFECYCLE,
                    "Бот упал, попытки перезапуска исчерпаны: " + message, e);
        } else {
            d.events().error(botId, EventCategory.LIFECYCLE, "Бот упал и будет перезапущен: " + message, e);
        }
    }

    static String describe(StopAction action) {
        return switch (action) {
            case KEEP_ORDERS -> "ордера и позиция оставлены";
            case CANCEL_ORDERS -> "ордера отменены, позиция оставлена";
            case CLOSE_POSITION -> "ордера отменены, позиция закрыта";
        };
    }
}
