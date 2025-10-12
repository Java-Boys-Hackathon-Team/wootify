package ru.javaboys.wootify.service;

import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TradingTerminalServiceImpl implements TradingTerminalService{

    @Autowired
    private PositionSevice positionSevice;
    @Autowired
    private OrderService orderService;

    @Override
    public void submitOrder(Account account, ApiKey apiKey, Symbol symbol, OrderSide orderSide,
                            OrderType orderType, Double leverage, BigDecimal price, BigDecimal quantity) {

        Position currentPosition = positionSevice.getActivePositionForTicker(account, apiKey, symbol);
        if (currentPosition == null) {
            currentPosition = positionSevice.createPositionForTicker(account, apiKey, symbol, leverage);
        }

        Order order = orderService.createOrder(account, apiKey, symbol, orderSide,
                            currentPosition, orderType, price, quantity);





    }

}
