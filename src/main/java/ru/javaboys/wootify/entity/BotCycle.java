package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.annotation.NumberFormat;
import io.jmix.core.metamodel.annotation.DateTimeFormat;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Цикл (сделка) стратегии: от базового ордера до закрытия позиции.
 * {@code plan} хранит снимок параметров и рассчитанную сетку на момент открытия цикла.
 */
@JmixEntity
@Table(name = "BOT_CYCLE", indexes = {
        @Index(name = "IDX_BOT_CYCLE_BOT", columnList = "BOT_ID")
}, uniqueConstraints = {
        @UniqueConstraint(name = "IDX_BOT_CYCLE_UNQ", columnNames = {"BOT_ID", "NUMBER_"})
})
@Entity
public class BotCycle {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "BOT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Bot bot;

    @Column(name = "NUMBER_", nullable = false)
    private Integer number;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "DIRECTION", nullable = false, length = 16)
    private String direction;

    @Column(name = "STARTED_AT", nullable = false)
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private OffsetDateTime startedAt;

    @Column(name = "CLOSED_AT")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private OffsetDateTime closedAt;

    @Column(name = "BASE_PRICE", precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal basePrice;

    @Column(name = "AVG_ENTRY_PRICE", precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal avgEntryPrice;

    @Column(name = "ENTRY_QTY", nullable = false, precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal entryQty;

    @Column(name = "ENTRY_COST", nullable = false, precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal entryCost;

    @Column(name = "EXIT_QTY", nullable = false, precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal exitQty;

    @Column(name = "EXIT_PROCEEDS", nullable = false, precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal exitProceeds;

    @Column(name = "FEES", nullable = false, precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal fees;

    @Column(name = "FILLED_SAFETY_ORDERS", nullable = false)
    private Integer filledSafetyOrders;

    @Column(name = "TAKE_PROFIT_PRICE", precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal takeProfitPrice;

    @Column(name = "STOP_LOSS_PRICE", precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal stopLossPrice;

    @Column(name = "REALIZED_PNL", precision = 28, scale = 12)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal realizedPnl;

    @Column(name = "CLOSE_REASON", length = 32)
    private String closeReason;

    @Lob
    @Column(name = "PLAN")
    private String plan;

    @InstanceName
    @DependsOnProperties({"number"})
    public String getDisplayName() {
        return "#" + number;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Bot getBot() {
        return bot;
    }

    public void setBot(Bot bot) {
        this.bot = bot;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public CycleStatus getStatus() {
        return status == null ? null : CycleStatus.fromId(status);
    }

    public void setStatus(CycleStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public TradeType getDirection() {
        return direction == null ? null : TradeType.fromId(direction);
    }

    public void setDirection(TradeType direction) {
        this.direction = direction == null ? null : direction.getId();
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getAvgEntryPrice() {
        return avgEntryPrice;
    }

    public void setAvgEntryPrice(BigDecimal avgEntryPrice) {
        this.avgEntryPrice = avgEntryPrice;
    }

    public BigDecimal getEntryQty() {
        return entryQty;
    }

    public void setEntryQty(BigDecimal entryQty) {
        this.entryQty = entryQty;
    }

    public BigDecimal getEntryCost() {
        return entryCost;
    }

    public void setEntryCost(BigDecimal entryCost) {
        this.entryCost = entryCost;
    }

    public BigDecimal getExitQty() {
        return exitQty;
    }

    public void setExitQty(BigDecimal exitQty) {
        this.exitQty = exitQty;
    }

    public BigDecimal getExitProceeds() {
        return exitProceeds;
    }

    public void setExitProceeds(BigDecimal exitProceeds) {
        this.exitProceeds = exitProceeds;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }

    public Integer getFilledSafetyOrders() {
        return filledSafetyOrders;
    }

    public void setFilledSafetyOrders(Integer filledSafetyOrders) {
        this.filledSafetyOrders = filledSafetyOrders;
    }

    public BigDecimal getTakeProfitPrice() {
        return takeProfitPrice;
    }

    public void setTakeProfitPrice(BigDecimal takeProfitPrice) {
        this.takeProfitPrice = takeProfitPrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public void setStopLossPrice(BigDecimal stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public CycleCloseReason getCloseReason() {
        return closeReason == null ? null : CycleCloseReason.fromId(closeReason);
    }

    public void setCloseReason(CycleCloseReason closeReason) {
        this.closeReason = closeReason == null ? null : closeReason.getId();
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }
}
