package ru.javaboys.wootify.service;

import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface TradingTerminalService {
    void submitOrder(CurrentAccountState accountState, CurrentDealState dealState);
}
