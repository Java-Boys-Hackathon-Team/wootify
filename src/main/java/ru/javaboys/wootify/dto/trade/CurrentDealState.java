package ru.javaboys.wootify.dto.trade;

import lombok.Builder;
import lombok.Data;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.Symbol;

import java.math.BigDecimal;

@Data
@Builder
public class CurrentDealState {
    Symbol symbol;
    OrderSide orderSide;
    OrderType orderType;
    Double leverage;
    BigDecimal price;
    BigDecimal quantity;
}
