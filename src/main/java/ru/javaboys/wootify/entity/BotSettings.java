package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@JmixEntity
@Table(name = "BOT_SETTINGS", indexes = {
        @Index(name = "IDX_BOT_SETTINGS_SYMBOL", columnList = "SYMBOL_ID"),
        @Index(name = "IDX_BOT_SETTINGS_POSITION", columnList = "POSITION_ID"),
        @Index(name = "IDX_BOT_SETTINGS_BOT", columnList = "BOT_ID")
})
@Entity
public class BotSettings {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "SYMBOL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Symbol symbol;

    @Column(name = "TRADE_TYPE")
    private String tradeType;

    @Column(name = "DEPOSIT", precision = 19, scale = 10)
    private BigDecimal deposit;

    @Column(name = "LEVERAGE")
    private Double leverage;

    @Column(name = "OVERLAP_PERSENT")
    private Integer overlapPersent;

    @Column(name = "ORDERS_QUANTITY")
    private Integer ordersQuantity;

    @Column(name = "PROFIT_PERSENT")
    private Double profitPersent;

    @JoinColumn(name = "POSITION_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private Position position;

    @Column(name = "STATE")
    private String state;

    @JoinColumn(name = "BOT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Bot bot;

    public Bot getBot() {
        return bot;
    }

    public void setBot(Bot bot) {
        this.bot = bot;
    }

    public BotSettingsState getState() {
        return state == null ? null : BotSettingsState.fromId(state);
    }

    public void setState(BotSettingsState state) {
        this.state = state == null ? null : state.getId();
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Double getProfitPersent() {
        return profitPersent;
    }

    public void setProfitPersent(Double profitPersent) {
        this.profitPersent = profitPersent;
    }

    public Integer getOrdersQuantity() {
        return ordersQuantity;
    }

    public void setOrdersQuantity(Integer ordersQuantity) {
        this.ordersQuantity = ordersQuantity;
    }

    public Integer getOverlapPersent() {
        return overlapPersent;
    }

    public void setOverlapPersent(Integer overlapPersent) {
        this.overlapPersent = overlapPersent;
    }

    public Double getLeverage() {
        return leverage;
    }

    public void setLeverage(Double leverage) {
        this.leverage = leverage;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public TradeType getTradeType() {
        return tradeType == null ? null : TradeType.fromId(tradeType);
    }

    public void setTradeType(TradeType tradeType) {
        this.tradeType = tradeType == null ? null : tradeType.getId();
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}