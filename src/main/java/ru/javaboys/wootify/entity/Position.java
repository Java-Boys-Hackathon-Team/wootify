package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "POSITION_", indexes = {
        @Index(name = "IDX_POSITION__SYMBOL", columnList = "SYMBOL_ID"),
        @Index(name = "IDX_POSITION__ACCOUNT", columnList = "ACCOUNT_ID")
})
@Entity(name = "Position_")
public class Position {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "ACCOUNT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @Column(name = "TIMESTAMP_")
    private LocalDateTime timestamp;

    @JoinColumn(name = "SYMBOL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Symbol symbol;

    @Column(name = "POSITION_QTY", precision = 19, scale = 10)
    private BigDecimal positionQty;

    @Column(name = "SETTLE_PRICE", precision = 19, scale = 10)
    private BigDecimal settlePrice;

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public BigDecimal getSettlePrice() {
        return settlePrice;
    }

    public void setSettlePrice(BigDecimal settlePrice) {
        this.settlePrice = settlePrice;
    }

    public BigDecimal getPositionQty() {
        return positionQty;
    }

    public void setPositionQty(BigDecimal positionQty) {
        this.positionQty = positionQty;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}