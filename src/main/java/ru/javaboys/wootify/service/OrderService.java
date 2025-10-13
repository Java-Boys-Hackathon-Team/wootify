package ru.javaboys.wootify.service;

import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;

public interface OrderService {
    Order createOrder(CurrentAccountState accountState, CurrentDealState dealState, Position position);
    Order createLocalOrder(CurrentAccountState accountState, CurrentDealState dealState, Position position);
    void updateOrderStatus(CurrentAccountState accountState, Order order);
    void cancelOrder(CurrentAccountState accountState, Order order);
}
