package ru.javaboys.wootify.exchange.paper;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Параметры симулятора бумажной торговли. Комиссии по умолчанию близки к тарифам WOOFi Pro.
 */
@ConfigurationProperties("wootify.paper")
public record PaperTradingProperties(BigDecimal makerFeeRate, BigDecimal takerFeeRate) {

    public PaperTradingProperties {
        if (makerFeeRate == null) {
            makerFeeRate = new BigDecimal("0.0002");
        }
        if (takerFeeRate == null) {
            takerFeeRate = new BigDecimal("0.0005");
        }
    }
}
