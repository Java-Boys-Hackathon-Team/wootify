package ru.javaboys.wootify.exchange.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Резервный источник цен: опрос {@code /v1/public/futures} одним запросом на все инструменты сети.
 * Даёт цену маркировки, поэтому bid и ask совпадают. Используется, когда WebSocket недоступен
 * (например, прокси не пропускает WebSocket) или временно молчит.
 */
final class OrderlyRestQuotePoller {

    private static final Logger log = LoggerFactory.getLogger(OrderlyRestQuotePoller.class);

    private final String name;
    private final URI uri;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> symbols = ConcurrentHashMap.newKeySet();
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private volatile boolean failing;

    OrderlyRestQuotePoller(String name, String restUrl, Duration interval, Clock clock) {
        this.name = name;
        this.uri = URI.create(restUrl + "/v1/public/futures");
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orderly-rest-" + name);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void track(String symbol) {
        if (symbols.add(symbol)) {
            scheduler.execute(this::poll);
        }
    }

    Optional<Quote> latest(String symbol) {
        return Optional.ofNullable(quotes.get(symbol));
    }

    Optional<Quote> fresh(String symbol, Duration maxAge) {
        return latest(symbol).filter(q -> q.isFresh(clock.instant(), maxAge));
    }

    void close() {
        scheduler.shutdownNow();
    }

    private void poll() {
        if (symbols.isEmpty()) {
            return;
        }
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            for (JsonNode row : mapper.readTree(response.body()).path("data").path("rows")) {
                String symbol = row.path("symbol").asText();
                if (symbols.contains(symbol) && row.hasNonNull("mark_price")) {
                    BigDecimal price = new BigDecimal(row.get("mark_price").asText());
                    quotes.put(symbol, new Quote(symbol, price, price, clock.instant()));
                }
            }
            if (failing) {
                log.info("[{}] REST quotes restored", name);
                failing = false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!failing) {
                log.warn("[{}] REST quotes poll failed: {}", name, e.toString());
                failing = true;
            }
        }
    }
}
