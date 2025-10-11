package ru.javaboys.wootify.service;

import org.springframework.stereotype.Service;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.math.BigInteger;

@Service
public class TradingTerminalServiceImpl implements TradingTerminalService{

    @Override
    public void submitOrder(Account account, ApiKey apiKey, Symbol symbol, OrderSide orderSide,
                            OrderType orderType, Double leverage, BigDecimal price, BigDecimal quantity) {


    }

}
