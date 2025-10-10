package ru.javaboys.wootify.orderly;

import ru.javaboys.wootify.orderly.model.PriceData;

public interface PriceListener {

    void priceReceived(PriceData data);

}
