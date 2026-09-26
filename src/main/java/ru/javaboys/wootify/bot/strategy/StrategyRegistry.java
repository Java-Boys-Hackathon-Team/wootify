package ru.javaboys.wootify.bot.strategy;

import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.StrategyType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class StrategyRegistry {

    private final Map<StrategyType, StrategyProvider> providers = new EnumMap<>(StrategyType.class);

    public StrategyRegistry(List<StrategyProvider> providers) {
        providers.forEach(p -> this.providers.put(p.type(), p));
    }

    public StrategyProvider provider(Bot bot) {
        StrategyProvider provider = bot.getStrategyType() == null ? null : providers.get(bot.getStrategyType());
        if (provider == null) {
            throw new BotConfigurationException("Стратегия " + bot.getStrategyType() + " не поддерживается");
        }
        return provider;
    }
}
