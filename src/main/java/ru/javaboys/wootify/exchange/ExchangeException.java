package ru.javaboys.wootify.exchange;

import ru.javaboys.wootify.common.TransientFailure;

/**
 * Ошибка обращения к бирже. Категория определяет реакцию бота.
 */
public class ExchangeException extends RuntimeException {

    public enum Kind {
        /** Сеть, таймаут, 5xx, ограничение частоты: можно повторить позже. */
        TRANSIENT,
        /** Биржа отклонила запрос по существу (недостаточно средств, неверные параметры). */
        REJECTED,
        /** Ключи API недействительны или не имеют прав. */
        AUTH,
        /** Биржа не знает ордер. */
        NOT_FOUND,
        UNKNOWN
    }

    private final Kind kind;

    public ExchangeException(Kind kind, String message) {
        this(kind, message, null);
    }

    public ExchangeException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static ExchangeException of(Kind kind, String message, Throwable cause) {
        return kind == Kind.TRANSIENT ? new Transient(message, cause) : new ExchangeException(kind, message, cause);
    }

    /** Временная ошибка биржи; движок повторит такт после паузы. */
    public static class Transient extends ExchangeException implements TransientFailure {
        public Transient(String message, Throwable cause) {
            super(Kind.TRANSIENT, message, cause);
        }
    }
}
