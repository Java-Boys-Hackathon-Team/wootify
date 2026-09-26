package ru.javaboys.wootify.exchange.marketdata;

import jakarta.annotation.PreDestroy;
import ru.javaboys.wootify.entity.Network;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Синтетические котировки (случайное блуждание) для работы без доступа к Orderly:
 * демонстрации, локальная разработка. Начальная цена любого инструмента - 100.
 */
public class SyntheticMarketDataService implements MarketDataService {

    private static final double STEP_VOLATILITY = 0.001;
    private static final BigDecimal HALF_SPREAD = new BigDecimal("0.0001");

    private final Clock clock;
    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Quote>>> listeners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "synthetic-market-data");
        t.setDaemon(true);
        return t;
    });

    public SyntheticMarketDataService(Clock clock) {
        this.clock = clock;
        scheduler.scheduleAtFixedRate(this::step, 500, 500, TimeUnit.MILLISECONDS);
    }

    private static String key(Network network, String symbol) {
        return network + ":" + symbol;
    }

    private void step() {
        prices.replaceAll((key, price) -> price * (1 + ThreadLocalRandom.current().nextGaussian() * STEP_VOLATILITY));
        prices.forEach(this::publish);
    }

    private void publish(String key, double price) {
        BigDecimal mid = BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP);
        BigDecimal delta = mid.multiply(HALF_SPREAD).setScale(4, RoundingMode.UP);
        String symbol = key.substring(key.indexOf(':') + 1);
        Quote quote = new Quote(symbol, mid.subtract(delta), mid.add(delta), clock.instant());
        quotes.put(key, quote);
        listeners.getOrDefault(key, List.of()).forEach(l -> l.accept(quote));
    }

    private void ensure(Network network, String symbol) {
        String key = key(network, symbol);
        if (prices.putIfAbsent(key, 100.0) == null) {
            publish(key, 100.0);
        }
    }

    @Override
    public Optional<Quote> latestQuote(Network network, String symbol) {
        ensure(network, symbol);
        return Optional.ofNullable(quotes.get(key(network, symbol)));
    }

    @Override
    public Quote freshQuote(Network network, String symbol) {
        return latestQuote(network, symbol)
                .orElseThrow(() -> new MarketDataUnavailableException("Нет цен " + symbol));
    }

    @Override
    public Runnable addListener(Network network, String symbol, Consumer<Quote> listener) {
        ensure(network, symbol);
        String key = key(network, symbol);
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.getOrDefault(key, List.of()).remove(listener);
    }

    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
    }
}
