package ru.javaboys.wootify.bot.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.DesiredState;
import ru.javaboys.wootify.entity.StopAction;

import java.sql.Array;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Атомарные операции над BOT_RUNTIME.
 * <p>
 * Каждый переход состояния - один условный UPDATE. Записи движка содержат проверку владельца
 * ({@code OWNER_INSTANCE = ?}): если аренду перехватил другой экземпляр, запись не пройдёт, а раннер
 * узнает о потере владения. Время аренды отсчитывается по часам БД, чтобы экземпляры с разными
 * системными часами не спорили о её истечении.
 */
public class BotRuntimeRepository {

    /** Условие «ботом никто не владеет или владелец не продлил аренду». */
    private static final String FREE = "(OWNER_INSTANCE IS NULL OR OWNER_INSTANCE = ? OR LEASE_UNTIL < CURRENT_TIMESTAMP)";

    private final JdbcTemplate jdbc;

    public BotRuntimeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Снимок управляющих полей. */
    public record Control(UUID botId, DesiredState desired, BotStatus status, StopAction requestedStopAction,
                          String owner, boolean leaseExpired, int restartCount) {
    }

    /** Режим, в котором бот захватывается. */
    public enum ClaimMode {
        /** Обычная работа или продолжение после перезапуска. */
        RUN,
        /** Выполнить действие остановки для бота, у которого нет работающего потока. */
        FINALIZE_STOP
    }

    public record Claimable(UUID botId, ClaimMode mode) {
    }

    // ------------------------------------------------------------------ управление (UI)

    public void ensureExists(UUID botId) {
        jdbc.update("""
                INSERT INTO BOT_RUNTIME (ID, BOT_ID, DESIRED_STATE, STATUS, RESTART_COUNT, TICK_COUNT, UPDATED_AT)
                VALUES (?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (BOT_ID) DO NOTHING""",
                UUID.randomUUID(), botId, DesiredState.STOPPED.getId(), BotStatus.STOPPED.getId());
    }

    /**
     * Запрос запуска. Сбрасывает счётчик перезапусков; бот в состоянии FAILED снова становится доступен.
     */
    public void requestStart(UUID botId) {
        jdbc.update("""
                UPDATE BOT_RUNTIME
                SET DESIRED_STATE = ?, REQUESTED_STOP_ACTION = NULL, RESTART_COUNT = 0, NEXT_RESTART_AT = NULL,
                    STATUS = CASE WHEN STATUS = ? THEN ? ELSE STATUS END,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE BOT_ID = ?""",
                DesiredState.RUNNING.getId(), BotStatus.FAILED.getId(), BotStatus.STOPPED.getId(), botId);
    }

    public void requestStop(UUID botId, StopAction action) {
        jdbc.update("""
                UPDATE BOT_RUNTIME
                SET DESIRED_STATE = ?, REQUESTED_STOP_ACTION = ?, NEXT_RESTART_AT = NULL, UPDATED_AT = CURRENT_TIMESTAMP
                WHERE BOT_ID = ?""",
                DesiredState.STOPPED.getId(), action.getId(), botId);
    }

    public Optional<Control> control(UUID botId) {
        return jdbc.query("""
                        SELECT BOT_ID, DESIRED_STATE, STATUS, REQUESTED_STOP_ACTION, OWNER_INSTANCE, RESTART_COUNT,
                               (LEASE_UNTIL IS NULL OR LEASE_UNTIL < CURRENT_TIMESTAMP) AS EXPIRED
                        FROM BOT_RUNTIME WHERE BOT_ID = ?""",
                (rs, n) -> new Control(rs.getObject("BOT_ID", UUID.class),
                        DesiredState.fromId(rs.getString("DESIRED_STATE")),
                        BotStatus.fromId(rs.getString("STATUS")),
                        StopAction.fromId(rs.getString("REQUESTED_STOP_ACTION")),
                        rs.getString("OWNER_INSTANCE"),
                        rs.getBoolean("EXPIRED"),
                        rs.getInt("RESTART_COUNT")),
                botId).stream().findFirst();
    }

    public List<Control> controls(Collection<UUID> botIds) {
        if (botIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(con -> {
                    var ps = con.prepareStatement("""
                            SELECT BOT_ID, DESIRED_STATE, STATUS, REQUESTED_STOP_ACTION, OWNER_INSTANCE, RESTART_COUNT,
                                   (LEASE_UNTIL IS NULL OR LEASE_UNTIL < CURRENT_TIMESTAMP) AS EXPIRED
                            FROM BOT_RUNTIME WHERE BOT_ID = ANY (?)""");
                    ps.setArray(1, con.createArrayOf("uuid", botIds.toArray()));
                    return ps;
                },
                (rs, n) -> new Control(rs.getObject("BOT_ID", UUID.class),
                        DesiredState.fromId(rs.getString("DESIRED_STATE")),
                        BotStatus.fromId(rs.getString("STATUS")),
                        StopAction.fromId(rs.getString("REQUESTED_STOP_ACTION")),
                        rs.getString("OWNER_INSTANCE"),
                        rs.getBoolean("EXPIRED"),
                        rs.getInt("RESTART_COUNT")));
    }

    // ------------------------------------------------------------------ супервизор

    /**
     * Боты, которые этот экземпляр может взять в работу.
     */
    public List<Claimable> findClaimable(String instance, int limit) {
        List<Claimable> result = new java.util.ArrayList<>();
        jdbc.query("SELECT BOT_ID FROM BOT_RUNTIME WHERE DESIRED_STATE = ? AND STATUS <> ? "
                   + "AND (NEXT_RESTART_AT IS NULL OR NEXT_RESTART_AT <= CURRENT_TIMESTAMP) AND " + FREE
                   + " ORDER BY UPDATED_AT LIMIT ?",
                rs -> {
                    result.add(new Claimable(rs.getObject(1, UUID.class), ClaimMode.RUN));
                },
                DesiredState.RUNNING.getId(), BotStatus.FAILED.getId(), instance, limit);
        jdbc.query("SELECT BOT_ID FROM BOT_RUNTIME WHERE DESIRED_STATE = ? AND REQUESTED_STOP_ACTION IS NOT NULL "
                   + "AND REQUESTED_STOP_ACTION <> ? AND STATUS <> ? AND " + FREE + " ORDER BY UPDATED_AT LIMIT ?",
                rs -> {
                    result.add(new Claimable(rs.getObject(1, UUID.class), ClaimMode.FINALIZE_STOP));
                },
                DesiredState.STOPPED.getId(), StopAction.KEEP_ORDERS.getId(), BotStatus.STOPPED.getId(), instance, limit);
        return result;
    }

    /**
     * Захват бота. Условие повторяет {@link #findClaimable}, поэтому из нескольких конкурентов
     * выигрывает ровно один.
     */
    public boolean tryClaim(UUID botId, ClaimMode mode, String instance, Duration lease) {
        if (mode == ClaimMode.RUN) {
            return jdbc.update("UPDATE BOT_RUNTIME SET OWNER_INSTANCE = ?, "
                               + "LEASE_UNTIL = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'), STATUS = ?, "
                               + "NEXT_RESTART_AT = NULL, UPDATED_AT = CURRENT_TIMESTAMP "
                               + "WHERE BOT_ID = ? AND DESIRED_STATE = ? AND STATUS <> ? "
                               + "AND (NEXT_RESTART_AT IS NULL OR NEXT_RESTART_AT <= CURRENT_TIMESTAMP) AND " + FREE,
                    instance, lease.toMillis(), BotStatus.STARTING.getId(), botId, DesiredState.RUNNING.getId(),
                    BotStatus.FAILED.getId(), instance) == 1;
        }
        return jdbc.update("UPDATE BOT_RUNTIME SET OWNER_INSTANCE = ?, "
                           + "LEASE_UNTIL = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'), STATUS = ?, "
                           + "UPDATED_AT = CURRENT_TIMESTAMP "
                           + "WHERE BOT_ID = ? AND DESIRED_STATE = ? AND REQUESTED_STOP_ACTION IS NOT NULL "
                           + "AND REQUESTED_STOP_ACTION <> ? AND STATUS <> ? AND " + FREE,
                instance, lease.toMillis(), BotStatus.STOPPING.getId(), botId, DesiredState.STOPPED.getId(),
                StopAction.KEEP_ORDERS.getId(), BotStatus.STOPPED.getId(), instance) == 1;
    }

    /**
     * Боты, которых пользователь остановил без действия над ордерами и у которых нет работающего
     * потока (ожидали перезапуска, упали, были приостановлены), просто помечаются остановленными.
     * Запрос пользователя отличается от сброса движком по непустому REQUESTED_STOP_ACTION: движок,
     * фиксируя ошибку, его очищает, и такая ошибка остаётся видна.
     */
    public int settleStoppedWithoutRunner(String instance, Collection<UUID> runningHere) {
        return jdbc.update(con -> {
            var ps = con.prepareStatement("UPDATE BOT_RUNTIME SET STATUS = ?, OWNER_INSTANCE = NULL, LEASE_UNTIL = NULL, "
                                          + "REQUESTED_STOP_ACTION = NULL, STOPPED_AT = CURRENT_TIMESTAMP, STATUS_MESSAGE = NULL, "
                                          + "UPDATED_AT = CURRENT_TIMESTAMP "
                                          + "WHERE DESIRED_STATE = ? AND STATUS <> ? AND REQUESTED_STOP_ACTION = ? AND " + FREE
                                          + " AND NOT (BOT_ID = ANY (?))");
            ps.setString(1, BotStatus.STOPPED.getId());
            ps.setString(2, DesiredState.STOPPED.getId());
            ps.setString(3, BotStatus.STOPPED.getId());
            ps.setString(4, StopAction.KEEP_ORDERS.getId());
            ps.setString(5, instance);
            ps.setArray(6, con.createArrayOf("uuid", runningHere.toArray()));
            return ps;
        });
    }

    /**
     * Продлевает аренду ботов этого экземпляра.
     *
     * @return боты, владение которыми подтверждено
     */
    public Set<UUID> renewLeases(String instance, Collection<UUID> botIds, Duration lease) {
        Set<UUID> renewed = new HashSet<>();
        if (botIds.isEmpty()) {
            return renewed;
        }
        jdbc.query(con -> {
            var ps = con.prepareStatement("UPDATE BOT_RUNTIME SET LEASE_UNTIL = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond') "
                                          + "WHERE OWNER_INSTANCE = ? AND BOT_ID = ANY (?) RETURNING BOT_ID");
            ps.setLong(1, lease.toMillis());
            ps.setString(2, instance);
            Array array = con.createArrayOf("uuid", botIds.toArray());
            ps.setArray(3, array);
            return ps;
        }, rs -> {
            renewed.add(rs.getObject(1, UUID.class));
        });
        return renewed;
    }

    // ------------------------------------------------------------------ раннер

    public boolean markRunning(UUID botId, String instance) {
        return jdbc.update("UPDATE BOT_RUNTIME SET STATUS = ?, STARTED_AT = CURRENT_TIMESTAMP, STOPPED_AT = NULL, "
                           + "LAST_HEARTBEAT = CURRENT_TIMESTAMP, STATUS_MESSAGE = NULL, UPDATED_AT = CURRENT_TIMESTAMP "
                           + "WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                BotStatus.RUNNING.getId(), botId, instance) == 1;
    }

    public boolean markStopping(UUID botId, String instance) {
        return jdbc.update("UPDATE BOT_RUNTIME SET STATUS = ?, UPDATED_AT = CURRENT_TIMESTAMP "
                           + "WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                BotStatus.STOPPING.getId(), botId, instance) == 1;
    }

    /**
     * Пульс: бот жив и выполнил такт.
     *
     * @return {@code false}, если владение потеряно
     */
    public boolean heartbeat(UUID botId, String instance, String statusMessage) {
        return jdbc.update("UPDATE BOT_RUNTIME SET LAST_HEARTBEAT = CURRENT_TIMESTAMP, TICK_COUNT = TICK_COUNT + 1, "
                           + "STATUS_MESSAGE = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                statusMessage, botId, instance) == 1;
    }

    public void recordError(UUID botId, String instance, String error, String statusMessage) {
        jdbc.update("UPDATE BOT_RUNTIME SET LAST_ERROR = ?, LAST_ERROR_AT = CURRENT_TIMESTAMP, STATUS_MESSAGE = ?, "
                    + "LAST_HEARTBEAT = CURRENT_TIMESTAMP, UPDATED_AT = CURRENT_TIMESTAMP "
                    + "WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                error, statusMessage, botId, instance);
    }

    /** Временные сбои прошли: ошибка больше не актуальна (история остаётся в журнале). */
    public void clearError(UUID botId, String instance) {
        jdbc.update("UPDATE BOT_RUNTIME SET LAST_ERROR = NULL, LAST_ERROR_AT = NULL WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                botId, instance);
    }

    public void resetRestartCount(UUID botId, String instance) {
        jdbc.update("UPDATE BOT_RUNTIME SET RESTART_COUNT = 0 WHERE BOT_ID = ? AND OWNER_INSTANCE = ?", botId, instance);
    }

    /** Бот остановлен пользователем или завершил работу сам. */
    public void markStopped(UUID botId, String instance, String statusMessage) {
        jdbc.update("UPDATE BOT_RUNTIME SET STATUS = ?, OWNER_INSTANCE = NULL, LEASE_UNTIL = NULL, "
                    + "REQUESTED_STOP_ACTION = NULL, STOPPED_AT = CURRENT_TIMESTAMP, STATUS_MESSAGE = ?, "
                    + "UPDATED_AT = CURRENT_TIMESTAMP WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                BotStatus.STOPPED.getId(), statusMessage, botId, instance);
    }

    /** Стратегия завершила работу сама (например, выполнила заданное число циклов). */
    public void markCompleted(UUID botId, String instance, String statusMessage) {
        jdbc.update("UPDATE BOT_RUNTIME SET DESIRED_STATE = ?, STATUS = ?, OWNER_INSTANCE = NULL, LEASE_UNTIL = NULL, "
                    + "REQUESTED_STOP_ACTION = NULL, STOPPED_AT = CURRENT_TIMESTAMP, STATUS_MESSAGE = ?, "
                    + "UPDATED_AT = CURRENT_TIMESTAMP WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                DesiredState.STOPPED.getId(), BotStatus.STOPPED.getId(), statusMessage, botId, instance);
    }

    /** Штатное выключение приложения: бот продолжит работу при следующем старте. */
    public void markSuspended(UUID botId, String instance) {
        jdbc.update("UPDATE BOT_RUNTIME SET STATUS = ?, OWNER_INSTANCE = NULL, LEASE_UNTIL = NULL, "
                    + "STATUS_MESSAGE = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                BotStatus.SUSPENDED.getId(), "Приостановлен: сервер остановлен", botId, instance);
    }

    /** Ошибка, которую не исправит перезапуск. */
    public void markFailed(UUID botId, String instance, String error) {
        jdbc.update("UPDATE BOT_RUNTIME SET STATUS = ?, OWNER_INSTANCE = NULL, LEASE_UNTIL = NULL, "
                    + "REQUESTED_STOP_ACTION = NULL, LAST_ERROR = ?, LAST_ERROR_AT = CURRENT_TIMESTAMP, "
                    + "STOPPED_AT = CURRENT_TIMESTAMP, STATUS_MESSAGE = NULL, UPDATED_AT = CURRENT_TIMESTAMP "
                    + "WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                BotStatus.FAILED.getId(), error, botId, instance);
    }

    /**
     * Падение бота: планирует перезапуск с паузой либо переводит в FAILED, если попытки исчерпаны.
     *
     * @return новый статус
     */
    public BotStatus markCrashed(UUID botId, String instance, String error, int maxRestarts,
                                 Duration baseBackoff, Duration maxBackoff) {
        Integer restarts = jdbc.query("SELECT RESTART_COUNT FROM BOT_RUNTIME WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                rs -> rs.next() ? rs.getInt(1) : null, botId, instance);
        if (restarts == null) {
            return null;
        }
        int attempt = restarts + 1;
        if (attempt > maxRestarts) {
            markFailed(botId, instance, error);
            return BotStatus.FAILED;
        }
        long backoff = Math.min(maxBackoff.toMillis(), baseBackoff.toMillis() * (1L << Math.min(attempt - 1, 20)));
        jdbc.update("UPDATE BOT_RUNTIME SET STATUS = ?, OWNER_INSTANCE = NULL, LEASE_UNTIL = NULL, RESTART_COUNT = ?, "
                    + "NEXT_RESTART_AT = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'), LAST_ERROR = ?, "
                    + "LAST_ERROR_AT = CURRENT_TIMESTAMP, STATUS_MESSAGE = ?, UPDATED_AT = CURRENT_TIMESTAMP "
                    + "WHERE BOT_ID = ? AND OWNER_INSTANCE = ?",
                BotStatus.RESTARTING.getId(), attempt, backoff, error,
                "Перезапуск " + attempt + " из " + maxRestarts + " через " + Duration.ofMillis(backoff).toSeconds() + " с",
                botId, instance);
        return BotStatus.RESTARTING;
    }

    // ------------------------------------------------------------------ состояние стратегии

    public Optional<String> loadStrategyState(UUID botId) {
        return jdbc.query("SELECT STRATEGY_STATE FROM BOT_RUNTIME WHERE BOT_ID = ?",
                        (rs, n) -> Optional.ofNullable(rs.getString(1)), botId)
                .stream().findFirst().flatMap(o -> o);
    }

    public boolean saveStrategyState(UUID botId, String instance, String state) {
        return jdbc.update("UPDATE BOT_RUNTIME SET STRATEGY_STATE = ?, UPDATED_AT = CURRENT_TIMESTAMP "
                           + "WHERE BOT_ID = ? AND OWNER_INSTANCE = ?", state, botId, instance) == 1;
    }

    public Optional<OffsetDateTime> now() {
        return Optional.ofNullable(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class));
    }
}
