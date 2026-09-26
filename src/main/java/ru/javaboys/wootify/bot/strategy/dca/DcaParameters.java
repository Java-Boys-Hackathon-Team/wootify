package ru.javaboys.wootify.bot.strategy.dca;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ru.javaboys.wootify.entity.DcaSettings;
import ru.javaboys.wootify.entity.TradeType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Снимок настроек DCA. Сохраняется в плане цикла, чтобы изменение настроек не влияло на уже открытый цикл.
 */
public record DcaParameters(TradeType direction, BigDecimal deposit, int leverage, int ordersCount,
                            BigDecimal gridRangePercent, BigDecimal volumeMultiplier, BigDecimal takeProfitPercent,
                            BigDecimal stopLossPercent) {

    public static DcaParameters of(DcaSettings s) {
        return new DcaParameters(s.getDirection(), s.getDeposit(), s.getLeverage(), s.getOrdersCount(),
                s.getGridRangePercent(), s.getVolumeMultiplier(), s.getTakeProfitPercent(), s.getStopLossPercent());
    }

    @JsonIgnore
    public boolean isLong() {
        return direction == TradeType.LONG;
    }

    /**
     * Проверки, не требующие биржи.
     */
    public static List<String> validate(DcaSettings s) {
        List<String> problems = new ArrayList<>();
        if (s == null) {
            problems.add("Не заданы параметры стратегии DCA");
            return problems;
        }
        if (s.getDirection() == null || s.getDeposit() == null || s.getLeverage() == null
            || s.getOrdersCount() == null || s.getGridRangePercent() == null || s.getVolumeMultiplier() == null
            || s.getTakeProfitPercent() == null) {
            problems.add("Заполнены не все обязательные параметры DCA");
            return problems;
        }
        if (s.getDeposit().signum() <= 0) {
            problems.add("Депозит должен быть положительным");
        }
        if (s.getLeverage() < 1 || s.getLeverage() > 50) {
            problems.add("Плечо должно быть от 1 до 50");
        }
        if (s.getOrdersCount() < 1 || s.getOrdersCount() > 50) {
            problems.add("Число ордеров должно быть от 1 до 50");
        }
        if (s.getOrdersCount() > 1 && s.getGridRangePercent().signum() <= 0) {
            problems.add("Для страховочных ордеров диапазон сетки должен быть больше нуля");
        }
        if (s.getGridRangePercent().compareTo(new BigDecimal("90")) > 0) {
            problems.add("Диапазон сетки не может превышать 90%");
        }
        if (s.getVolumeMultiplier().compareTo(BigDecimal.ONE) < 0) {
            problems.add("Множитель объёма не может быть меньше 1");
        }
        if (s.getTakeProfitPercent().signum() <= 0) {
            problems.add("Процент фиксации прибыли должен быть больше нуля");
        }
        if (s.getStopLossPercent() != null && s.getStopLossPercent().compareTo(s.getGridRangePercent()) <= 0) {
            problems.add("Стоп-лосс отсчитывается от цены базового ордера и должен быть больше диапазона сетки");
        }
        if (s.getStopLossPercent() != null && s.getDirection() == TradeType.LONG
            && s.getStopLossPercent().compareTo(new BigDecimal("100")) >= 0) {
            problems.add("Стоп-лосс для LONG должен быть меньше 100%");
        }
        return problems;
    }
}
