package ru.javaboys.wootify.exchange;

import ru.javaboys.wootify.entity.Symbol;

/**
 * Инструмент в двух нотациях: Orderly ({@code PERP_ETH_USDC}) и CCXT ({@code ETH/USDC:USDC}).
 */
public record MarketSymbol(String orderly, String ccxt) {

    public static MarketSymbol of(Symbol symbol) {
        return new MarketSymbol(symbol.getWoofiTicker(), symbol.getAnalogTicker());
    }

    @Override
    public String toString() {
        return orderly;
    }
}
