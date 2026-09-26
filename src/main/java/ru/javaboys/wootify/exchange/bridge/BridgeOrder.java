package ru.javaboys.wootify.exchange.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Нормализованный ордер из ответа моста ({@code /v2/orders}). Статус в нотации CCXT:
 * {@code open}, {@code closed}, {@code canceled}, {@code rejected}, {@code expired}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BridgeOrder(String id, String clientOrderId, String symbol, String side, String type, String status,
                          String rawStatus, BigDecimal price, BigDecimal amount, BigDecimal filled,
                          BigDecimal average, BigDecimal fee, Boolean reduceOnly) {
}
