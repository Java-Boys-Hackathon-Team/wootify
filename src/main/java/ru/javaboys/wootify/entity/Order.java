package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "ORDER_", indexes = {
        @Index(name = "IDX_ORDER__ACCOUNT", columnList = "ACCOUNT_ID"),
        @Index(name = "IDX_ORDER__API_KEY_ID", columnList = "API_KEY_ID"),
        @Index(name = "IDX_ORDER__POSITION", columnList = "POSITION_ID"),
        @Index(name = "IDX_ORDER__SYMBOL", columnList = "SYMBOL_ID")
})
@Entity(name = "Order_")
public class Order {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Column(name = "SENDING_DATE")
    private LocalDateTime sendingDate;

    @Column(name = "CLOSED_DATE")
    private LocalDateTime closedDate;

    @JoinColumn(name = "ACCOUNT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @JoinColumn(name = "API_KEY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ApiKey apiKey;

    @JoinColumn(name = "POSITION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Position position;

    @JoinColumn(name = "SYMBOL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Symbol symbol;

    @Column(name = "TYPE_")
    private String type;

    @Column(name = "SIDE")
    private String side;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "QUANTITY", precision = 19, scale = 10)
    private BigDecimal quantity;

    @Column(name = "PRICE", precision = 19, scale = 10)
    private BigDecimal price;

    @Column(name = "ORDERLY_ORDER_ID")
    private String orderlyOrderId;

    @Column(name = "TOTAL_FEE", precision = 19, scale = 10)
    private BigDecimal totalFee;

    @Column(name = "AVERAGE_EXECUTED_PRICE", precision = 19, scale = 10)
    private BigDecimal averageExecutedPrice;

    @Column(name = "TOTAL_EXECUTED_QUANTITY", precision = 19, scale = 10)
    private BigDecimal totalExecutedQuantity;

    @Column(name = "REALIZED_PNL", precision = 19, scale = 10)
    private BigDecimal realizedPnl;

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public BigDecimal getAverageExecutedPrice() {
        return averageExecutedPrice;
    }

    public void setAverageExecutedPrice(BigDecimal averageExecutedPrice) {
        this.averageExecutedPrice = averageExecutedPrice;
    }

    public BigDecimal getTotalExecutedQuantity() {
        return totalExecutedQuantity;
    }

    public void setTotalExecutedQuantity(BigDecimal totalExecutedQuantity) {
        this.totalExecutedQuantity = totalExecutedQuantity;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(BigDecimal total_fee) {
        this.totalFee = total_fee;
    }

    public String getOrderlyOrderId() {
        return orderlyOrderId;
    }

    public void setOrderlyOrderId(String orderly_order_id) {
        this.orderlyOrderId = orderly_order_id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public OrderStatus getStatus() {
        return status == null ? null : OrderStatus.fromId(status);
    }

    public void setStatus(OrderStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public OrderSide getSide() {
        return side == null ? null : OrderSide.fromId(side);
    }

    public void setSide(OrderSide side) {
        this.side = side == null ? null : side.getId();
    }

    public OrderType getType() {
        return type == null ? null : OrderType.fromId(type);
    }

    public void setType(OrderType type) {
        this.type = type == null ? null : type.getId();
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKeyId) {
        this.apiKey = apiKeyId;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public LocalDateTime getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDateTime closedDate) {
        this.closedDate = closedDate;
    }

    public LocalDateTime getSendingDate() {
        return sendingDate;
    }

    public void setSendingDate(LocalDateTime sendingDate) {
        this.sendingDate = sendingDate;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}