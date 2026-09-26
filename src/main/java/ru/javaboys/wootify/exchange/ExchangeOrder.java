package ru.javaboys.wootify.exchange;

import java.math.BigDecimal;

/**
 * Состояние ордера на бирже.
 *
 * @param avgPrice средняя цена исполнения, {@code null} если исполнений не было
 * @param fee      уплаченная комиссия в котируемой валюте
 */
public record ExchangeOrder(String exchangeOrderId, String clientOrderId, ExchangeOrderStatus status,
                            BigDecimal filledQty, BigDecimal avgPrice, BigDecimal fee, String rejectReason) {

    public boolean isFinal() {
        return status != ExchangeOrderStatus.OPEN;
    }
}
