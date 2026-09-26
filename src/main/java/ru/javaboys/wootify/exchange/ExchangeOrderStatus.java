package ru.javaboys.wootify.exchange;

public enum ExchangeOrderStatus {
    /** Активен, возможно частично исполнен. */
    OPEN,
    FILLED,
    /** Отменён; мог быть частично исполнен до отмены. */
    CANCELLED,
    REJECTED
}
