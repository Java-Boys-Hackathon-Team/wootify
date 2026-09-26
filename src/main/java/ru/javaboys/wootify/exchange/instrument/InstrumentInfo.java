package ru.javaboys.wootify.exchange.instrument;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Торговые ограничения инструмента.
 *
 * @param quoteTick   шаг цены
 * @param baseTick    шаг объёма
 * @param baseMin     минимальный объём ордера
 * @param baseMax     максимальный объём ордера
 * @param minNotional минимальная стоимость ордера в котируемой валюте
 */
public record InstrumentInfo(String symbol, BigDecimal quoteTick, BigDecimal baseTick,
                             BigDecimal baseMin, BigDecimal baseMax, BigDecimal minNotional) {

    public BigDecimal roundPrice(BigDecimal price, RoundingMode mode) {
        return roundToStep(price, quoteTick, mode);
    }

    public BigDecimal roundQtyDown(BigDecimal qty) {
        return roundToStep(qty, baseTick, RoundingMode.DOWN);
    }

    /**
     * Проверяет ордер на ограничения инструмента.
     *
     * @return описание нарушения или {@code null}, если ордер допустим
     */
    public String validate(BigDecimal qty, BigDecimal price) {
        if (qty.compareTo(baseMin) < 0) {
            return "объём " + qty.stripTrailingZeros().toPlainString() + " меньше минимального "
                   + baseMin.stripTrailingZeros().toPlainString();
        }
        if (baseMax != null && baseMax.signum() > 0 && qty.compareTo(baseMax) > 0) {
            return "объём " + qty.stripTrailingZeros().toPlainString() + " больше максимального "
                   + baseMax.stripTrailingZeros().toPlainString();
        }
        BigDecimal notional = qty.multiply(price);
        if (notional.compareTo(minNotional) < 0) {
            return "стоимость ордера " + notional.setScale(2, RoundingMode.HALF_UP).toPlainString()
                   + " меньше минимальной " + minNotional.stripTrailingZeros().toPlainString();
        }
        return null;
    }

    static BigDecimal roundToStep(BigDecimal value, BigDecimal step, RoundingMode mode) {
        if (step == null || step.signum() == 0) {
            return value;
        }
        BigDecimal steps = value.divide(step, 0, mode);
        return steps.multiply(step).stripTrailingZeros().setScale(Math.max(step.stripTrailingZeros().scale(), 0), RoundingMode.UNNECESSARY);
    }
}
