package ru.javaboys.wootify.service;

import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.client.OrderClient;
import ru.javaboys.wootify.dto.request.OrderRequest;
import ru.javaboys.wootify.dto.response.OrderCancelResponse;
import ru.javaboys.wootify.dto.response.OrderDetailResponse;
import ru.javaboys.wootify.dto.response.OrderDetailsResponseInfo;
import ru.javaboys.wootify.dto.response.OrderResponse;
import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderClient orderClient;
    @Autowired
    private DataManager dataManager;

    @Override
    public Order createOrder(CurrentAccountState accountState, CurrentDealState dealState, Position position) {

        if (position.getStatus().equals(PositionStatus.PRE_CLOSE_CANCEL)) {
            throw new IllegalStateException("Order not be created because position status is CLOSE_CANCEL");
        }

        Order localOrder = createLocalOrder(accountState, dealState, position);

        OrderRequest request = OrderRequest.builder()
                .symbol(dealState.getSymbol().getAnalogTicker())
                .side(dealState.getOrderSide().name().toLowerCase())
                .type(dealState.getOrderType().name().toLowerCase())
                .amount(dealState.getQuantity().doubleValue())
                .clientOrderId(localOrder.getId().toString())
                .build();

        if (dealState.getOrderType().equals(OrderType.LIMIT)) {
            request.setPrice(dealState.getPrice().doubleValue());
        }

        OrderResponse response = orderClient.createOrder(
                accountState.getApiKey().getKey(),
                accountState.getApiKey().getSecret(),
                accountState.getAccount().getWoofiId(),
                accountState.getApiKey().getEnv(),
                request
        );

        if (response == null) {
            throw new IllegalStateException("Order creation failed: empty response");
        }

        String externalId = null;

        if (response.getInfo() != null && response.getInfo().containsKey("order_id")) {
            externalId = response.getInfo().get("order_id").toString();
        } else if (response.getId() != null) {
            externalId = response.getId();
        }

        if (externalId != null) {
            localOrder.setOrderlyOrderId(externalId);
            localOrder.setStatus(OrderStatus.SENT_OPEN);
            localOrder.setSendingDate(LocalDateTime.now());
            localOrder = dataManager.save(localOrder);
        }

        return localOrder;
    }

    @Override
    public Order createLocalOrder(CurrentAccountState accountState, CurrentDealState dealState, Position position) {

        Order order = dataManager.create(Order.class);
        order.setCreatedDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CREATED);
        order.setAccount(accountState.getAccount());
        order.setApiKey(accountState.getApiKey());
        order.setPosition(position);
        order.setSymbol(dealState.getSymbol());
        order.setType(dealState.getOrderType());
        order.setSide(dealState.getOrderSide());
        order.setQuantity(dealState.getQuantity());
        if (dealState.getOrderType().equals(OrderType.LIMIT)) order.setPrice(dealState.getPrice());
        order = dataManager.save(order);

        return order;
    }

    @Override
    public void updateOrderStatus(CurrentAccountState accountState, Order order) {
        OrderDetailResponse response = orderClient.getOrderById(
                order.getOrderlyOrderId(),
                order.getSymbol().getAnalogTicker(),
                order.getApiKey().getKey(),
                order.getApiKey().getSecret(),
                order.getAccount().getWoofiId(),
                order.getApiKey().getEnv()
        );

        if (response == null) {
            throw new IllegalStateException("Empty response (updateOrderStatus) for order " + order.getOrderlyOrderId());
        }

        OrderDetailsResponseInfo info = response.getInfo();
        OrderStatus currentStatus = order.getStatus();
        String responseStatus = response.getStatus();

        order.setWoofiStatus(info.getStatus());

        if (responseStatus.equals("closed")) {
            order.setStatus(OrderStatus.CLOSED);
        } else if (responseStatus.equals("open")) {
            order.setStatus(OrderStatus.OPEN);
        } else if (responseStatus.equals("canceled") & info.getStatus().equals("CANCELLED")) {
            order.setStatus(OrderStatus.CANCELLED);
        }

        if (info.getTotal_executed_quantity() != null)
            order.setTotalExecutedQuantity(BigDecimal.valueOf(info.getTotal_executed_quantity()));
        if (info.getAverage_executed_price() != null)
            order.setAverageExecutedPrice(BigDecimal.valueOf(info.getAverage_executed_price()));
        if (info.getTotal_fee() != null)
            order.setTotalFee(BigDecimal.valueOf(info.getTotal_fee()));

        if (!currentStatus.equals(order.getStatus()) &
                (order.getStatus().equals(OrderStatus.CLOSED) ||  order.getStatus().equals(OrderStatus.CANCELLED))) {
            order.setClosedDate(LocalDateTime.now());
        }

        dataManager.save(order);
    }

    @Override
    public void cancelOrder(CurrentAccountState accountState, Order order) {
        OrderCancelResponse response = orderClient.cancelOrder(
                order.getSymbol().getAnalogTicker(),
                order.getOrderlyOrderId(),
                order.getApiKey().getKey(),
                order.getApiKey().getSecret(),
                order.getAccount().getWoofiId(),
                order.getApiKey().getEnv()
        );

        if (response == null) {
            throw new IllegalStateException("Empty response (cancelOrder) for order " + order.getOrderlyOrderId());
        }

        if (response.getCanceled()) {
            order.setStatus(OrderStatus.SENT_CANCEL); //не изменять статус пока не изменится на woofi
            order.setWoofiStatus(response.getResult().getInfo().getStatus());
            dataManager.save(order);
        }

    }
}
