package ru.javaboys.wootify.exchange;

import org.junit.jupiter.api.Test;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentInfoTest {

    private final InstrumentInfo eth = new InstrumentInfo("PERP_ETH_USDC", new BigDecimal("0.01"),
            new BigDecimal("0.0001"), new BigDecimal("0.0001"), new BigDecimal("1100"), new BigDecimal("10"));

    @Test
    void roundsPriceToTick() {
        assertThat(eth.roundPrice(new BigDecimal("2689.7468"), RoundingMode.DOWN)).isEqualByComparingTo("2689.74");
        assertThat(eth.roundPrice(new BigDecimal("2689.7401"), RoundingMode.UP)).isEqualByComparingTo("2689.75");
        assertThat(eth.roundPrice(new BigDecimal("2689.74"), RoundingMode.UP)).isEqualByComparingTo("2689.74");
    }

    @Test
    void roundsQuantityDownToStep() {
        assertThat(eth.roundQtyDown(new BigDecimal("0.123456"))).isEqualByComparingTo("0.1234");
        assertThat(eth.roundQtyDown(new BigDecimal("0.00009"))).isEqualByComparingTo("0");
    }

    @Test
    void supportsStepsLargerThanOne() {
        InstrumentInfo coarse = new InstrumentInfo("X", new BigDecimal("5"), new BigDecimal("10"),
                BigDecimal.TEN, null, BigDecimal.ONE);
        assertThat(coarse.roundQtyDown(new BigDecimal("129"))).isEqualByComparingTo("120");
        assertThat(coarse.roundPrice(new BigDecimal("12"), RoundingMode.UP)).isEqualByComparingTo("15");
    }

    @Test
    void validatesMinimums() {
        assertThat(eth.validate(new BigDecimal("0.01"), new BigDecimal("2600"))).isNull();
        assertThat(eth.validate(new BigDecimal("0.001"), new BigDecimal("2600"))).contains("стоимость ордера");
        assertThat(eth.validate(new BigDecimal("0.00001"), new BigDecimal("2600"))).contains("меньше минимального");
        assertThat(eth.validate(new BigDecimal("2000"), new BigDecimal("2600"))).contains("больше максимального");
    }
}
