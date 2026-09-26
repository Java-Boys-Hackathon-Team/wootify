package ru.javaboys.wootify.exchange;

import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;

import java.math.BigDecimal;

/**
 * @param price цена лимитного ордера, для рыночного - {@code null}
 */
public record PlaceOrderCommand(MarketSymbol symbol, String clientOrderId, OrderSide side, OrderType type,
                                BigDecimal quantity, BigDecimal price, boolean reduceOnly) {
}
