package ru.javaboys.wootify.test_support;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ru.javaboys.wootify.orderly.client.OrderlyStreamingMultiClient;

//@SpringBootTest
public class OrderlyStreamingMultiClientTest {
    private static final Logger log = LoggerFactory.getLogger(OrderlyStreamingMultiClientTest.class);

    @Autowired
    private OrderlyStreamingMultiClient streamingService;

    @Disabled
    @Test
    public void test() throws InterruptedException {

        log.info("Start streaming service, sleeping 5s");
        sleep(5_000L);

        Thread thread1 = Thread.ofPlatform()
                .name("PERP_PUMP_USDC")
                .start(() -> {
                    log.info("Subscribe on PERP_PUMP_USDC");
                    streamingService.subscribe("PERP_PUMP_USDC", priceData -> {
                        log.info("Price data PERP_PUMP_USDC: {}", priceData);
                    });

                    sleep(60_000L);

                    log.info("Unsubscribe from PERP_PUMP_USDC");
                    streamingService.unsubscribe("PERP_PUMP_USDC");

                    sleep(30_000L);
                });

        Thread thread2 = Thread.ofPlatform()
                .name("PERP_ETH_USDC")
                .start(() -> {
                    log.info("Subscribe on PERP_ETH_USDC");
                    streamingService.subscribe("PERP_ETH_USDC", priceData -> {
                        log.info("Price data PERP_ETH_USDC: {}", priceData);
                    });

                    sleep(30_000L);

                    log.info("Unsubscribe from PERP_ETH_USDC");
                    streamingService.unsubscribe("PERP_ETH_USDC");

                    sleep(30_000L);
                });

        thread1.join();
        thread2.join();
    }

    private static void sleep(long millis) {
        try {
            log.info("Sleeping " + (millis / 1_000) + "s");
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
