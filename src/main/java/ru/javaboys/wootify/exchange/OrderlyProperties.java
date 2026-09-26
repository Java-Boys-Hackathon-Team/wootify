package ru.javaboys.wootify.exchange;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.javaboys.wootify.entity.Network;

/**
 * Адреса публичного API Orderly по сетям.
 *
 * @param accountId идентификатор, который Orderly требует в пути публичного WebSocket-потока
 */
@ConfigurationProperties("wootify.orderly")
public record OrderlyProperties(Endpoint mainnet, Endpoint testnet, String accountId) {

    public record Endpoint(String restUrl, String wsUrl) {
    }

    public OrderlyProperties {
        if (mainnet == null) {
            mainnet = new Endpoint("https://api-evm.orderly.org", "wss://ws-evm.orderly.org/ws/stream/");
        }
        if (testnet == null) {
            testnet = new Endpoint("https://testnet-api-evm.orderly.org", "wss://testnet-ws-evm.orderly.org/ws/stream/");
        }
        if (accountId == null || accountId.isBlank()) {
            accountId = "wootify";
        }
    }

    public Endpoint endpoint(Network network) {
        return network == Network.TESTNET ? testnet : mainnet;
    }
}
