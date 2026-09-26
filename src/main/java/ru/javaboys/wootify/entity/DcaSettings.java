package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Параметры стратегии усреднения (DCA): базовый ордер и сетка страховочных ордеров,
 * фиксация прибыли от средней цены входа и необязательный стоп-лосс.
 */
@JmixEntity
@Table(name = "DCA_SETTINGS")
@Entity
public class DcaSettings {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "DIRECTION", nullable = false, length = 16)
    private String direction;

    @NotNull
    @Positive
    @Column(name = "DEPOSIT", nullable = false, precision = 19, scale = 8)
    private BigDecimal deposit;

    @NotNull
    @Min(1)
    @Max(50)
    @Column(name = "LEVERAGE", nullable = false)
    private Integer leverage;

    @NotNull
    @Min(1)
    @Max(50)
    @Column(name = "ORDERS_COUNT", nullable = false)
    private Integer ordersCount;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("90.0")
    @Column(name = "GRID_RANGE_PERCENT", nullable = false, precision = 9, scale = 4)
    private BigDecimal gridRangePercent;

    @NotNull
    @DecimalMin("1.0")
    @DecimalMax("5.0")
    @Column(name = "VOLUME_MULTIPLIER", nullable = false, precision = 9, scale = 4)
    private BigDecimal volumeMultiplier;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    @Column(name = "TAKE_PROFIT_PERCENT", nullable = false, precision = 9, scale = 4)
    private BigDecimal takeProfitPercent;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    @Column(name = "STOP_LOSS_PERCENT", precision = 9, scale = 4)
    private BigDecimal stopLossPercent;

    @Min(1)
    @Column(name = "MAX_CYCLES")
    private Integer maxCycles;

    @NotNull
    @Min(0)
    @Column(name = "CYCLE_COOLDOWN_SEC", nullable = false)
    private Integer cycleCooldownSec;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public TradeType getDirection() {
        return direction == null ? null : TradeType.fromId(direction);
    }

    public void setDirection(TradeType direction) {
        this.direction = direction == null ? null : direction.getId();
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public Integer getLeverage() {
        return leverage;
    }

    public void setLeverage(Integer leverage) {
        this.leverage = leverage;
    }

    public Integer getOrdersCount() {
        return ordersCount;
    }

    public void setOrdersCount(Integer ordersCount) {
        this.ordersCount = ordersCount;
    }

    public BigDecimal getGridRangePercent() {
        return gridRangePercent;
    }

    public void setGridRangePercent(BigDecimal gridRangePercent) {
        this.gridRangePercent = gridRangePercent;
    }

    public BigDecimal getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public void setVolumeMultiplier(BigDecimal volumeMultiplier) {
        this.volumeMultiplier = volumeMultiplier;
    }

    public BigDecimal getTakeProfitPercent() {
        return takeProfitPercent;
    }

    public void setTakeProfitPercent(BigDecimal takeProfitPercent) {
        this.takeProfitPercent = takeProfitPercent;
    }

    public BigDecimal getStopLossPercent() {
        return stopLossPercent;
    }

    public void setStopLossPercent(BigDecimal stopLossPercent) {
        this.stopLossPercent = stopLossPercent;
    }

    public Integer getMaxCycles() {
        return maxCycles;
    }

    public void setMaxCycles(Integer maxCycles) {
        this.maxCycles = maxCycles;
    }

    public Integer getCycleCooldownSec() {
        return cycleCooldownSec;
    }

    public void setCycleCooldownSec(Integer cycleCooldownSec) {
        this.cycleCooldownSec = cycleCooldownSec;
    }
}
