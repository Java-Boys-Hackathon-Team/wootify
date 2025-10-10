package ru.javaboys.wootify.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketLimitsResponse {
    private MarketLeverageLimitsResponse leverage;
    private MarketAmountLimitsResponse amount;
    private MarketPriceLimitsResponse price;
    private MarketCostLimitsResponse cost;
}
