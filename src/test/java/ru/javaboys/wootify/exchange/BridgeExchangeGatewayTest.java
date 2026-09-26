package ru.javaboys.wootify.exchange;

import org.junit.jupiter.api.Test;
import ru.javaboys.wootify.exchange.bridge.BridgeOrder;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeExchangeGatewayTest {

    private static ExchangeOrder map(BridgeOrder order) throws Exception {
        Class<?> gateway = Class.forName("ru.javaboys.wootify.exchange.bridge.BridgeExchangeGateway");
        Method m = gateway.getDeclaredMethod("toExchangeOrder", BridgeOrder.class, String.class);
        m.setAccessible(true);
        return (ExchangeOrder) m.invoke(null, order, "cid");
    }

    private static BridgeOrder order(String status, String rawStatus, String filled, String average, String fee) {
        return new BridgeOrder("42", null, "ETH/USDC:USDC", "buy", "limit", status, rawStatus,
                new BigDecimal("100"), BigDecimal.ONE, filled == null ? null : new BigDecimal(filled),
                average == null ? null : new BigDecimal(average), fee == null ? null : new BigDecimal(fee), false);
    }

    @Test
    void mapsCcxtStatuses() throws Exception {
        assertThat(map(order("open", "NEW", "0", null, "0")).status()).isEqualTo(ExchangeOrderStatus.OPEN);
        assertThat(map(order("open", "PARTIAL_FILLED", "0.5", "100", "0.01")).status()).isEqualTo(ExchangeOrderStatus.OPEN);
        assertThat(map(order("closed", "FILLED", "1", "100", "0.02")).status()).isEqualTo(ExchangeOrderStatus.FILLED);
        assertThat(map(order("canceled", "CANCELLED", "0", null, null)).status()).isEqualTo(ExchangeOrderStatus.CANCELLED);
        assertThat(map(order("rejected", "REJECTED", null, null, null)).status()).isEqualTo(ExchangeOrderStatus.REJECTED);
    }

    @Test
    void normalizesMissingNumbersAndKeepsClientId() throws Exception {
        ExchangeOrder mapped = map(order("open", "NEW", null, "0", "-0.05"));
        assertThat(mapped.filledQty()).isEqualByComparingTo("0");
        assertThat(mapped.avgPrice()).isNull();
        assertThat(mapped.fee()).isEqualByComparingTo("0.05");
        assertThat(mapped.clientOrderId()).isEqualTo("cid");
    }
}
