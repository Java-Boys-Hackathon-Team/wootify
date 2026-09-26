package ru.javaboys.wootify.bot.strategy;

import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.exchange.ExchangeGateway;
import ru.javaboys.wootify.exchange.MarketSymbol;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;
import ru.javaboys.wootify.exchange.marketdata.Quote;

import java.time.Clock;
import java.util.Optional;

/**
 * Всё, что движок предоставляет стратегии на время одного запуска бота.
 */
public interface StrategyContext {

    /** Снимок конфигурации на момент запуска: изменения настроек применяются при следующем запуске. */
    Bot bot();

    MarketSymbol symbol();

    ExchangeGateway exchange();

    /** Свежая котировка инструмента бота; при её отсутствии - временный сбой. */
    Quote quote();

    InstrumentInfo instrument();

    Clock clock();

    /** Текущее занятие бота, показывается в UI. */
    void status(String message);

    void info(EventCategory category, String message);

    void warn(EventCategory category, String message);

    /** Произвольное состояние стратегии (JSON), сохраняется в БД сразу. */
    Optional<String> loadState();

    void saveState(String json);

    /** Запрошена ли остановка: длинные операции внутри такта могут проверять и завершаться раньше. */
    boolean stopRequested();
}
