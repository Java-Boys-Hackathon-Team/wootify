package ru.javaboys.wootify.exchange;

import ru.javaboys.wootify.entity.Bot;

/**
 * Создаёт шлюз под режим бота: бумажная торговля или реальная через мост.
 */
public interface ExchangeGatewayFactory {

    ExchangeGateway forBot(Bot bot);
}
