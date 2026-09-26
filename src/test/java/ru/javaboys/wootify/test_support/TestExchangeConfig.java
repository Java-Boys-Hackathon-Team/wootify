package ru.javaboys.wootify.test_support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.javaboys.wootify.exchange.instrument.InstrumentService;
import ru.javaboys.wootify.exchange.instrument.StaticInstrumentService;

@TestConfiguration(proxyBeanMethods = false)
public class TestExchangeConfig {

    @Bean
    public ManualMarketData manualMarketData() {
        return new ManualMarketData();
    }

    @Bean
    public InstrumentService testInstrumentService() {
        return new StaticInstrumentService();
    }
}
