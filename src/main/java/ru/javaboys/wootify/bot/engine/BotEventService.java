package ru.javaboys.wootify.bot.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.entity.EventLevel;

import java.time.Duration;
import java.util.UUID;

/**
 * Журнал событий бота. Пишет в BOT_EVENT и в журнал приложения.
 * <p>
 * Если бот раз за разом сообщает одно и то же (например, биржа недоступна), новая запись не
 * создаётся: у последней увеличивается счётчик повторов и время последнего появления.
 */
public class BotEventService {

    private static final Logger log = LoggerFactory.getLogger("ru.javaboys.wootify.bot.events");
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);
    private static final int MESSAGE_MAX = 1000;

    private final JdbcTemplate jdbc;
    private final String instanceId;
    private final Duration retention;

    public BotEventService(JdbcTemplate jdbc, String instanceId, Duration retention) {
        this.jdbc = jdbc;
        this.instanceId = instanceId;
        this.retention = retention;
    }

    public void info(UUID botId, EventCategory category, String message) {
        record(botId, EventLevel.INFO, category, message, null);
    }

    public void warn(UUID botId, EventCategory category, String message) {
        record(botId, EventLevel.WARN, category, message, null);
    }

    public void error(UUID botId, EventCategory category, String message, Throwable error) {
        record(botId, EventLevel.ERROR, category, message, error == null ? null : Failures.stackTrace(error));
    }

    public void record(UUID botId, EventLevel level, EventCategory category, String message, String details) {
        String text = Failures.truncate(message, MESSAGE_MAX);
        switch (level) {
            case ERROR -> log.error("[bot {}] {}", botId, text);
            case WARN -> log.warn("[bot {}] {}", botId, text);
            default -> log.info("[bot {}] {}", botId, text);
        }
        try {
            int merged = jdbc.update("""
                            UPDATE BOT_EVENT SET REPEAT_COUNT = REPEAT_COUNT + 1, LAST_OCCURRED_AT = CURRENT_TIMESTAMP,
                                                 DETAILS = COALESCE(?, DETAILS)
                            WHERE ID = (SELECT ID FROM BOT_EVENT WHERE BOT_ID = ? ORDER BY CREATED_AT DESC, ID DESC LIMIT 1)
                              AND LEVEL_ = ? AND CATEGORY = ? AND MESSAGE = ?
                              AND LAST_OCCURRED_AT > CURRENT_TIMESTAMP - (? * INTERVAL '1 millisecond')""",
                    details, botId, level.getId(), category.getId(), text, DEDUP_WINDOW.toMillis());
            if (merged == 0) {
                jdbc.update("""
                                INSERT INTO BOT_EVENT (ID, BOT_ID, CREATED_AT, LAST_OCCURRED_AT, REPEAT_COUNT, LEVEL_, CATEGORY,
                                                       MESSAGE, DETAILS, INSTANCE_ID)
                                VALUES (?, ?, CLOCK_TIMESTAMP(), CLOCK_TIMESTAMP(), 1, ?, ?, ?, ?, ?)""",
                        UUID.randomUUID(), botId, level.getId(), category.getId(), text, details, instanceId);
            }
        } catch (RuntimeException e) {
            // Журнал не должен ронять бота: например, если бот удалён или БД недоступна.
            log.warn("Cannot store bot event for {}: {}", botId, e.toString());
        }
    }

    @Scheduled(cron = "${wootify.bots.event-cleanup-cron:0 17 3 * * *}")
    public void deleteOldEvents() {
        int deleted = jdbc.update("DELETE FROM BOT_EVENT WHERE LAST_OCCURRED_AT < CURRENT_TIMESTAMP - (? * INTERVAL '1 millisecond')",
                retention.toMillis());
        if (deleted > 0) {
            log.info("Deleted {} bot events older than {}", deleted, retention);
        }
    }
}
