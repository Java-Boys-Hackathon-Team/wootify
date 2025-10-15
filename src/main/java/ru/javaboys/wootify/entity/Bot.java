package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "BOT", indexes = {
        @Index(name = "IDX_BOT_SYMBOL", columnList = "SYMBOL_ID")
})
@Entity
public class Bot {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "SYMBOL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Symbol symbol;

    @InstanceName
    @Column(name = "NAME")
    private String name;

    @Column(name = "START_DATE_TIME")
    private LocalDateTime startDateTime;

    @Column(name = "END_DATE_TIME")
    private LocalDateTime endDateTime;

    @Column(name = "ERROR_MESSAGE")
    @Lob
    private String errorMessage;

    @Column(name = "DESIRED_STATE")
    private String desiredState;

    @Column(name = "RUNTIME_STATE")
    private String runtimeState;

    @Column(name = "LAST_HEART_BEAT")
    private Integer lastHeartBeat;

    @Column(name = "OWNER_INSTANCE")
    private UUID ownerInstance;

    public UUID getOwnerInstance() {
        return ownerInstance;
    }

    public void setOwnerInstance(UUID ownerInstance) {
        this.ownerInstance = ownerInstance;
    }

    public Integer getLastHeartBeat() {
        return lastHeartBeat;
    }

    public void setLastHeartBeat(Integer lastHeartBeat) {
        this.lastHeartBeat = lastHeartBeat;
    }

    public RuntimeState getRuntimeState() {
        return runtimeState == null ? null : RuntimeState.fromId(runtimeState);
    }

    public void setRuntimeState(RuntimeState runtimeState) {
        this.runtimeState = runtimeState == null ? null : runtimeState.getId();
    }

    public DesiredState getDesiredState() {
        return desiredState == null ? null : DesiredState.fromId(desiredState);
    }

    public void setDesiredState(DesiredState desiredState) {
        this.desiredState = desiredState == null ? null : desiredState.getId();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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