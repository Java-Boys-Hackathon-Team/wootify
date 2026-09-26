package ru.javaboys.wootify.test_support;

import ru.javaboys.wootify.bot.strategy.StrategyProvider;
import ru.javaboys.wootify.bot.strategy.StrategyRegistry;
import ru.javaboys.wootify.entity.Bot;

import java.util.List;

/**
 * Боты с именем {@code scripted-*} исполняют {@link ScriptedStrategy}, остальные - настоящие стратегии.
 */
public class ScriptedStrategyRegistry extends StrategyRegistry {

    public static final String PREFIX = "scripted-";

    private final StrategyProvider scripted = new ScriptedStrategy.Provider();

    public ScriptedStrategyRegistry(List<StrategyProvider> providers) {
        super(providers);
    }

    @Override
    public StrategyProvider provider(Bot bot) {
        return bot.getName() != null && bot.getName().startsWith(PREFIX) ? scripted : super.provider(bot);
    }
}
