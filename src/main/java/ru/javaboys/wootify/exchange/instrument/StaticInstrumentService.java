package ru.javaboys.wootify.exchange.instrument;

import ru.javaboys.wootify.entity.Network;

import java.math.BigDecimal;

/**
 * Одинаковые ограничения для всех инструментов. Используется вместе с синтетическими котировками.
 */
public class StaticInstrumentService implements InstrumentService {

    @Override
    public InstrumentInfo instrument(Network network, String symbol) {
        return new InstrumentInfo(symbol, new BigDecimal("0.01"), new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("1000000"), new BigDecimal("5"));
    }
}
