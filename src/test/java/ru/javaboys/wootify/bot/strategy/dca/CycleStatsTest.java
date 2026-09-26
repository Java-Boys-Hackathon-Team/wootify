package ru.javaboys.wootify.bot.strategy.dca;

import org.junit.jupiter.api.Test;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderRole;
import ru.javaboys.wootify.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CycleStatsTest {

    private static Order order(OrderRole role, OrderStatus status, String qty, String price, String fee) {
        Order o = new Order();
        o.setRole(role);
        o.setStatus(status);
        o.setTotalExecutedQuantity(qty == null ? null : new BigDecimal(qty));
        o.setAverageExecutedPrice(price == null ? null : new BigDecimal(price));
        o.setTotalFee(fee == null ? null : new BigDecimal(fee));
        return o;
    }

    @Test
    void longCycleWithSafetyFillAndTakeProfit() {
        List<Order> orders = List.of(
                order(OrderRole.BASE, OrderStatus.CLOSED, "1", "100", "0.05"),
                order(OrderRole.SAFETY, OrderStatus.CLOSED, "1", "96", "0.02"),
                order(OrderRole.SAFETY, OrderStatus.CANCELLED, "0", null, "0"),
                order(OrderRole.TAKE_PROFIT, OrderStatus.CLOSED, "2", "99", "0.04"));
        CycleStats stats = CycleStats.of(orders);
        assertThat(stats.entryQty()).isEqualByComparingTo("2");
        assertThat(stats.avgEntryPrice()).isEqualByComparingTo("98");
        assertThat(stats.netQty()).isEqualByComparingTo("0");
        assertThat(stats.filledSafetyOrders()).isEqualTo(1);
        assertThat(stats.fees()).isEqualByComparingTo("0.11");
        // (99 - 98) * 2 - 0.11
        assertThat(stats.realizedPnl(true)).isEqualByComparingTo("1.89");
    }

    @Test
    void shortCycleProfitsWhenPriceFalls() {
        List<Order> orders = List.of(
                order(OrderRole.BASE, OrderStatus.CLOSED, "2", "50", "0.05"),
                order(OrderRole.TAKE_PROFIT, OrderStatus.CLOSED, "2", "49.5", "0.02"));
        assertThat(CycleStats.of(orders).realizedPnl(false)).isEqualByComparingTo("0.93");
    }

    @Test
    void partiallyClosedPositionCountsOnlyClosedVolume() {
        List<Order> orders = List.of(
                order(OrderRole.BASE, OrderStatus.CLOSED, "2", "100", "0"),
                order(OrderRole.CLOSE, OrderStatus.CLOSED, "1", "90", "0"));
        CycleStats stats = CycleStats.of(orders);
        assertThat(stats.netQty()).isEqualByComparingTo("1");
        assertThat(stats.realizedPnl(true)).isEqualByComparingTo("-10");
    }
}
