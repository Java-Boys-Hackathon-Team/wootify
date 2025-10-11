package ru.javaboys.wootify.service;

import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface TradingTerminalService {
    void submitOrder(
            Account account,
            ApiKey apiKey,
            Symbol symbol,
            OrderSide orderSide,
            OrderType orderType,
            Double leverage,
            BigDecimal price,
            BigDecimal quantity
    );
}
