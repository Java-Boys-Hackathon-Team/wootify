package ru.javaboys.wootify.service;

import io.jmix.core.DataManager;
import io.jmix.core.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.client.OrderClient;
import ru.javaboys.wootify.dto.request.OrderRequest;
import ru.javaboys.wootify.dto.response.OrderResponse;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderClient orderClient;
    @Autowired
    private DataManager dataManager;

    public Order createOrder(
            Account account,
            ApiKey apiKey,
            Symbol symbol,
            OrderSide orderSide,
            Position position,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity) {

        Order localOrder = createLocalOrder(account, apiKey, symbol, orderSide, position, orderType, price, quantity);

//        OrderRequest request = OrderRequest.builder()
//                .symbol(symbol.getAnalogTicker())
//                .side(orderSide.name())
//                .type(orderType.name())
//                .amount(quantity.doubleValue())
//                .price(price.doubleValue())
//                .clientOrderId(localOrder.getId())
//                .build();
//
//        OrderResponse response = orderClient.createOrder(
//                apiKey.getKey(),
//                apiKey.getSecret(),
//                account.getWoofiId(),
//                apiKey.getEnv(),
//                request
//        );
//
//        if (response == null) {
//            throw new IllegalStateException("Order creation failed: empty response");
//        }

        return null;
    }

    public Order createLocalOrder(
            Account account,
            ApiKey apiKey,
            Symbol symbol,
            OrderSide orderSide,
            Position position,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity) {

        Order order = dataManager.create(Order.class);
        order.setCreatedDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CREATED);
        order.setAccount(account);
        order.setApiKey(apiKey);
        order.setPosition(position);
        order.setSymbol(symbol);
        order.setType(orderType);
        order.setSide(orderSide);
        order.setPrice(price);
        order.setQuantity(quantity);
        order = dataManager.save(order);

        return order;
    }
}
