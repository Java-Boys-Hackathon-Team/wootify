package ru.javaboys.wootify.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PositionResponse {
    private Map<String, Object> info;

    private String id;
    private String symbol;
    private Long timestamp;
    private String datetime;
    private Long lastUpdateTimestamp;

    private Double initialMargin;
    private Double initialMarginPercentage;
    private Double maintenanceMargin;
    private Double maintenanceMarginPercentage;

    private Double entryPrice;
    private Double notional;
    private Double leverage;
    private Double unrealizedPnl;

    private Double contracts;
    private Double contractSize;

    private Double marginRatio;
    private Double liquidationPrice;
    private Double markPrice;
    private Double lastPrice;

    private Double collateral;
    private String marginMode;
    private String marginType;
    private String side;

    private Double percentage;
    private Boolean hedged;

    private Double stopLossPrice;
    private Double takeProfitPrice;
}