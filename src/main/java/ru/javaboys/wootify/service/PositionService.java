package ru.javaboys.wootify.service;

import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.Position;
import ru.javaboys.wootify.entity.Symbol;

public interface PositionService {
    Double setLeverageForTicker(CurrentAccountState accountState, CurrentDealState dealState);
    Double getLeverageForTicker(CurrentAccountState accountState, CurrentDealState dealState);

    Position getActivePositionForTicker(CurrentAccountState accountState, Symbol symbol);
    Position createPositionForTicker(CurrentAccountState accountState, CurrentDealState dealState);
}
