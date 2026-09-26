package ru.javaboys.wootify.exchange.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Одно WebSocket-соединение с публичным потоком Orderly для одной сети.
 * <p>
 * Подписки на {@code <symbol>@bbo} переживают разрывы: после переподключения они
 * восстанавливаются автоматически. Котировка считается актуальной, пока соединение, на котором
 * она получена, живо: Orderly присылает bbo только при изменении цены, поэтому у малоликвидного
 * инструмента цена может не меняться долго, оставаясь верной.
 */
final class OrderlyQuoteStream {

    private static final Logger log = LoggerFactory.getLogger(OrderlyQuoteStream.class);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(45);

    private final String name;
    private final String url;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    private final Set<String> symbols = ConcurrentHashMap.newKeySet();
    private final Map<String, StampedQuote> quotes = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Quote>>> listeners = new ConcurrentHashMap<>();

    private final Object lock = new Object();
    private WebSocket webSocket;
    private long connectionEpoch;
    private boolean connected;
    private int attempt;
    private boolean closed;
    private volatile Instant lastMessageAt = Instant.EPOCH;

    private record StampedQuote(Quote quote, long epoch) {
    }

    OrderlyQuoteStream(String name, String url, OkHttpClient client, ObjectMapper mapper, Clock clock) {
        this.name = name;
        this.url = url;
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orderly-ws-" + name);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::watchdog, 10, 10, TimeUnit.SECONDS);
    }

    void subscribe(String symbol) {
        if (!symbols.add(symbol)) {
            return;
        }
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (webSocket == null) {
                connect();
            } else if (connected) {
                sendSubscribe(webSocket, symbol);
            }
        }
    }

    Optional<Quote> latest(String symbol) {
        StampedQuote sq = quotes.get(symbol);
        return sq == null ? Optional.empty() : Optional.of(sq.quote());
    }

    /**
     * Котировка годна, если получена на текущем живом соединении либо сама достаточно свежая.
     */
    Optional<Quote> live(String symbol, Duration maxAge) {
        StampedQuote sq = quotes.get(symbol);
        if (sq == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (sq.quote().isFresh(now, maxAge)) {
            return Optional.of(sq.quote());
        }
        synchronized (lock) {
            boolean alive = connected && sq.epoch() == connectionEpoch
                    && !lastMessageAt.plus(maxAge).isBefore(now);
            return alive ? Optional.of(sq.quote()) : Optional.empty();
        }
    }

    Runnable addListener(String symbol, Consumer<Quote> listener) {
        listeners.computeIfAbsent(symbol, s -> new CopyOnWriteArrayList<>()).add(listener);
        subscribe(symbol);
        return () -> listeners.getOrDefault(symbol, List.of()).remove(listener);
    }

    void close() {
        synchronized (lock) {
            closed = true;
            if (webSocket != null) {
                webSocket.close(1000, "shutdown");
            }
        }
        scheduler.shutdownNow();
    }

    private void connect() {
        long epoch = ++connectionEpoch;
        log.info("[{}] Connecting to {}", name, url);
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new Listener(epoch));
    }

    private void scheduleReconnect() {
        long delayMs = Math.min(MAX_BACKOFF.toMillis(), 1000L << Math.min(attempt, 5));
        attempt++;
        log.info("[{}] Reconnect in {} ms (attempt {})", name, delayMs, attempt);
        scheduler.schedule(() -> {
            synchronized (lock) {
                if (!closed && webSocket == null) {
                    connect();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void watchdog() {
        WebSocket stale = null;
        synchronized (lock) {
            if (connected && lastMessageAt.plus(SILENCE_LIMIT).isBefore(clock.instant())) {
                log.warn("[{}] No messages for {}, reconnecting", name, SILENCE_LIMIT);
                stale = webSocket;
            }
        }
        if (stale != null) {
            stale.cancel();
        }
    }

    private void sendSubscribe(WebSocket ws, String symbol) {
        String payload = mapper.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("event", "subscribe")
                .put("topic", symbol + "@bbo")
                .toString();
        ws.send(payload);
    }

    private final class Listener extends WebSocketListener {
        private final long epoch;

        private Listener(long epoch) {
            this.epoch = epoch;
        }

        private boolean current() {
            return epoch == connectionEpoch;
        }

        @Override
        public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
            synchronized (lock) {
                if (!current()) {
                    ws.cancel();
                    return;
                }
                connected = true;
                attempt = 0;
                lastMessageAt = clock.instant();
                log.info("[{}] Connected, subscribing to {} symbols", name, symbols.size());
                symbols.forEach(s -> sendSubscribe(ws, s));
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
            lastMessageAt = clock.instant();
            try {
                JsonNode node = mapper.readTree(text);
                String event = node.path("event").asText("");
                if ("ping".equals(event)) {
                    ws.send(mapper.createObjectNode().put("event", "pong").put("ts", clock.millis()).toString());
                    return;
                }
                String topic = node.path("topic").asText("");
                if (topic.endsWith("@bbo")) {
                    onBbo(node.path("data"));
                } else if (node.has("success") && !node.path("success").asBoolean(true)) {
                    log.warn("[{}] Orderly error: {}", name, text);
                }
            } catch (Exception e) {
                log.warn("[{}] Cannot parse message: {}", name, text, e);
            }
        }

        private void onBbo(JsonNode data) {
            String symbol = data.path("symbol").asText(null);
            if (symbol == null || !data.hasNonNull("bid") || !data.hasNonNull("ask")) {
                return;
            }
            Quote quote = new Quote(symbol, new BigDecimal(data.get("bid").asText()),
                    new BigDecimal(data.get("ask").asText()), clock.instant());
            quotes.put(symbol, new StampedQuote(quote, epoch));
            for (Consumer<Quote> listener : listeners.getOrDefault(symbol, List.of())) {
                try {
                    listener.accept(quote);
                } catch (Exception e) {
                    log.warn("[{}] Quote listener failed", name, e);
                }
            }
        }

        @Override
        public void onClosed(@NotNull WebSocket ws, int code, @NotNull String reason) {
            onDisconnect("closed: " + code + " " + reason, null);
        }

        @Override
        public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t, @Nullable Response response) {
            onDisconnect("failure: " + t.getMessage(), t);
        }

        private void onDisconnect(String reason, @Nullable Throwable t) {
            synchronized (lock) {
                if (!current()) {
                    return;
                }
                connected = false;
                webSocket = null;
                connectionEpoch++;
                if (closed) {
                    return;
                }
                if (t != null) {
                    log.warn("[{}] Disconnected ({})", name, reason);
                } else {
                    log.info("[{}] Disconnected ({})", name, reason);
                }
                scheduleReconnect();
            }
        }
    }
}
