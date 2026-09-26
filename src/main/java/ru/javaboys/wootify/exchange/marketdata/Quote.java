package ru.javaboys.wootify.exchange.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Лучшие цены покупки и продажи (bbo) по инструменту.
 */
public record Quote(String symbol, BigDecimal bid, BigDecimal ask, Instant timestamp) {

    public BigDecimal mid() {
        return bid.add(ask).divide(BigDecimal.valueOf(2), bid.scale() + 2, RoundingMode.HALF_UP);
    }

    public boolean isFresh(Instant now, Duration maxAge) {
        return !timestamp.plus(maxAge).isBefore(now);
    }
}
