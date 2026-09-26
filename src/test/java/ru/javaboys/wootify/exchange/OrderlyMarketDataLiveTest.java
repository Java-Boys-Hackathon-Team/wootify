package ru.javaboys.wootify.exchange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.exchange.instrument.OrderlyInstrumentService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataProperties;
import ru.javaboys.wootify.exchange.marketdata.OrderlyMarketDataService;
import ru.javaboys.wootify.exchange.marketdata.Quote;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Проверка на живом Orderly. Запуск: ORDERLY_LIVE_TESTS=true ./gradlew test --tests '*LiveTest'
 */
@EnabledIfEnvironmentVariable(named = "ORDERLY_LIVE_TESTS", matches = "true")
class OrderlyMarketDataLiveTest {

    private final OrderlyProperties properties = new OrderlyProperties(null, null, null);

    /**
     * Проходит и без WebSocket (например, за прокси): тогда цены приходят через резервный REST-опрос.
     */
    @Test
    void receivesQuotesFromBothNetworks() {
        OrderlyMarketDataService service = new OrderlyMarketDataService(properties,
                new MarketDataProperties("orderly", Duration.ofSeconds(30), true, Duration.ofSeconds(2)), Clock.systemUTC());
        try {
            for (Network network : Network.values()) {
                service.latestQuote(network, "PERP_ETH_USDC");
                await().atMost(Duration.ofSeconds(30)).ignoreExceptions()
                        .until(() -> service.freshQuote(network, "PERP_ETH_USDC") != null);
                Quote quote = service.freshQuote(network, "PERP_ETH_USDC");
                assertThat(quote.bid()).isPositive();
                assertThat(quote.ask()).isGreaterThanOrEqualTo(quote.bid());
            }
        } finally {
            service.close();
        }
    }

    @Test
    void loadsInstrumentInfo() {
        var info = new OrderlyInstrumentService(properties, Clock.systemUTC()).instrument(Network.MAINNET, "PERP_ETH_USDC");
        assertThat(info.baseTick()).isPositive();
        assertThat(info.minNotional()).isPositive();
    }
}
