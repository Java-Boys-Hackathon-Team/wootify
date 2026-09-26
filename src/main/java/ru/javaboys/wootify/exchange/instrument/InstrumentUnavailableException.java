package ru.javaboys.wootify.exchange.instrument;

import ru.javaboys.wootify.common.TransientFailure;

public class InstrumentUnavailableException extends RuntimeException implements TransientFailure {

    public InstrumentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
