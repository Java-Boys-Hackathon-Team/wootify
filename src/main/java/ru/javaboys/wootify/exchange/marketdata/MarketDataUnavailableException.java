package ru.javaboys.wootify.exchange.marketdata;

import ru.javaboys.wootify.common.TransientFailure;

public class MarketDataUnavailableException extends RuntimeException implements TransientFailure {

    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
