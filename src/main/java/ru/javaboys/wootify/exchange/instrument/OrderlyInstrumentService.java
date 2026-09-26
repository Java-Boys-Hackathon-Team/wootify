package ru.javaboys.wootify.exchange.instrument;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.exchange.OrderlyProperties;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Параметры инструментов из публичного REST Orderly ({@code /v1/public/info/{symbol}}) с кэшем на час.
 */
public class OrderlyInstrumentService implements InstrumentService {

    private static final Duration TTL = Duration.ofHours(1);

    private final OrderlyProperties properties;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(InstrumentInfo info, Instant loadedAt) {
    }

    public OrderlyInstrumentService(OrderlyProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public InstrumentInfo instrument(Network network, String symbol) {
        String key = network + ":" + symbol;
        Cached cached = cache.get(key);
        if (cached != null && cached.loadedAt().plus(TTL).isAfter(clock.instant())) {
            return cached.info();
        }
        try {
            InstrumentInfo info = load(network, symbol);
            cache.put(key, new Cached(info, clock.instant()));
            return info;
        } catch (InstrumentUnavailableException e) {
            if (cached != null) {
                return cached.info();
            }
            throw e;
        }
    }

    private InstrumentInfo load(Network network, String symbol) {
        URI uri = URI.create(properties.endpoint(network).restUrl() + "/v1/public/info/" + symbol);
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InstrumentUnavailableException("Orderly недоступен: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InstrumentUnavailableException("Прервано", e);
        }
        if (response.statusCode() == 400 || response.statusCode() == 404) {
            throw new IllegalArgumentException("Инструмент " + symbol + " не найден в Orderly (" + network + ")");
        }
        if (response.statusCode() != 200) {
            throw new InstrumentUnavailableException("Orderly ответил " + response.statusCode(), null);
        }
        try {
            JsonNode data = mapper.readTree(response.body()).path("data");
            if (data.isMissingNode() || !data.has("base_tick")) {
                throw new IllegalArgumentException("Инструмент " + symbol + " не найден в Orderly (" + network + ")");
            }
            return new InstrumentInfo(symbol,
                    decimal(data, "quote_tick"), decimal(data, "base_tick"),
                    decimal(data, "base_min"), decimal(data, "base_max"), decimal(data, "min_notional"));
        } catch (IOException e) {
            throw new InstrumentUnavailableException("Некорректный ответ Orderly", e);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? BigDecimal.ZERO : new BigDecimal(value.asText());
    }
}
