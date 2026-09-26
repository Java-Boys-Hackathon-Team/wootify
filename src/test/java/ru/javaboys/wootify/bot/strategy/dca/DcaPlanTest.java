package ru.javaboys.wootify.bot.strategy.dca;

import org.junit.jupiter.api.Test;
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.DcaSettings;
import ru.javaboys.wootify.entity.TradeType;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DcaPlanTest {

    private static final InstrumentInfo INSTRUMENT = new InstrumentInfo("PERP_TEST_USDC", new BigDecimal("0.01"),
            new BigDecimal("0.001"), new BigDecimal("0.001"), new BigDecimal("100000"), new BigDecimal("5"));

    private static DcaParameters params(TradeType direction, String deposit, int leverage, int orders, String range,
                                        String multiplier, String tp, String sl) {
        return new DcaParameters(direction, new BigDecimal(deposit), leverage, orders, new BigDecimal(range),
                new BigDecimal(multiplier), new BigDecimal(tp), sl == null ? null : new BigDecimal(sl));
    }

    @Test
    void weightsFollowVolumeMultiplierAndSumToOne() {
        List<BigDecimal> w = DcaPlan.weights(params(TradeType.LONG, "100", 1, 3, "4", "2", "1", null));
        assertThat(w).hasSize(3);
        assertThat(w.get(0)).isEqualByComparingTo(new BigDecimal(1).divide(new BigDecimal(7), DcaPlan.MC));
        assertThat(w.get(2)).isEqualByComparingTo(new BigDecimal(4).divide(new BigDecimal(7), DcaPlan.MC));
        assertThat(w.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isCloseTo(BigDecimal.ONE,
                org.assertj.core.data.Offset.offset(new BigDecimal("1e-12")));
    }

    @Test
    void longGridIsBelowBasePriceAndRoundedAwayFromMarket() {
        DcaParameters p = params(TradeType.LONG, "100", 1, 3, "4", "1", "1", null);
        assertThat(DcaPlan.baseQuantity(p, INSTRUMENT, new BigDecimal("100.01"))).isEqualByComparingTo("0.333");

        List<DcaPlan.Level> levels = DcaPlan.safetyLevels(p, INSTRUMENT, new BigDecimal("100.01"));
        assertThat(levels).extracting(DcaPlan.Level::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("98.00"), new BigDecimal("96.00"));
        assertThat(levels).extracting(DcaPlan.Level::quantity)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.340"), new BigDecimal("0.347"));
    }

    @Test
    void shortGridIsAboveBasePrice() {
        DcaParameters p = params(TradeType.SHORT, "300", 2, 4, "6", "1.5", "1", null);
        List<DcaPlan.Level> levels = DcaPlan.safetyLevels(p, INSTRUMENT, new BigDecimal("50"));
        assertThat(levels).extracting(DcaPlan.Level::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("51.00"), new BigDecimal("52.00"), new BigDecimal("53.00"));
        // Объём растёт с множителем 1.5.
        assertThat(levels.get(2).quantity()).isGreaterThan(levels.get(1).quantity());
    }

    @Test
    void takeProfitAndStopLossAreRoundedInFavourOfSafety() {
        DcaParameters longP = params(TradeType.LONG, "100", 1, 3, "4", "1", "1", "10");
        assertThat(DcaPlan.takeProfitPrice(longP, INSTRUMENT, new BigDecimal("99.001"))).isEqualByComparingTo("100.00");
        assertThat(DcaPlan.stopLossPrice(longP, INSTRUMENT, new BigDecimal("100"))).isEqualByComparingTo("90.00");

        DcaParameters shortP = params(TradeType.SHORT, "100", 1, 3, "4", "1", "1", "10");
        assertThat(DcaPlan.takeProfitPrice(shortP, INSTRUMENT, new BigDecimal("100"))).isEqualByComparingTo("99.00");
        assertThat(DcaPlan.stopLossPrice(shortP, INSTRUMENT, new BigDecimal("100"))).isEqualByComparingTo("110.00");
        assertThat(DcaPlan.stopLossPrice(params(TradeType.LONG, "100", 1, 3, "4", "1", "1", null), INSTRUMENT,
                BigDecimal.TEN)).isNull();
    }

    @Test
    void tooSmallDepositIsAConfigurationError() {
        DcaParameters p = params(TradeType.LONG, "20", 1, 5, "4", "1", "1", null);
        assertThatThrownBy(() -> DcaPlan.baseQuantity(p, INSTRUMENT, new BigDecimal("100")))
                .isInstanceOf(BotConfigurationException.class)
                .hasMessageContaining("Базовый ордер")
                .hasMessageContaining("Увеличьте депозит");
    }

    @Test
    void singleOrderPlanHasNoSafetyLevels() {
        DcaParameters p = params(TradeType.LONG, "100", 1, 1, "0", "1", "1", null);
        assertThat(DcaPlan.safetyLevels(p, INSTRUMENT, new BigDecimal("100"))).isEmpty();
        assertThat(DcaPlan.baseQuantity(p, INSTRUMENT, new BigDecimal("100"))).isEqualByComparingTo("1.000");
    }

    @Test
    void validatesSettings() {
        DcaSettings s = new DcaSettings();
        s.setDirection(TradeType.LONG);
        s.setDeposit(new BigDecimal("100"));
        s.setLeverage(1);
        s.setOrdersCount(3);
        s.setGridRangePercent(new BigDecimal("5"));
        s.setVolumeMultiplier(BigDecimal.ONE);
        s.setTakeProfitPercent(BigDecimal.ONE);
        assertThat(DcaParameters.validate(s)).isEmpty();

        s.setStopLossPercent(new BigDecimal("3"));
        assertThat(DcaParameters.validate(s)).singleElement().asString().contains("больше диапазона сетки");

        s.setStopLossPercent(null);
        s.setGridRangePercent(BigDecimal.ZERO);
        assertThat(DcaParameters.validate(s)).singleElement().asString().contains("диапазон сетки");

        assertThat(DcaParameters.validate(null)).isNotEmpty();
    }
}
