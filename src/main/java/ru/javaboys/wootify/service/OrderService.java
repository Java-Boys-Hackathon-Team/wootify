package ru.javaboys.wootify.service;

import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;

public interface OrderService {
    Order createOrder(
            Account account,
            ApiKey apiKey,
            Symbol symbol,
            OrderSide orderSide,
            Position position,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity);

    Order createLocalOrder(
            Account account,
            ApiKey apiKey,
            Symbol symbol,
            OrderSide orderSide,
            Position position,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity);

}
