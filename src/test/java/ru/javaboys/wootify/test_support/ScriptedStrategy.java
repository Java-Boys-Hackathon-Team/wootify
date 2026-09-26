package ru.javaboys.wootify.test_support;

import ru.javaboys.wootify.bot.strategy.StrategyContext;
import ru.javaboys.wootify.bot.strategy.StrategyProvider;
import ru.javaboys.wootify.bot.strategy.TickResult;
import ru.javaboys.wootify.bot.strategy.TradingStrategy;
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.StopAction;
import ru.javaboys.wootify.entity.StrategyType;
import ru.javaboys.wootify.exchange.marketdata.MarketDataUnavailableException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * Стратегия со сценарием, который задаёт тест: считает вызовы и выполняет заданное поведение на каждом такте.
 */
public class ScriptedStrategy implements TradingStrategy {

    public enum Outcome {OK, TRANSIENT, FATAL, CONFIG, COMPLETE}

    public static final class Script {
        public final AtomicInteger starts = new AtomicInteger();
        public final AtomicInteger ticks = new AtomicInteger();
        public final List<StopAction> stops = new CopyOnWriteArrayList<>();
        public final List<String> instancesStarted = new CopyOnWriteArrayList<>();
        public volatile IntFunction<Outcome> onTick = n -> Outcome.OK;
    }

    private static final Map<UUID, Script> SCRIPTS = new ConcurrentHashMap<>();

    public static Script script(UUID botId) {
        return SCRIPTS.computeIfAbsent(botId, id -> new Script());
    }

    public static void reset() {
        SCRIPTS.clear();
    }

    private final Script script;

    ScriptedStrategy(Bot bot) {
        this.script = script(bot.getId());
    }

    @Override
    public void start(StrategyContext ctx) {
        script.starts.incrementAndGet();
        script.instancesStarted.add(Thread.currentThread().getName());
    }

    @Override
    public TickResult tick(StrategyContext ctx) {
        int n = script.ticks.incrementAndGet();
        ctx.status("Такт " + n);
        return switch (script.onTick.apply(n)) {
            case OK -> TickResult.CONTINUE;
            case COMPLETE -> TickResult.COMPLETED;
            case TRANSIENT -> throw new MarketDataUnavailableException("Нет цен (тест)");
            case FATAL -> throw new IllegalStateException("Сбой стратегии (тест)");
            case CONFIG -> throw new BotConfigurationException("Неверные настройки (тест)");
        };
    }

    @Override
    public void stop(StrategyContext ctx, StopAction action) {
        script.stops.add(action);
    }

    public static class Provider implements StrategyProvider {
        @Override
        public StrategyType type() {
            return StrategyType.DCA;
        }

        @Override
        public List<String> validate(Bot bot) {
            return List.of();
        }

        @Override
        public TradingStrategy create(Bot bot) {
            return new ScriptedStrategy(bot);
        }
    }
}
