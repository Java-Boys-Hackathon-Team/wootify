package ru.javaboys.wootify.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * Конфигурация торгового бота. Редактируется пользователем, пока бот остановлен.
 * Состояние исполнения хранится отдельно в {@link BotRuntime} и изменяется только движком.
 */
@JmixEntity
@Table(name = "BOT", indexes = {
        @Index(name = "IDX_BOT_SYMBOL", columnList = "SYMBOL_ID"),
        @Index(name = "IDX_BOT_ACCOUNT", columnList = "ACCOUNT_ID"),
        @Index(name = "IDX_BOT_API_KEY", columnList = "API_KEY_ID"),
        @Index(name = "IDX_BOT_DCA_SETTINGS", columnList = "DCA_SETTINGS_ID")
}, uniqueConstraints = {
        @UniqueConstraint(name = "IDX_BOT_UNQ_NAME", columnNames = {"NAME"})
})
@Entity
public class Bot {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @InstanceName
    @NotBlank
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @NotNull
    @Column(name = "STRATEGY_TYPE", nullable = false, length = 32)
    private String strategyType;

    @NotNull
    @Column(name = "TRADING_MODE", nullable = false, length = 16)
    private String tradingMode;

    @NotNull
    @Column(name = "NETWORK", nullable = false, length = 16)
    private String network;

    @NotNull
    @JoinColumn(name = "SYMBOL_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Symbol symbol;

    @JoinColumn(name = "ACCOUNT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @JoinColumn(name = "API_KEY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ApiKey apiKey;

    @NotNull
    @Min(1)
    @Max(3600)
    @Column(name = "TICK_INTERVAL_SEC", nullable = false)
    private Integer tickIntervalSec;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "MAX_RESTARTS", nullable = false)
    private Integer maxRestarts;

    @NotNull
    @Column(name = "STOP_ACTION", nullable = false, length = 32)
    private String stopAction;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @JoinColumn(name = "DCA_SETTINGS_ID")
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private DcaSettings dcaSettings;

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "bot")
    private BotRuntime runtime;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    private OffsetDateTime lastModifiedDate;


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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StrategyType getStrategyType() {
        return strategyType == null ? null : StrategyType.fromId(strategyType);
    }

    public void setStrategyType(StrategyType strategyType) {
        this.strategyType = strategyType == null ? null : strategyType.getId();
    }

    public TradingMode getTradingMode() {
        return tradingMode == null ? null : TradingMode.fromId(tradingMode);
    }

    public void setTradingMode(TradingMode tradingMode) {
        this.tradingMode = tradingMode == null ? null : tradingMode.getId();
    }

    public Network getNetwork() {
        return network == null ? null : Network.fromId(network);
    }

    public void setNetwork(Network network) {
        this.network = network == null ? null : network.getId();
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getTickIntervalSec() {
        return tickIntervalSec;
    }

    public void setTickIntervalSec(Integer tickIntervalSec) {
        this.tickIntervalSec = tickIntervalSec;
    }

    public Integer getMaxRestarts() {
        return maxRestarts;
    }

    public void setMaxRestarts(Integer maxRestarts) {
        this.maxRestarts = maxRestarts;
    }

    public StopAction getStopAction() {
        return stopAction == null ? null : StopAction.fromId(stopAction);
    }

    public void setStopAction(StopAction stopAction) {
        this.stopAction = stopAction == null ? null : stopAction.getId();
    }

    public DcaSettings getDcaSettings() {
        return dcaSettings;
    }

    public void setDcaSettings(DcaSettings dcaSettings) {
        this.dcaSettings = dcaSettings;
    }

    public BotRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(BotRuntime runtime) {
        this.runtime = runtime;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
