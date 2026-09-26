package ru.javaboys.wootify.bot.engine;

import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.core.security.SystemAuthenticator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.javaboys.wootify.bot.strategy.StrategyProvider;
import ru.javaboys.wootify.bot.strategy.StrategyRegistry;
import ru.javaboys.wootify.exchange.ExchangeGatewayFactory;
import ru.javaboys.wootify.exchange.instrument.InstrumentService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataService;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(BotEngineProperties.class)
public class BotEngineConfiguration {

    @Bean
    public BotRuntimeRepository botRuntimeRepository(JdbcTemplate jdbcTemplate) {
        return new BotRuntimeRepository(jdbcTemplate);
    }

    @Bean
    public BotEventService botEventService(JdbcTemplate jdbcTemplate, BotEngineProperties properties) {
        return new BotEventService(jdbcTemplate, properties.instanceId(), properties.eventRetention());
    }

    @Bean
    public BotLoader botLoader(UnconstrainedDataManager dataManager) {
        return new BotLoader(dataManager);
    }

    @Bean
    public StrategyRegistry strategyRegistry(ObjectProvider<StrategyProvider> providers) {
        return new StrategyRegistry(providers.orderedStream().toList());
    }

    @Bean
    public BotRunner.Dependencies botRunnerDependencies(BotLoader loader, StrategyRegistry strategies,
                                                        ExchangeGatewayFactory gateways, MarketDataService marketData,
                                                        InstrumentService instruments, BotEventService events,
                                                        BotRuntimeRepository runtime, BotEngineProperties properties,
                                                        SystemAuthenticator systemAuthenticator, Clock clock) {
        return new BotRunner.Dependencies(loader, strategies, gateways, marketData, instruments, events, runtime,
                properties, systemAuthenticator, clock);
    }

    @Bean
    public BotSupervisor botSupervisor(BotRuntimeRepository runtime, BotEventService events,
                                       BotRunner.Dependencies dependencies) {
        return new BotSupervisor(runtime, events, dependencies);
    }

    @Bean
    public BotControlService botControlService(BotLoader loader, BotRuntimeRepository runtime, BotEventService events,
                                               StrategyRegistry strategies, ExchangeGatewayFactory gateways,
                                               BotSupervisor supervisor, UnconstrainedDataManager dataManager,
                                               CurrentAuthentication currentAuthentication) {
        return new BotControlService(loader, runtime, events, strategies, gateways, supervisor, dataManager,
                currentAuthentication);
    }

    @Bean
    public BotRuntimeInitializer botRuntimeInitializer(BotRuntimeRepository runtime) {
        return new BotRuntimeInitializer(runtime);
    }
}
