package ru.javaboys.wootify.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Состояние исполнения бота. Изменяется только движком ботов атомарными SQL-операциями
 * (см. {@code BotRuntimeRepository}); в UI доступно только для чтения.
 */
@JmixEntity
@Table(name = "BOT_RUNTIME", indexes = {
        @Index(name = "IDX_BOT_RUNTIME_OWNER", columnList = "OWNER_INSTANCE")
})
@Entity
public class BotRuntime {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "BOT_ID", nullable = false, unique = true)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Bot bot;

    @Column(name = "DESIRED_STATE", nullable = false, length = 16)
    private String desiredState;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "REQUESTED_STOP_ACTION", length = 32)
    private String requestedStopAction;

    @Column(name = "OWNER_INSTANCE", length = 100)
    private String ownerInstance;

    @Column(name = "LEASE_UNTIL")
    private OffsetDateTime leaseUntil;

    @Column(name = "LAST_HEARTBEAT")
    private OffsetDateTime lastHeartbeat;

    @Column(name = "STARTED_AT")
    private OffsetDateTime startedAt;

    @Column(name = "STOPPED_AT")
    private OffsetDateTime stoppedAt;

    @Column(name = "RESTART_COUNT", nullable = false)
    private Integer restartCount;

    @Column(name = "NEXT_RESTART_AT")
    private OffsetDateTime nextRestartAt;

    @Column(name = "LAST_ERROR", length = 4000)
    private String lastError;

    @Column(name = "LAST_ERROR_AT")
    private OffsetDateTime lastErrorAt;

    @Column(name = "STATUS_MESSAGE", length = 1000)
    private String statusMessage;

    @Column(name = "TICK_COUNT", nullable = false)
    private Long tickCount;

    @Lob
    @Column(name = "STRATEGY_STATE")
    private String strategyState;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;


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

    public DesiredState getDesiredState() {
        return desiredState == null ? null : DesiredState.fromId(desiredState);
    }

    public void setDesiredState(DesiredState desiredState) {
        this.desiredState = desiredState == null ? null : desiredState.getId();
    }

    public BotStatus getStatus() {
        return status == null ? null : BotStatus.fromId(status);
    }

    public void setStatus(BotStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public StopAction getRequestedStopAction() {
        return requestedStopAction == null ? null : StopAction.fromId(requestedStopAction);
    }

    public void setRequestedStopAction(StopAction requestedStopAction) {
        this.requestedStopAction = requestedStopAction == null ? null : requestedStopAction.getId();
    }

    public String getOwnerInstance() {
        return ownerInstance;
    }

    public void setOwnerInstance(String ownerInstance) {
        this.ownerInstance = ownerInstance;
    }

    public OffsetDateTime getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(OffsetDateTime leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public OffsetDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(OffsetDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(OffsetDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public Integer getRestartCount() {
        return restartCount;
    }

    public void setRestartCount(Integer restartCount) {
        this.restartCount = restartCount;
    }

    public OffsetDateTime getNextRestartAt() {
        return nextRestartAt;
    }

    public void setNextRestartAt(OffsetDateTime nextRestartAt) {
        this.nextRestartAt = nextRestartAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(OffsetDateTime lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Long getTickCount() {
        return tickCount;
    }

    public void setTickCount(Long tickCount) {
        this.tickCount = tickCount;
    }

    public String getStrategyState() {
        return strategyState;
    }

    public void setStrategyState(String strategyState) {
        this.strategyState = strategyState;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
