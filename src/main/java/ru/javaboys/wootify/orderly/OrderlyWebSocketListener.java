package ru.javaboys.wootify.orderly;

import java.util.concurrent.BlockingQueue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class OrderlyWebSocketListener extends WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(OrderlyWebSocketListener.class);

    private final BlockingQueue<String> queue;
    private final SocketOpenedCallback callback;

    public OrderlyWebSocketListener(BlockingQueue<String> queue, SocketOpenedCallback callback) {
        this.queue = queue;
        this.callback = callback;
    }

    @Override
    public void onClosed(@NotNull WebSocket ws, int code, @NotNull String reason) {
        log.info("WS closed: " + code + " " + reason);
    }

    @Override
    public void onClosing(@NotNull WebSocket ws, int code, @NotNull String reason) {
        log.info("WS closing: " + code + " " + reason);
    }

    @Override
    public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t, @Nullable Response response) {
        log.error("WS failure: " + t.getMessage(), t);
    }

    @Override
    public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
        log.info("WS message(String): " + text);
        queue.add(text);
    }

    @Override
    public void onMessage(@NotNull WebSocket ws, @NotNull ByteString bytes) {
        log.info("WS message(ByteString): " + bytes.hex());
    }

    @Override
    public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
        log.info("WS opened: " + response.code());
        callback.opened(ws, response);
    }
}