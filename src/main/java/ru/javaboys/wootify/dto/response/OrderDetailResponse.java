package ru.javaboys.wootify.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class OrderDetailResponse {
    private String id;
    private String clientOrderId;
    private Long timestamp;
    private String datetime;
    private Long lastTradeTimestamp;
    private Long lastUpdateTimestamp;

    private String status;
    private String symbol;
    private String type;
    private String timeInForce;
    private Boolean postOnly;
    private Boolean reduceOnly;
    private String side;

    private Double price;
    private Double stopPrice;
    private Double triggerPrice;
    private Double takeProfitPrice;
    private Double stopLossPrice;
    private Double average;

    private Double amount;
    private Double filled;
    private Double remaining;
    private Double cost;

    private List<Map<String, Object>> trades;
    private OrderDetailFeeResponse fee;
    private List<OrderDetailFeeResponse> fees;
    private Map<String, Object> info;
}
