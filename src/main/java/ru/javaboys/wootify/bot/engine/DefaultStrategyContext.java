package ru.javaboys.wootify.bot.engine;

import ru.javaboys.wootify.bot.strategy.StrategyContext;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.exchange.ExchangeGateway;
import ru.javaboys.wootify.exchange.MarketSymbol;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;
import ru.javaboys.wootify.exchange.instrument.InstrumentService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataService;
import ru.javaboys.wootify.exchange.marketdata.Quote;

import java.time.Clock;
import java.util.Optional;

final class DefaultStrategyContext implements StrategyContext {

    private final Bot bot;
    private final MarketSymbol symbol;
    private final ExchangeGateway exchange;
    private final MarketDataService marketData;
    private final InstrumentService instruments;
    private final BotEventService events;
    private final BotRuntimeRepository runtime;
    private final String instanceId;
    private final BotHandle handle;
    private final Clock clock;
    private volatile String statusMessage;

    DefaultStrategyContext(Bot bot, ExchangeGateway exchange, MarketDataService marketData,
                           InstrumentService instruments, BotEventService events, BotRuntimeRepository runtime,
                           String instanceId, BotHandle handle, Clock clock) {
        this.bot = bot;
        this.symbol = MarketSymbol.of(bot.getSymbol());
        this.exchange = exchange;
        this.marketData = marketData;
        this.instruments = instruments;
        this.events = events;
        this.runtime = runtime;
        this.instanceId = instanceId;
        this.handle = handle;
        this.clock = clock;
    }

    @Override
    public Bot bot() {
        return bot;
    }

    @Override
    public MarketSymbol symbol() {
        return symbol;
    }

    @Override
    public ExchangeGateway exchange() {
        return exchange;
    }

    @Override
    public Quote quote() {
        return marketData.freshQuote(bot.getNetwork(), symbol.orderly());
    }

    @Override
    public InstrumentInfo instrument() {
        return instruments.instrument(bot.getNetwork(), symbol.orderly());
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public void status(String message) {
        this.statusMessage = message;
    }

    String statusMessage() {
        return statusMessage;
    }

    @Override
    public void info(EventCategory category, String message) {
        events.info(bot.getId(), category, message);
    }

    @Override
    public void warn(EventCategory category, String message) {
        events.warn(bot.getId(), category, message);
    }

    @Override
    public Optional<String> loadState() {
        return runtime.loadStrategyState(bot.getId());
    }

    @Override
    public void saveState(String json) {
        if (!runtime.saveStrategyState(bot.getId(), instanceId, json)) {
            handle.signal(new BotHandle.Lost());
            throw new OwnershipLostException();
        }
    }

    @Override
    public boolean stopRequested() {
        return handle.signal() != null;
    }
}
