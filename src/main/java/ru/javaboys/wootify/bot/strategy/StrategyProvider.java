package ru.javaboys.wootify.bot.strategy;

import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.StrategyType;

import java.util.List;

/**
 * Фабрика стратегии определённого типа. Регистрируется как Spring-бин.
 */
public interface StrategyProvider {

    StrategyType type();

    /**
     * Проверка настроек без обращения к бирже.
     *
     * @return список проблем; пустой, если настройки корректны
     */
    List<String> validate(Bot bot);

    TradingStrategy create(Bot bot);
}
