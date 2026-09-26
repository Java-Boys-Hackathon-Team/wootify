package ru.javaboys.wootify.bot.strategy.dca;

import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Расчёт сетки DCA.
 * <p>
 * Бюджет цикла в котируемой валюте - {@code депозит × плечо}. Он делится между N ордерами пропорционально
 * {@code M^i}, где M - множитель объёма, i - номер ордера (0 - базовый). Страховочные ордера равномерно
 * покрывают диапазон R% от цены исполнения базового: для LONG ниже неё, для SHORT выше.
 * Цены округляются от рынка (LONG вниз, SHORT вверх), объёмы - вниз до шага лота.
 */
public final class DcaPlan {

    static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Level(int level, BigDecimal price, BigDecimal quantity) {
    }

    private DcaPlan() {
    }

    /** Доли бюджета по ордерам, сумма равна 1. */
    static List<BigDecimal> weights(DcaParameters p) {
        List<BigDecimal> raw = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < p.ordersCount(); i++) {
            BigDecimal w = p.volumeMultiplier().pow(i, MC);
            raw.add(w);
            sum = sum.add(w, MC);
        }
        List<BigDecimal> result = new ArrayList<>();
        for (BigDecimal w : raw) {
            result.add(w.divide(sum, MC));
        }
        return result;
    }

    static BigDecimal budget(DcaParameters p) {
        return p.deposit().multiply(BigDecimal.valueOf(p.leverage()), MC);
    }

    /**
     * Объём базового ордера по текущей цене.
     */
    public static BigDecimal baseQuantity(DcaParameters p, InstrumentInfo instrument, BigDecimal price) {
        BigDecimal quote = budget(p).multiply(weights(p).get(0), MC);
        BigDecimal qty = instrument.roundQtyDown(quote.divide(price, MC));
        String problem = instrument.validate(qty, price);
        if (problem != null) {
            throw new BotConfigurationException("Базовый ордер: " + problem
                                                + ". Увеличьте депозит или плечо либо уменьшите число ордеров");
        }
        return qty;
    }

    /**
     * Страховочные ордера от цены исполнения базового.
     */
    public static List<Level> safetyLevels(DcaParameters p, InstrumentInfo instrument, BigDecimal basePrice) {
        List<Level> levels = new ArrayList<>();
        int n = p.ordersCount();
        if (n < 2) {
            return levels;
        }
        List<BigDecimal> weights = weights(p);
        BigDecimal budget = budget(p);
        BigDecimal range = p.gridRangePercent().divide(HUNDRED, MC);
        for (int i = 1; i < n; i++) {
            BigDecimal offset = range.multiply(BigDecimal.valueOf(i), MC).divide(BigDecimal.valueOf(n - 1), MC);
            BigDecimal factor = p.isLong() ? BigDecimal.ONE.subtract(offset, MC) : BigDecimal.ONE.add(offset, MC);
            BigDecimal price = instrument.roundPrice(basePrice.multiply(factor, MC),
                    p.isLong() ? RoundingMode.DOWN : RoundingMode.UP);
            if (price.signum() <= 0) {
                throw new BotConfigurationException("Страховочный ордер " + i + ": цена получается неположительной");
            }
            BigDecimal qty = instrument.roundQtyDown(budget.multiply(weights.get(i), MC).divide(price, MC));
            String problem = instrument.validate(qty, price);
            if (problem != null) {
                throw new BotConfigurationException("Страховочный ордер " + i + ": " + problem
                                                    + ". Увеличьте депозит или плечо либо уменьшите число ордеров");
            }
            levels.add(new Level(i, price, qty));
        }
        return levels;
    }

    /** Цена фиксации прибыли от средней цены входа, округлённая в пользу прибыли. */
    public static BigDecimal takeProfitPrice(DcaParameters p, InstrumentInfo instrument, BigDecimal avgEntry) {
        BigDecimal k = p.takeProfitPercent().divide(HUNDRED, MC);
        BigDecimal raw = avgEntry.multiply(p.isLong() ? BigDecimal.ONE.add(k, MC) : BigDecimal.ONE.subtract(k, MC), MC);
        return instrument.roundPrice(raw, p.isLong() ? RoundingMode.UP : RoundingMode.DOWN);
    }

    /** Цена стоп-лосса от цены базового ордера или {@code null}, если стоп-лосс не задан. */
    public static BigDecimal stopLossPrice(DcaParameters p, InstrumentInfo instrument, BigDecimal basePrice) {
        if (p.stopLossPercent() == null) {
            return null;
        }
        BigDecimal k = p.stopLossPercent().divide(HUNDRED, MC);
        BigDecimal raw = basePrice.multiply(p.isLong() ? BigDecimal.ONE.subtract(k, MC) : BigDecimal.ONE.add(k, MC), MC);
        return instrument.roundPrice(raw, p.isLong() ? RoundingMode.DOWN : RoundingMode.UP);
    }
}
