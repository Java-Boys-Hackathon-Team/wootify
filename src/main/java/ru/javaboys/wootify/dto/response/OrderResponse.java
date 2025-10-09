package ru.javaboys.wootify.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {
    private String id;
    private String status;
    private String symbol;
    private String type;
    private String side;
    private Double price;
    private Double amount;
    private Double filled;
    private Double remaining;
    private Map<String, Object> info;
}
