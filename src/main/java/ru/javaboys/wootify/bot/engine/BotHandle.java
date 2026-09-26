package ru.javaboys.wootify.bot.engine;

import ru.javaboys.wootify.entity.StopAction;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ручка работающего бота в памяти экземпляра: поток и сигнал ему от супервизора.
 */
final class BotHandle {

    /** Почему бот должен прекратить работу. */
    sealed interface Signal permits Stop, Shutdown, Lost {
    }

    /** Пользователь остановил бота: выполнить действие над ордерами. */
    record Stop(StopAction action) implements Signal {
    }

    /** Сервер выключается: приостановиться, чтобы продолжить после старта. */
    record Shutdown() implements Signal {
    }

    /** Владение потеряно: ничего не трогать, ботом уже управляет другой экземпляр. */
    record Lost() implements Signal {
    }

    private final UUID botId;
    private final BotRuntimeRepository.ClaimMode mode;
    private final Instant startedAt = Instant.now();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition signalled = lock.newCondition();
    private volatile Signal signal;
    private volatile Thread thread;
    private volatile Instant lastProgress = Instant.now();
    private volatile boolean stallReported;

    BotHandle(UUID botId, BotRuntimeRepository.ClaimMode mode) {
        this.botId = botId;
        this.mode = mode;
    }

    UUID botId() {
        return botId;
    }

    BotRuntimeRepository.ClaimMode mode() {
        return mode;
    }

    Instant startedAt() {
        return startedAt;
    }

    void attach(Thread thread) {
        this.thread = thread;
    }

    Thread thread() {
        return thread;
    }

    boolean isAlive() {
        Thread t = thread;
        return t != null && t.isAlive();
    }

    /**
     * Первый сигнал выигрывает, кроме случая, когда уже идущую остановку нужно превратить в потерю
     * владения: тогда писать в БД больше нельзя.
     */
    void signal(Signal newSignal) {
        lock.lock();
        try {
            if (signal == null || (newSignal instanceof Lost && !(signal instanceof Lost))) {
                signal = newSignal;
            }
            signalled.signalAll();
        } finally {
            lock.unlock();
        }
    }

    Signal signal() {
        return signal;
    }

    /**
     * Пауза между тактами, прерываемая сигналом.
     */
    void awaitSignal(Duration timeout) throws InterruptedException {
        lock.lock();
        try {
            long nanos = timeout.toNanos();
            while (signal == null && nanos > 0) {
                nanos = signalled.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    void progress() {
        lastProgress = Instant.now();
        stallReported = false;
    }

    Instant lastProgress() {
        return lastProgress;
    }

    boolean markStallReported() {
        if (stallReported) {
            return false;
        }
        stallReported = true;
        return true;
    }
}
