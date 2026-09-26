package ru.javaboys.wootify.test_support;

import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.exchange.marketdata.MarketDataService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataUnavailableException;
import ru.javaboys.wootify.exchange.marketdata.Quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Котировки, которые тест задаёт вручную.
 */
public class ManualMarketData implements MarketDataService {

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    public void set(Network network, String symbol, String bid, String ask) {
        quotes.put(network + ":" + symbol, new Quote(symbol, new BigDecimal(bid), new BigDecimal(ask), Instant.now()));
    }

    /** Узкий спред вокруг цены: bid = price - 0.01, ask = price + 0.01. */
    public void setMid(Network network, String symbol, String price) {
        BigDecimal p = new BigDecimal(price);
        set(network, symbol, p.subtract(new BigDecimal("0.01")).toPlainString(), p.add(new BigDecimal("0.01")).toPlainString());
    }

    public void remove(Network network, String symbol) {
        quotes.remove(network + ":" + symbol);
    }

    public void clear() {
        quotes.clear();
    }

    @Override
    public Optional<Quote> latestQuote(Network network, String symbol) {
        return Optional.ofNullable(quotes.get(network + ":" + symbol));
    }

    @Override
    public Quote freshQuote(Network network, String symbol) {
        return latestQuote(network, symbol)
                .orElseThrow(() -> new MarketDataUnavailableException("Нет цен " + symbol));
    }

    @Override
    public Runnable addListener(Network network, String symbol, Consumer<Quote> listener) {
        return () -> {
        };
    }
}
