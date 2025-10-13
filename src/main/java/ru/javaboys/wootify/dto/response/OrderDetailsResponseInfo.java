package ru.javaboys.wootify.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderDetailsResponseInfo {
    private String status;
    private Double total_executed_quantity;
    private Double total_fee;
    private Double average_executed_price;
}
