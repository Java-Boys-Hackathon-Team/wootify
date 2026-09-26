package ru.javaboys.wootify.exchange.bridge;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Числа передаются строками, чтобы не терять точность на стороне Python.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BridgeOrderRequest(String symbol, String side, String type, String amount, String price,
                                 String clientOrderId, boolean reduceOnly) {
}
