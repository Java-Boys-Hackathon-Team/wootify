package ru.javaboys.wootify.orderly.client;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import okhttp3.WebSocket;
import ru.javaboys.wootify.orderly.OrderlyWebSocketListener;
import ru.javaboys.wootify.orderly.PriceListener;
import ru.javaboys.wootify.orderly.PriceTrackerThread;
import ru.javaboys.wootify.orderly.PriceTrackerThreadListener;
import ru.javaboys.wootify.orderly.model.PriceData;

public class OrderlyStreamingClient extends AbstractOrderlyClient {
    private static final Logger log = LoggerFactory.getLogger(OrderlyStreamingClient.class);
    private static final String URL = "wss://ws-evm.orderly.org/ws/stream/";

    private final String symbol;
    private final WebSocket webSocket;
    private final Thread workingThread;

    public OrderlyStreamingClient(
            final String accountId,
            final String symbol,
            final PriceListener priceListener
    ) {
        super(URL, accountId);

        this.symbol = symbol;

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(10_000);
        OrderlyWebSocketListener wsListener = new OrderlyWebSocketListener(queue, (ws, response) -> subscribe(symbol));
        this.webSocket = this.client.newWebSocket(buildRequest(), wsListener);
        this.workingThread = new PriceTrackerThread(queue, new PriceTrackerThreadListener() {
            @Override
            public void pingReceived() {
                webSocket.send(buildPongJson().toString());
            }

            @Override
            public void priceDataReceived(PriceData data) {
                priceListener.priceReceived(data);
            }
        });
        this.workingThread.start();
    }

    public void unsubscribeAndWait() {
        String topicName = buildTopic(symbol);
        JsonObject obj = buildUnsubscribeJson(topicName);
        log.info("Unsubscribe {}", obj);

        webSocket.send(obj.toString());
        workingThread.interrupt();
        webSocket.close(1000, "Unsubscribe");
        try {
            workingThread.join();
        } catch (InterruptedException e) {
            log.warn("Error while waiting for working thread to finish", e);
        }
    }

    private void subscribe(String symbol) {
        String topicName = symbol + "@bbo";
        JsonObject obj = buildSubscribeJson(topicName);
        log.info("Subscribe {}", obj);

        webSocket.send(obj.toString());
    }

}
