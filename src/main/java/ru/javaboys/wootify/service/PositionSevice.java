package ru.javaboys.wootify.service;

import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.entity.Position;
import ru.javaboys.wootify.entity.Symbol;

public interface PositionSevice {
    Double setLeverageForTicker(Account account, ApiKey apiKey, Double leverage, Symbol symbol);
    Position getActivePositionForTicker(Account account, ApiKey apiKey, Symbol symbol);
    Position createPositionForTicker(Account account, ApiKey apiKey, Symbol symbol, Double leverage);
}
