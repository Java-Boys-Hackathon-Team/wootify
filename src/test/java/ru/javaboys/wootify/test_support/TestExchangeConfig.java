package ru.javaboys.wootify.test_support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.javaboys.wootify.bot.strategy.StrategyProvider;
import ru.javaboys.wootify.bot.strategy.StrategyRegistry;
import ru.javaboys.wootify.exchange.instrument.InstrumentService;
import ru.javaboys.wootify.exchange.instrument.StaticInstrumentService;


@TestConfiguration(proxyBeanMethods = false)
public class TestExchangeConfig {

    @Bean
    public ManualMarketData manualMarketData() {
        return new ManualMarketData();
    }

    @Bean
    @Primary
    public StrategyRegistry scriptedStrategyRegistry(ObjectProvider<StrategyProvider> providers) {
        return new ScriptedStrategyRegistry(providers.orderedStream().toList());
    }

    @Bean
    public InstrumentService testInstrumentService() {
        return new StaticInstrumentService();
    }
}
