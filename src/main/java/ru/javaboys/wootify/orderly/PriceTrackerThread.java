package ru.javaboys.wootify.orderly;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;

import ru.javaboys.wootify.orderly.model.Event;
import ru.javaboys.wootify.orderly.model.PriceData;

public class PriceTrackerThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(PriceTrackerThread.class);

    private final PriceTrackerThreadListener listener;
    private final ObjectReader priceDataReader;
    private final ObjectReader eventDataReader;
    private final BlockingQueue<String> queue;

    public PriceTrackerThread(BlockingQueue<String> queue, PriceTrackerThreadListener listener) {
        super("orderly-streaming");

        this.queue = queue;
        this.listener = listener;

        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.priceDataReader = mapper.readerFor(PriceData.class);
        this.eventDataReader = mapper.readerFor(Event.class);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String json = queue.take();
                PriceData data = priceDataReader.readValue(json);
                if (data.getTopic() == null) {
                    Event event = eventDataReader.readValue(json);
                    if (event.getEvent().equals("ping")) {
                        log.info("Received ping, sending pong");
                        listener.pingReceived();
                    } else {
                        log.warn("Unknown event: {}", event);
                    }
                    continue;
                }

                listener.priceDataReceived(data);
            } catch (Exception e) {
                log.error("Unknown error", e);
            }
        }
    }

}
