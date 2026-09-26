package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.annotation.NumberFormat;
import io.jmix.core.metamodel.annotation.DateTimeFormat;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "POSITION_", indexes = {
        @Index(name = "IDX_POSITION__SYMBOL", columnList = "SYMBOL_ID"),
        @Index(name = "IDX_POSITION__ACCOUNT", columnList = "ACCOUNT_ID"),
        @Index(name = "IDX_POSITION__API_KEY", columnList = "API_KEY_ID")
})
@Entity(name = "Position_")
public class Position {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "STATUS")
    private String status;

    @JoinColumn(name = "API_KEY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ApiKey apiKey;

    @JoinColumn(name = "ACCOUNT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @Column(name = "CREATED_DATE")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private LocalDateTime createdDate;

    @JoinColumn(name = "SYMBOL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Symbol symbol;

    @Column(name = "CLOSED_DATE")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private LocalDateTime closedDate;

    @Column(name = "LEVERAGE")
    private Double leverage;

    @Column(name = "POSITION_QTY", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal positionQty;

    @Column(name = "AVERAGE_OPEN_PRICE", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal averageOpenPrice;

    @Column(name = "REALIZED_PNL", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal realizedPnl;


    public void setLeverage(Double leverage) {
        this.leverage = leverage;
    }

    public Double getLeverage() {
        return leverage;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public BigDecimal getAverageOpenPrice() {
        return averageOpenPrice;
    }

    public void setAverageOpenPrice(BigDecimal averageOpenPrice) {
        this.averageOpenPrice = averageOpenPrice;
    }

    public BigDecimal getPositionQty() {
        return positionQty;
    }

    public void setPositionQty(BigDecimal positionQty) {
        this.positionQty = positionQty;
    }

    public LocalDateTime getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDateTime closedDate) {
        this.closedDate = closedDate;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public PositionStatus getStatus() {
        return status == null ? null : PositionStatus.fromId(status);
    }

    public void setStatus(PositionStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
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