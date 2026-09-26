package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ордер на стороне симулятора биржи (бумажная торговля).
 * Играет роль «биржевой» записи: бот видит его только через {@code ExchangeGateway}.
 */
@JmixEntity
@Table(name = "PAPER_ORDER", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_PAPER_ORDER_UNQ_CLIENT_ID", columnNames = {"CLIENT_ORDER_ID"})
})
@Entity
public class PaperOrder {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CLIENT_ORDER_ID", nullable = false, length = 64)
    private String clientOrderId;

    @Column(name = "NETWORK", nullable = false, length = 16)
    private String network;

    @Column(name = "SYMBOL", nullable = false, length = 64)
    private String symbol;

    @Column(name = "SIDE", nullable = false, length = 8)
    private String side;

    @Column(name = "TYPE_", nullable = false, length = 16)
    private String type;

    @Column(name = "PRICE", precision = 28, scale = 12)
    private BigDecimal price;

    @Column(name = "QUANTITY", nullable = false, precision = 28, scale = 12)
    private BigDecimal quantity;

    @Column(name = "REDUCE_ONLY", nullable = false)
    private Boolean reduceOnly;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "FILLED_QTY", nullable = false, precision = 28, scale = 12)
    private BigDecimal filledQty;

    @Column(name = "AVG_PRICE", precision = 28, scale = 12)
    private BigDecimal avgPrice;

    @Column(name = "FEE", nullable = false, precision = 28, scale = 12)
    private BigDecimal fee;

    @Column(name = "REJECT_REASON", length = 500)
    private String rejectReason;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Network getNetwork() {
        return network == null ? null : Network.fromId(network);
    }

    public void setNetwork(Network network) {
        this.network = network == null ? null : network.getId();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public Boolean getReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(Boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public PaperOrderStatus getStatus() {
        return status == null ? null : PaperOrderStatus.fromId(status);
    }

    public void setStatus(PaperOrderStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public BigDecimal getFilledQty() {
        return filledQty;
    }

    public void setFilledQty(BigDecimal filledQty) {
        this.filledQty = filledQty;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
