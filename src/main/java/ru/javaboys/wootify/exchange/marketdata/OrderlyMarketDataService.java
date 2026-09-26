package ru.javaboys.wootify.exchange.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.exchange.OrderlyProperties;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Котировки Orderly: основной источник - публичный WebSocket-поток bbo (по соединению на сеть),
 * резервный - опрос REST с ценой маркировки, когда поток недоступен или молчит.
 */
public class OrderlyMarketDataService implements MarketDataService {

    private final MarketDataProperties properties;
    private final Map<Network, OrderlyQuoteStream> streams = new EnumMap<>(Network.class);
    private final Map<Network, OrderlyRestQuotePoller> pollers = new EnumMap<>(Network.class);

    public OrderlyMarketDataService(OrderlyProperties orderly, MarketDataProperties properties, Clock clock) {
        this.properties = properties;
        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
        ObjectMapper mapper = new ObjectMapper();
        for (Network network : Network.values()) {
            String url = orderly.endpoint(network).wsUrl() + orderly.accountId();
            streams.put(network, new OrderlyQuoteStream(network.name().toLowerCase(), url, client, mapper, clock));
            if (properties.restFallback()) {
                pollers.put(network, new OrderlyRestQuotePoller(network.name().toLowerCase(),
                        orderly.endpoint(network).restUrl(), properties.restPollInterval(), clock));
            }
        }
    }

    @Override
    public Optional<Quote> latestQuote(Network network, String symbol) {
        OrderlyQuoteStream stream = subscribe(network, symbol);
        OrderlyRestQuotePoller poller = pollers.get(network);
        return stream.latest(symbol).or(() -> poller == null ? Optional.empty() : poller.latest(symbol));
    }

    @Override
    public Quote freshQuote(Network network, String symbol) {
        OrderlyQuoteStream stream = subscribe(network, symbol);
        OrderlyRestQuotePoller poller = pollers.get(network);
        return stream.live(symbol, properties.maxQuoteAge())
                .or(() -> poller == null ? Optional.empty() : poller.fresh(symbol, properties.maxQuoteAge()))
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "Нет актуальных цен " + symbol + " (" + network + ")"));
    }

    private OrderlyQuoteStream subscribe(Network network, String symbol) {
        OrderlyQuoteStream stream = streams.get(network);
        stream.subscribe(symbol);
        OrderlyRestQuotePoller poller = pollers.get(network);
        if (poller != null) {
            poller.track(symbol);
        }
        return stream;
    }

    @Override
    public Runnable addListener(Network network, String symbol, Consumer<Quote> listener) {
        return streams.get(network).addListener(symbol, listener);
    }

    @PreDestroy
    public void close() {
        streams.values().forEach(OrderlyQuoteStream::close);
        pollers.values().forEach(OrderlyRestQuotePoller::close);
    }
}
