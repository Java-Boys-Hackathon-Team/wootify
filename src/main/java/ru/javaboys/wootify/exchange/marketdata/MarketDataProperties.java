package ru.javaboys.wootify.exchange.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param source       источник котировок: {@code orderly} (WebSocket Orderly) или {@code synthetic}
 *                     (случайное блуждание, для работы без сети)
 * @param maxQuoteAge      котировка старше этого возраста считается устаревшей
 * @param restFallback     опрашивать REST Orderly, если WebSocket-поток недоступен
 * @param restPollInterval период опроса REST
 */
@ConfigurationProperties("wootify.market-data")
public record MarketDataProperties(String source, Duration maxQuoteAge, Boolean restFallback,
                                   Duration restPollInterval) {

    public MarketDataProperties {
        if (source == null) {
            source = "orderly";
        }
        if (maxQuoteAge == null) {
            maxQuoteAge = Duration.ofSeconds(30);
        }
        if (restFallback == null) {
            restFallback = true;
        }
        if (restPollInterval == null) {
            restPollInterval = Duration.ofSeconds(3);
        }
    }
}
