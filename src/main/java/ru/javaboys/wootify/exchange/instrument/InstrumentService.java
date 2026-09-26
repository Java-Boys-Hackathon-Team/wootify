package ru.javaboys.wootify.exchange.instrument;

import ru.javaboys.wootify.entity.Network;

public interface InstrumentService {

    /**
     * @throws InstrumentUnavailableException если сведения временно недоступны
     * @throws IllegalArgumentException       если инструмент неизвестен бирже
     */
    InstrumentInfo instrument(Network network, String symbol);
}
