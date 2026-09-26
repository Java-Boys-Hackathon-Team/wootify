package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.annotation.DateTimeFormat;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Запись журнала бота: жизненный цикл, ордера, предупреждения и ошибки.
 * Одинаковые сообщения подряд схлопываются: растёт {@code repeatCount}.
 */
@JmixEntity
@Table(name = "BOT_EVENT", indexes = {
        @Index(name = "IDX_BOT_EVENT_BOT_CREATED", columnList = "BOT_ID, CREATED_AT")
})
@Entity
public class BotEvent {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "BOT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Bot bot;

    @Column(name = "CREATED_AT", nullable = false)
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private OffsetDateTime createdAt;

    @Column(name = "LAST_OCCURRED_AT", nullable = false)
    @DateTimeFormat("dd.MM.yyyy HH:mm:ss")
    private OffsetDateTime lastOccurredAt;

    @Column(name = "REPEAT_COUNT", nullable = false)
    private Integer repeatCount;

    @Column(name = "LEVEL_", nullable = false, length = 8)
    private String level;

    @Column(name = "CATEGORY", nullable = false, length = 16)
    private String category;

    @InstanceName
    @Column(name = "MESSAGE", nullable = false, length = 1000)
    private String message;

    @Lob
    @Column(name = "DETAILS")
    private String details;

    @Column(name = "INSTANCE_ID", length = 100)
    private String instanceId;


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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getLastOccurredAt() {
        return lastOccurredAt;
    }

    public void setLastOccurredAt(OffsetDateTime lastOccurredAt) {
        this.lastOccurredAt = lastOccurredAt;
    }

    public Integer getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(Integer repeatCount) {
        this.repeatCount = repeatCount;
    }

    public EventLevel getLevel() {
        return level == null ? null : EventLevel.fromId(level);
    }

    public void setLevel(EventLevel level) {
        this.level = level == null ? null : level.getId();
    }

    public EventCategory getCategory() {
        return category == null ? null : EventCategory.fromId(category);
    }

    public void setCategory(EventCategory category) {
        this.category = category == null ? null : category.getId();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
