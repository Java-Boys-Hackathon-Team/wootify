package ru.javaboys.wootify.exchange.marketdata;

import ru.javaboys.wootify.entity.Network;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Источник текущих цен. Подписка на инструмент оформляется при первом обращении к нему
 * и сохраняется, поэтому движку достаточно просто спрашивать цену.
 */
public interface MarketDataService {

    /**
     * Последняя известная котировка или пустое значение, если данных ещё нет.
     */
    Optional<Quote> latestQuote(Network network, String symbol);

    /**
     * Котировка не старше допустимого возраста ({@code wootify.market-data.max-quote-age}).
     *
     * @throws MarketDataUnavailableException если свежей котировки нет
     */
    Quote freshQuote(Network network, String symbol);

    /**
     * Подписка на обновления котировок, например для UI.
     *
     * @return действие отписки
     */
    Runnable addListener(Network network, String symbol, Consumer<Quote> listener);
}
