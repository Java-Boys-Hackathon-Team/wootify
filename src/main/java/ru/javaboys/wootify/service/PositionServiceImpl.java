package ru.javaboys.wootify.service;

import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.client.LeverageClient;
import ru.javaboys.wootify.client.PositionClient;
import ru.javaboys.wootify.dto.request.LeverageRequest;
import ru.javaboys.wootify.dto.response.LeverageGetResponse;
import ru.javaboys.wootify.dto.response.LeverageResponse;
import ru.javaboys.wootify.dto.response.PositionResponse;
import ru.javaboys.wootify.dto.response.PositionsResponse;
import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.dto.trade.PositionStateResponse;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

@Service
public class PositionServiceImpl implements PositionService {

    @Autowired
    private PositionClient positionClient;
    @Autowired
    private LeverageClient leverageClient;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private OrderService orderService;

    @Override
    public Double setLeverageForTicker(CurrentAccountState accountState, CurrentDealState dealState) {
        LeverageRequest request = new LeverageRequest();
        request.setLeverage(dealState.getLeverage().intValue());

        LeverageResponse leverageResponse = leverageClient.setLeverage(
                dealState.getSymbol().getAnalogTicker(),
                accountState.getApiKey().getKey(),
                accountState.getApiKey().getSecret(),
                accountState.getAccount().getWoofiId(),
                accountState.getApiKey().getEnv(),
                request
        );

        // Разбор поля result -> data -> leverage
        Map<String, Object> result = leverageResponse.getResult();
        if (result != null && result.containsKey("data")) {
            Object dataObj = result.get("data");
            if (dataObj instanceof Map<?, ?> dataMap) {
                Object levObj = dataMap.get("leverage");
                if (levObj != null) {
                    try {
                        return Double.parseDouble(levObj.toString());
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
            }
        }

        return 0.0;
    }

    @Override
    public Double getLeverageForTicker(CurrentAccountState accountState, CurrentDealState dealState) {
        LeverageGetResponse leverageGetResponse = leverageClient.getLeverage(
                dealState.getSymbol().getAnalogTicker(),
                accountState.getApiKey().getKey(),
                accountState.getApiKey().getSecret(),
                accountState.getAccount().getWoofiId(),
                accountState.getApiKey().getBrokerId(),
                accountState.getApiKey().getEnv()
        );

        return (double) leverageGetResponse.getLeverage();
    }

    public Position getActivePositionForTicker(CurrentAccountState accountState, Symbol symbol) {
        return dataManager.load(Position.class)
                .query("select p from Position_ p where p.account = :account and p.symbol = :symbol " +
                        "and p.apiKey = :apiKey and p.status in (:st1, :st2)")
                .parameter("account", accountState.getAccount())
                .parameter("symbol", symbol)
                .parameter("apiKey", accountState.getApiKey())
                .parameter("st1", PositionStatus.CREATED.getId())
                .parameter("st2", PositionStatus.OPENED.getId())
                .optional()
                .orElse(null);
    }

    public Position createPositionForTicker(CurrentAccountState accountState, CurrentDealState dealState) {
        Position position = dataManager.create(Position.class);
        position.setAccount(accountState.getAccount());
        position.setApiKey(accountState.getApiKey());
        position.setSymbol(dealState.getSymbol());
        position.setStatus(PositionStatus.CREATED);
        position.setCreatedDate(LocalDateTime.now());
        position.setLeverage(dealState.getLeverage());
        position = dataManager.save(position);

        return position;
    }

    @Override
    public void updatePositionInfo(Position position) {

        boolean positionPreCloseCancel = position.getStatus().equals(PositionStatus.PRE_CLOSE_CANCEL);

        if (positionPreCloseCancel) {
            if (checkClosedOfOrdersForPosition(position)) {
                throw new IllegalStateException("There are still unclosed orders for this position" + position);
            }
        }

        PositionStateResponse positionState = getPositionStateResponse(position, "closePosition");

        if (positionState.getPositionsCount() == 0 & positionPreCloseCancel) {
            position.setStatus(PositionStatus.CLOSED);
            // TODO Обработать ситуацию с CANCELLED
            position.setClosedDate(LocalDateTime.now());
            dataManager.save(position);

            // TODO Добавить расчет PNL сделки

        }

        // TODO Требуется дополнительная обработка этой ситуации
        if (positionState.getPositionsCount() == 0 & position.getStatus().equals(PositionStatus.OPENED)) {
            position.setStatus(PositionStatus.CLOSED);
            position.setClosedDate(LocalDateTime.now());
            dataManager.save(position);
        }

        PositionResponse posResponse = positionState.getPositionResponse();
        if (posResponse != null) {
            position.setPositionQty(BigDecimal.valueOf(posResponse.getContracts()));
            position.setAverageOpenPrice(BigDecimal.valueOf(posResponse.getEntryPrice()));
            position.setStatus(PositionStatus.OPENED);
            dataManager.save(position);
        }
    }

    private Boolean checkClosedOfOrdersForPosition(Position position) {
        // Возврат = есть хотя бы один Ордер по Позиции не в статусе CANCELLED или CLOSED
        Long count = dataManager.loadValue(
                        "select count(o) from Order_ o " +
                                "where o.position = :pos " +
                                "and o.status not in ('CANCELLED', 'CLOSED')", Long.class)
                .parameter("pos", position)
                .one();

        return count > 0;
    }

    @Override
    public void closePosition(Position position) {
        PositionStateResponse positionState = getPositionStateResponse(position, "closePosition");

        if (positionState.getPositionsCount() == 0)
            throw new IllegalStateException("Attempt to close an unopened position: " + position);;

        PositionResponse posResponse = positionState.getPositionResponse();
        if (posResponse == null) return;

        Double contractsQuantity = posResponse.getContracts();

        if (contractsQuantity.equals(0.0)) return;

        CurrentDealState dealState = CurrentDealState.builder()
                .symbol(position.getSymbol())
                .orderType(OrderType.MARKET)
                .orderSide(contractsQuantity >= 0 ? OrderSide.SELL : OrderSide.BUY)
                .quantity(BigDecimal.valueOf(contractsQuantity))
                .build();

        CurrentAccountState accountState = CurrentAccountState.builder()
                .account(position.getAccount())
                .apiKey(position.getApiKey())
                .build();

        Order closeOrder = orderService.createOrder(accountState, dealState, position);

        position.setStatus(PositionStatus.PRE_CLOSE_CANCEL);
        dataManager.save(position);

    }

    private PositionStateResponse getPositionStateResponse(Position position, String callingMethod) {

        PositionsResponse response = positionClient.getPositions(
                position.getSymbol().getAnalogTicker(),
                true,
                position.getApiKey().getKey(),
                position.getApiKey().getSecret(),
                position.getAccount().getWoofiId(),
                position.getApiKey().getEnv()
        );

        if (response == null) {
            throw new IllegalStateException(callingMethod + ": empty response");
        }

        PositionStateResponse stateResponse = PositionStateResponse.builder()
                .positionsCount(response.getCount())
                .build();
        if (response.getCount() > 0) stateResponse.setPositionResponse(response.getPositions().getFirst());

        return stateResponse;
    }

}
