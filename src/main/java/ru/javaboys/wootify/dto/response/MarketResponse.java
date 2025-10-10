package ru.javaboys.wootify.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketResponse {
    private String id;
    private String lowercaseId;
    private String symbol;

    private String base;
    private String quote;
    private String settle;

    private String baseId;
    private String quoteId;
    private String settleId;

    private String type;
    private Boolean spot;
    private Boolean margin;
    private Boolean swap;
    private Boolean future;
    private Boolean option;
    private Boolean contract;
    private Boolean linear;
    private Boolean inverse;
    private String subType;

    private Double taker;
    private Double maker;
    private Double contractSize;

    private Long expiry;
    private String expiryDatetime;
    private Double strike;
    private String optionType;

    private MarketPrecisionResponse precision;
    private MarketLimitsResponse limits;

    private Long created;
    private Map<String, Object> info;

    private Boolean tierBased;
    private Boolean percentage;
}