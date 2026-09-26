package ru.javaboys.wootify.bot.engine;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.common.TransientFailure;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Классификация сбоев бота.
 */
public final class Failures {

    private static final int MAX_DEPTH = 10;

    private Failures() {
    }

    /**
     * Временный сбой: сеть, биржа, нет цен, кратковременная недоступность БД.
     */
    public static boolean isTransient(Throwable e) {
        Throwable t = e;
        for (int i = 0; t != null && i < MAX_DEPTH; i++, t = t.getCause()) {
            if (t instanceof TransientFailure
                || t instanceof TransientDataAccessException
                || t instanceof RecoverableDataAccessException
                || t instanceof DataAccessResourceFailureException
                || t instanceof CannotCreateTransactionException) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConfiguration(Throwable e) {
        Throwable t = e;
        for (int i = 0; t != null && i < MAX_DEPTH; i++, t = t.getCause()) {
            if (t instanceof BotConfigurationException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Короткое сообщение для UI: текст самого информативного исключения цепочки.
     */
    public static String message(Throwable e) {
        Throwable t = e;
        String best = null;
        for (int i = 0; t != null && i < MAX_DEPTH; i++, t = t.getCause()) {
            if (t instanceof TransientFailure || t instanceof BotConfigurationException) {
                return t.getMessage();
            }
            if (best == null && t.getMessage() != null && !t.getMessage().isBlank()) {
                best = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        }
        return best != null ? best : e.getClass().getName();
    }

    public static String stackTrace(Throwable e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
