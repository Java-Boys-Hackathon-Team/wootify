package ru.javaboys.wootify.orderly;

import org.jetbrains.annotations.NotNull;

import okhttp3.Response;
import okhttp3.WebSocket;

public interface SocketOpenedCallback {

    void opened(@NotNull WebSocket ws, @NotNull Response response);
}
