package ru.javaboys.wootify.exchange;

import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.entity.TradingMode;
import ru.javaboys.wootify.exchange.bridge.BridgeExchangeGateway;
import ru.javaboys.wootify.exchange.bridge.BridgeTradingClient;
import ru.javaboys.wootify.exchange.paper.PaperExchange;
import ru.javaboys.wootify.exchange.paper.PaperExchangeGateway;

public class DefaultExchangeGatewayFactory implements ExchangeGatewayFactory {

    private final PaperExchange paperExchange;
    private final BridgeTradingClient bridgeClient;

    public DefaultExchangeGatewayFactory(PaperExchange paperExchange, BridgeTradingClient bridgeClient) {
        this.paperExchange = paperExchange;
        this.bridgeClient = bridgeClient;
    }

    @Override
    public ExchangeGateway forBot(Bot bot) {
        if (bot.getTradingMode() == TradingMode.PAPER) {
            return new PaperExchangeGateway(paperExchange, bot.getNetwork());
        }
        ApiKey apiKey = bot.getApiKey();
        Account account = bot.getAccount();
        if (apiKey == null || account == null) {
            throw new BotConfigurationException("Для реальной торговли укажите аккаунт и ключ API");
        }
        if (account.getWoofiId() == null || apiKey.getKey() == null || apiKey.getSecret() == null) {
            throw new BotConfigurationException("У аккаунта или ключа API не заполнены идентификатор, ключ или секрет");
        }
        Network keyNetwork = "testnet".equalsIgnoreCase(apiKey.getEnv()) ? Network.TESTNET : Network.MAINNET;
        if (keyNetwork != bot.getNetwork()) {
            throw new BotConfigurationException("Ключ API относится к сети " + keyNetwork
                                                + ", а бот настроен на " + bot.getNetwork());
        }
        return new BridgeExchangeGateway(bridgeClient, new BridgeExchangeGateway.Credentials(
                apiKey.getKey(), apiKey.getSecret(), account.getWoofiId(), apiKey.getBrokerId(),
                keyNetwork == Network.TESTNET ? "testnet" : "mainnet"));
    }
}
