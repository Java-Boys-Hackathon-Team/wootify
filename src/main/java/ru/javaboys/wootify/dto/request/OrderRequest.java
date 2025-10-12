package ru.javaboys.wootify.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderRequest {
    private String symbol;
    private String side;
    private String type;
    private Double amount;
    private Double price;
    @JsonProperty("clientOrderId")
    private String clientOrderId;
    private Map<String, Object> params;
}

