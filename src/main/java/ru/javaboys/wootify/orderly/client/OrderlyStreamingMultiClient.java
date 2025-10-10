package ru.javaboys.wootify.orderly.client;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.google.gson.JsonObject;

import jakarta.annotation.PreDestroy;
import okhttp3.WebSocket;
import ru.javaboys.wootify.orderly.OrderlyWebSocketListener;
import ru.javaboys.wootify.orderly.PriceListener;
import ru.javaboys.wootify.orderly.PriceTrackerThread;
import ru.javaboys.wootify.orderly.PriceTrackerThreadListener;
import ru.javaboys.wootify.orderly.model.PriceData;

//@Service
public class OrderlyStreamingMultiClient extends AbstractOrderlyClient {
    private static final Logger log = LoggerFactory.getLogger(OrderlyStreamingMultiClient.class);

    private final ConcurrentHashMap<String, PriceListener> listeners = new ConcurrentHashMap<>();
    private final ExecutorService dispatchPool = new ThreadPoolExecutor(
            0, 64,
            30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    private final Thread workingThread;
    private final WebSocket webSocket;

    public OrderlyStreamingMultiClient(
            @Value("${orderly.ws.url}") String url,
            @Value("${orderly.account-id}") String accountId
    ) {
        super(url, accountId);

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(10_000);
        OrderlyWebSocketListener listener = new OrderlyWebSocketListener(queue, (ws, response) -> {
        });
        this.webSocket = this.client.newWebSocket(buildRequest(), listener);
        this.workingThread = new PriceTrackerThread(queue, new PriceTrackerThreadListener() {
            @Override
            public void pingReceived() {
                webSocket.send(buildPongJson().toString());
            }

            @Override
            public void priceDataReceived(PriceData data) {
                PriceListener listener = listeners.get(data.getTopic());
                if (listener != null) {
                    dispatchPool.execute(() -> listener.priceReceived(data));
                }
            }
        });
        this.workingThread.start();
    }

    public void subscribe(String symbol, PriceListener listener) {
        String topicName = symbol + "@bbo";
        listeners.put(topicName, listener);

        JsonObject obj = buildSubscribeJson(topicName);
        log.info("Subscribe {}", obj);

        webSocket.send(obj.toString());
    }

    public void unsubscribe(String symbol) {
        String topicName = symbol + "@bbo";
        listeners.remove(topicName);

        JsonObject obj = buildUnsubscribeJson(topicName);
        log.info("Unsubscribe {}", obj);

        webSocket.send(obj.toString());
    }

    @PreDestroy
    protected void preDestroy() {
        workingThread.interrupt();
    }

}
