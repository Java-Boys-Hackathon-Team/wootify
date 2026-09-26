package ru.javaboys.wootify.exchange;

import java.util.Optional;

/**
 * Единая точка доступа бота к бирже. Реализации: симулятор бумажной торговли и мост к WOOFi Pro.
 * <p>
 * Все операции идемпотентны относительно {@code clientOrderId}: повторное размещение ордера с тем же
 * идентификатором не создаёт второй ордер, отмена завершённого ордера не является ошибкой.
 * Ошибки сообщаются через {@link ExchangeException} с категорией.
 */
public interface ExchangeGateway {

    ExchangeOrder placeOrder(PlaceOrderCommand command);

    /**
     * Ищет ордер по идентификатору клиента, а при его отсутствии на бирже - по биржевому.
     *
     * @return пустое значение, если биржа такого ордера не знает
     */
    Optional<ExchangeOrder> findOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId);

    void cancelOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId);

    void setLeverage(MarketSymbol symbol, int leverage);
}
