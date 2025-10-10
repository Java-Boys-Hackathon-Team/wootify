package ru.javaboys.wootify.orderly.client;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public abstract class AbstractOrderlyClient {
    protected final OkHttpClient client;
    private final String accountId;
    private final String url;

    public AbstractOrderlyClient(
            String url,
            String accountId
    ) {
        this.url = url;
        this.accountId = accountId;
        this.client = new OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
    }

    protected Request buildRequest() {
        return new Request.Builder()
                .url(url + accountId)
                .build();
    }

    protected String buildTopic(String symbol) {
        return symbol + "@bbo";
    }

    protected JsonObject buildSubscribeJson(String topic) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", UUID.randomUUID().toString());
        obj.addProperty("topic", topic);
        obj.addProperty("event", "subscribe");

        return obj;
    }

    protected JsonObject buildUnsubscribeJson(String topic) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", UUID.randomUUID().toString());
        obj.addProperty("topic", topic);
        obj.addProperty("event", "unsubscribe");

        return obj;
    }

    protected JsonObject buildPongJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("ts", System.currentTimeMillis());
        obj.addProperty("event", "pong");

        return obj;
    }

}
