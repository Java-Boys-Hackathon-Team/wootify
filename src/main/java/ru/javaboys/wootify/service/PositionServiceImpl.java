package ru.javaboys.wootify.service;

import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.javaboys.wootify.client.LeverageClient;
import ru.javaboys.wootify.dto.request.LeverageRequest;
import ru.javaboys.wootify.dto.response.LeverageResponse;
import ru.javaboys.wootify.entity.*;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PositionServiceImpl implements PositionSevice{

    @Autowired
    private LeverageClient leverageClient;
    @Autowired
    private DataManager dataManager;


    @Override
    public Double setLeverageForTicker(Account account, ApiKey apiKey, Double leverage, Symbol symbol) {
        LeverageRequest request = new LeverageRequest();
        request.setLeverage(leverage.intValue());

        LeverageResponse leverageResponse = leverageClient.setLeverage(
                symbol.getAnalogTicker(),
                apiKey.getKey(),
                apiKey.getSecret(),
                account.getWoofiId(),
                apiKey.getEnv(),
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

    public Position getActivePositionForTicker(Account account, ApiKey apiKey, Symbol symbol) {
        return dataManager.load(Position.class)
                .query("select p from Position_ p where p.account = :account and p.symbol = :symbol " +
                        "and p.apiKey = :apiKey and p.status in (:st1, :st2)")
                .parameter("account", account)
                .parameter("symbol", symbol)
                .parameter("apiKey", apiKey)
                .parameter("st1", PositionStatus.CREATED.getId())
                .parameter("st2", PositionStatus.OPENED.getId())
                .optional()
                .orElse(null);
    }

    public Position createPositionForTicker(Account account, ApiKey apiKey, Symbol symbol, Double leverage) {
        Position position = dataManager.create(Position.class);
        position.setAccount(account);
        position.setApiKey(apiKey);
        position.setSymbol(symbol);
        position.setStatus(PositionStatus.CREATED);
        position.setCreatedDate(LocalDateTime.now());
        position.setLeverage(leverage); // можно задать дефолтное значение
        position = dataManager.save(position);

        return position;
    }


}
