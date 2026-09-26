package ru.javaboys.wootify.view.bot;

import com.vaadin.flow.component.html.Span;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotRuntime;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.EventLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Отображение состояния ботов, общее для списка и карточки.
 */
@Component
public class BotUiSupport {

    /** Сводка по закрытым циклам бота. */
    public record CycleSummary(int closedCycles, BigDecimal realizedPnl, Integer activeCycle) {
    }

    private final JdbcTemplate jdbc;

    public BotUiSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static BotStatus status(Bot bot) {
        BotRuntime runtime = bot.getRuntime();
        return runtime == null || runtime.getStatus() == null ? BotStatus.STOPPED : runtime.getStatus();
    }

    public static String statusText(BotStatus status) {
        return switch (status) {
            case STOPPED -> "Остановлен";
            case STARTING -> "Запускается";
            case RUNNING -> "Работает";
            case STOPPING -> "Останавливается";
            case SUSPENDED -> "Приостановлен";
            case RESTARTING -> "Ожидает перезапуска";
            case FAILED -> "Сбой";
        };
    }

    public static Span statusBadge(BotStatus status) {
        Span badge = new Span(statusText(status));
        badge.addClassNames("bot-badge", "bot-status-" + status.getId().toLowerCase());
        return badge;
    }

    public static Span levelBadge(EventLevel level) {
        Span badge = new Span(switch (level) {
            case INFO -> "Инфо";
            case WARN -> "Внимание";
            case ERROR -> "Ошибка";
        });
        badge.addClassNames("bot-badge", "bot-level-" + level.getId().toLowerCase());
        return badge;
    }

    /**
     * Бот должен работать, но давно не подавал признаков жизни: поток завис или экземпляр упал.
     */
    public static boolean heartbeatStale(Bot bot, OffsetDateTime now) {
        BotRuntime runtime = bot.getRuntime();
        if (runtime == null || runtime.getLastHeartbeat() == null) {
            return false;
        }
        BotStatus status = status(bot);
        if (status != BotStatus.RUNNING && status != BotStatus.STARTING) {
            return false;
        }
        long tick = bot.getTickIntervalSec() == null ? 5 : bot.getTickIntervalSec();
        Duration limit = Duration.ofSeconds(Math.max(45, tick * 3));
        return runtime.getLastHeartbeat().plus(limit).isBefore(now);
    }

    public static Span heartbeat(Bot bot, OffsetDateTime now) {
        BotRuntime runtime = bot.getRuntime();
        if (runtime == null || runtime.getLastHeartbeat() == null) {
            return new Span("—");
        }
        String ago = ago(Duration.between(runtime.getLastHeartbeat(), now));
        Span span = new Span(heartbeatStale(bot, now) ? "нет пульса " + ago : ago);
        if (heartbeatStale(bot, now)) {
            span.addClassNames("bot-heartbeat-stale");
        }
        return span;
    }

    public static String ago(Duration d) {
        long s = Math.max(0, d.toSeconds());
        if (s < 60) {
            return s + " с назад";
        }
        if (s < 3600) {
            return (s / 60) + " мин назад";
        }
        if (s < 86400) {
            return (s / 3600) + " ч назад";
        }
        return (s / 86400) + " дн назад";
    }

    public static Span pnl(BigDecimal value) {
        if (value == null) {
            return new Span("—");
        }
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        Span span = new Span((rounded.signum() > 0 ? "+" : "") + rounded.toPlainString());
        span.addClassNames(rounded.signum() >= 0 ? "bot-pnl-positive" : "bot-pnl-negative");
        return span;
    }

    public Map<UUID, CycleSummary> cycleSummaries(Collection<UUID> botIds) {
        Map<UUID, CycleSummary> result = new HashMap<>();
        if (botIds.isEmpty()) {
            return result;
        }
        jdbc.query(con -> {
            var ps = con.prepareStatement("""
                    SELECT BOT_ID,
                           COUNT(*) FILTER (WHERE STATUS = 'CLOSED') AS CLOSED_CYCLES,
                           SUM(REALIZED_PNL) FILTER (WHERE STATUS = 'CLOSED') AS PNL,
                           MAX(NUMBER_) FILTER (WHERE STATUS <> 'CLOSED') AS ACTIVE_CYCLE
                    FROM BOT_CYCLE WHERE BOT_ID = ANY (?) GROUP BY BOT_ID""");
            ps.setArray(1, con.createArrayOf("uuid", botIds.toArray()));
            return ps;
        }, rs -> {
            Integer active = rs.getObject("ACTIVE_CYCLE", Integer.class);
            result.put(rs.getObject("BOT_ID", UUID.class), new CycleSummary(rs.getInt("CLOSED_CYCLES"),
                    rs.getBigDecimal("PNL"), active));
        });
        return result;
    }

    public OffsetDateTime dbNow() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
    }
}
