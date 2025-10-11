package ru.javaboys.wootify.test_support;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import ru.javaboys.wootify.orderly.client.OrderlyStreamingClient;

//@SpringBootTest
public class OrderlyStreamingClientTest {
    private static final Logger log = LoggerFactory.getLogger(OrderlyStreamingClientTest.class);

    @Value("${orderly.account-id}") private String accountId;

    @Disabled
    @Test
    public void test() {

        sleep(5000L);

        OrderlyStreamingClient client1 = new OrderlyStreamingClient(accountId, "PERP_PUMP_USDC", data -> {
            log.info("Price data: {}", data);
        });
        OrderlyStreamingClient client2 = new OrderlyStreamingClient(accountId, "PERP_ETH_USDC", data -> {
            log.info("Price data: {}", data);
        });
        sleep(30_000L);

        log.info("Unsubscribe from client#1 30s");
        client1.unsubscribeAndWait();
        sleep(30_000L);

        log.info("Unsubscribe from client#2 30s");
        client2.unsubscribeAndWait();
        sleep(30_000L);
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
