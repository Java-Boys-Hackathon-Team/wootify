package ru.javaboys.wootify.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceResponse {
    private Map<String, Object> info;
    private Map<String, Double> total;
    private Map<String, Double> free;
    private Map<String, Double> used;

    private TokenBalance USDC;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenBalance {
        private Double free;
        private Double used;
        private Double total;
        private String frozen;
    }
}