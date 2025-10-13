package ru.javaboys.wootify.service;

import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.client.LeverageClient;
import ru.javaboys.wootify.dto.request.LeverageRequest;
import ru.javaboys.wootify.dto.response.LeverageGetResponse;
import ru.javaboys.wootify.dto.response.LeverageResponse;
import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.*;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PositionServiceImpl implements PositionService {

    @Autowired
    private LeverageClient leverageClient;
    @Autowired
    private DataManager dataManager;


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


}
