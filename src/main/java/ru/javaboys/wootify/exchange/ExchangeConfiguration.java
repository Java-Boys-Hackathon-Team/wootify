package ru.javaboys.wootify.exchange;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.javaboys.wootify.exchange.bridge.BridgeTradingClient;
import ru.javaboys.wootify.exchange.instrument.InstrumentService;
import ru.javaboys.wootify.exchange.instrument.OrderlyInstrumentService;
import ru.javaboys.wootify.exchange.instrument.StaticInstrumentService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataProperties;
import ru.javaboys.wootify.exchange.marketdata.MarketDataService;
import ru.javaboys.wootify.exchange.marketdata.OrderlyMarketDataService;
import ru.javaboys.wootify.exchange.marketdata.SyntheticMarketDataService;
import ru.javaboys.wootify.exchange.paper.PaperExchange;
import ru.javaboys.wootify.exchange.paper.PaperTradingProperties;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({OrderlyProperties.class, MarketDataProperties.class, PaperTradingProperties.class})
public class ExchangeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "wootify.market-data.source", havingValue = "orderly", matchIfMissing = true)
    public MarketDataService orderlyMarketDataService(OrderlyProperties orderly, MarketDataProperties properties,
                                                      Clock clock) {
        return new OrderlyMarketDataService(orderly, properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "wootify.market-data.source", havingValue = "synthetic")
    public MarketDataService syntheticMarketDataService(Clock clock) {
        return new SyntheticMarketDataService(clock);
    }

    @Bean
    @ConditionalOnProperty(name = "wootify.market-data.source", havingValue = "orderly", matchIfMissing = true)
    public InstrumentService orderlyInstrumentService(OrderlyProperties orderly, Clock clock) {
        return new OrderlyInstrumentService(orderly, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "wootify.market-data.source", havingValue = "synthetic")
    public InstrumentService staticInstrumentService() {
        return new StaticInstrumentService();
    }

    @Bean
    public PaperExchange paperExchange(JdbcTemplate jdbcTemplate, MarketDataService marketData,
                                       PaperTradingProperties properties, Clock clock) {
        return new PaperExchange(jdbcTemplate, marketData, properties, clock);
    }

    @Bean
    public ExchangeGatewayFactory exchangeGatewayFactory(PaperExchange paperExchange, BridgeTradingClient bridgeClient) {
        return new DefaultExchangeGatewayFactory(paperExchange, bridgeClient);
    }
}
