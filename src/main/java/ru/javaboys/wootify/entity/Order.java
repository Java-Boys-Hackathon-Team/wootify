package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.annotation.NumberFormat;
import io.jmix.core.metamodel.annotation.DateTimeFormat;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.OnDeleteInverse;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "ORDER_", indexes = {
        @Index(name = "IDX_ORDER__ACCOUNT", columnList = "ACCOUNT_ID"),
        @Index(name = "IDX_ORDER__API_KEY_ID", columnList = "API_KEY_ID"),
        @Index(name = "IDX_ORDER__POSITION", columnList = "POSITION_ID"),
        @Index(name = "IDX_ORDER__SYMBOL", columnList = "SYMBOL_ID"),
        @Index(name = "IDX_ORDER__BOT", columnList = "BOT_ID"),
        @Index(name = "IDX_ORDER__CYCLE", columnList = "CYCLE_ID")
}, uniqueConstraints = {
        @UniqueConstraint(name = "IDX_ORDER__UNQ_CLIENT_ORDER_ID", columnNames = {"CLIENT_ORDER_ID"})
})
@Entity(name = "Order_")
public class Order {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CREATED_DATE")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private LocalDateTime createdDate;

    @Column(name = "SENDING_DATE")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private LocalDateTime sendingDate;

    @Column(name = "CLOSED_DATE")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
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
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal quantity;

    @Column(name = "PRICE", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal price;

    @Column(name = "ORDERLY_ORDER_ID")
    private String orderlyOrderId;

    @Column(name = "TOTAL_FEE", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal totalFee;

    @Column(name = "AVERAGE_EXECUTED_PRICE", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal averageExecutedPrice;

    @Column(name = "TOTAL_EXECUTED_QUANTITY", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal totalExecutedQuantity;

    @Column(name = "REALIZED_PNL", precision = 19, scale = 10)
    @NumberFormat(pattern = "#,##0.########")
    private BigDecimal realizedPnl;

    @Column(name = "WOOFI_STATUS")
    private String woofiStatus;

    public String getWoofiStatus() {
        return woofiStatus;
    }

    public void setWoofiStatus(String woofiStatus) {
        this.woofiStatus = woofiStatus;
    }

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


    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "BOT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Bot bot;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "CYCLE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private BotCycle cycle;

    @Column(name = "CLIENT_ORDER_ID", length = 64)
    private String clientOrderId;

    @Column(name = "ROLE_", length = 16)
    private String role;

    @Column(name = "GRID_LEVEL")
    private Integer gridLevel;

    @Column(name = "TRADING_MODE", length = 16)
    private String tradingMode;

    @Column(name = "REDUCE_ONLY")
    private Boolean reduceOnly;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    @Column(name = "UPDATED_DATE")
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private LocalDateTime updatedDate;

    public Bot getBot() {
        return bot;
    }

    public void setBot(Bot bot) {
        this.bot = bot;
    }

    public BotCycle getCycle() {
        return cycle;
    }

    public void setCycle(BotCycle cycle) {
        this.cycle = cycle;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public OrderRole getRole() {
        return role == null ? null : OrderRole.fromId(role);
    }

    public void setRole(OrderRole role) {
        this.role = role == null ? null : role.getId();
    }

    public Integer getGridLevel() {
        return gridLevel;
    }

    public void setGridLevel(Integer gridLevel) {
        this.gridLevel = gridLevel;
    }

    public TradingMode getTradingMode() {
        return tradingMode == null ? null : TradingMode.fromId(tradingMode);
    }

    public void setTradingMode(TradingMode tradingMode) {
        this.tradingMode = tradingMode == null ? null : tradingMode.getId();
    }

    public Boolean getReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(Boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}
