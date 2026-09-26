package ru.javaboys.wootify.exchange.paper;

import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.exchange.ExchangeGateway;
import ru.javaboys.wootify.exchange.ExchangeOrder;
import ru.javaboys.wootify.exchange.MarketSymbol;
import ru.javaboys.wootify.exchange.PlaceOrderCommand;

import java.util.Optional;

/**
 * Шлюз бумажной торговли для конкретной сети (определяет, чьи цены используются).
 */
public class PaperExchangeGateway implements ExchangeGateway {

    private final PaperExchange exchange;
    private final Network network;

    public PaperExchangeGateway(PaperExchange exchange, Network network) {
        this.exchange = exchange;
        this.network = network;
    }

    @Override
    public ExchangeOrder placeOrder(PlaceOrderCommand command) {
        return exchange.place(network, command);
    }

    @Override
    public Optional<ExchangeOrder> findOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId) {
        return exchange.find(clientOrderId, exchangeOrderId);
    }

    @Override
    public void cancelOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId) {
        exchange.cancel(clientOrderId);
    }

    @Override
    public void setLeverage(MarketSymbol symbol, int leverage) {
        // Симулятор не учитывает маржу и плечо.
    }
}
