package ru.javaboys.wootify.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.*;

@Service
public class TradingTerminalServiceImpl implements TradingTerminalService{

    @Autowired
    private PositionService positionService;
    @Autowired
    private OrderService orderService;

    @Override
    public void submitOrder(CurrentAccountState accountState, CurrentDealState dealState) {

        Position currentPosition = positionService.getActivePositionForTicker(accountState, dealState.getSymbol());
        if (currentPosition == null) {
            currentPosition = positionService.createPositionForTicker(accountState,  dealState);
        }

        Order order = orderService.createOrder(accountState,  dealState, currentPosition);

    }

}
