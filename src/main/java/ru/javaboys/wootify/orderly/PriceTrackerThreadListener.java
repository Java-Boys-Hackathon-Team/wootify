package ru.javaboys.wootify.orderly;

import ru.javaboys.wootify.orderly.model.PriceData;

public interface PriceTrackerThreadListener {

    void pingReceived();

    void priceDataReceived(PriceData data);

}
