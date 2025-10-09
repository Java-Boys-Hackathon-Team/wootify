package ru.javaboys.wootify.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeverageResponse {
    private String symbol;
    private Integer requestedLeverage;
    private String requestedMarginMode;
    private String warning;
    private Map<String, Object> result;
}