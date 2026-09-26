package ru.javaboys.wootify.bot.strategy.dca;

import ru.javaboys.wootify.bot.order.BotOrderService;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderRole;
import ru.javaboys.wootify.entity.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Позиция цикла, посчитанная по исполнениям его ордеров. Входы - базовый и страховочные ордера,
 * выходы - фиксация прибыли и закрытие.
 */
public record CycleStats(BigDecimal entryQty, BigDecimal entryCost, BigDecimal exitQty, BigDecimal exitProceeds,
                         BigDecimal fees, int filledSafetyOrders) {

    public static CycleStats of(List<Order> orders) {
        BigDecimal entryQty = BigDecimal.ZERO;
        BigDecimal entryCost = BigDecimal.ZERO;
        BigDecimal exitQty = BigDecimal.ZERO;
        BigDecimal exitProceeds = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        int filledSafety = 0;
        for (Order o : orders) {
            BigDecimal qty = BotOrderService.filled(o);
            if (qty.signum() <= 0 || o.getAverageExecutedPrice() == null) {
                continue;
            }
            BigDecimal notional = qty.multiply(o.getAverageExecutedPrice());
            if (o.getTotalFee() != null) {
                fees = fees.add(o.getTotalFee());
            }
            if (isEntry(o)) {
                entryQty = entryQty.add(qty);
                entryCost = entryCost.add(notional);
                if (o.getRole() == OrderRole.SAFETY && o.getStatus() == OrderStatus.CLOSED) {
                    filledSafety++;
                }
            } else {
                exitQty = exitQty.add(qty);
                exitProceeds = exitProceeds.add(notional);
            }
        }
        return new CycleStats(entryQty, entryCost, exitQty, exitProceeds, fees, filledSafety);
    }

    public static boolean isEntry(Order o) {
        return o.getRole() == OrderRole.BASE || o.getRole() == OrderRole.SAFETY;
    }

    public BigDecimal netQty() {
        return entryQty.subtract(exitQty);
    }

    public BigDecimal avgEntryPrice() {
        return entryQty.signum() == 0 ? null : entryCost.divide(entryQty, 12, RoundingMode.HALF_UP);
    }

    /**
     * Реализованный результат по закрытому объёму с учётом всех комиссий цикла.
     * Для LONG вход - покупка, выход - продажа; для SHORT наоборот.
     */
    public BigDecimal realizedPnl(boolean isLong) {
        BigDecimal avg = avgEntryPrice();
        if (avg == null) {
            return fees.negate();
        }
        BigDecimal closedCost = avg.multiply(exitQty);
        BigDecimal gross = isLong ? exitProceeds.subtract(closedCost) : closedCost.subtract(exitProceeds);
        return gross.subtract(fees).setScale(8, RoundingMode.HALF_UP);
    }
}
