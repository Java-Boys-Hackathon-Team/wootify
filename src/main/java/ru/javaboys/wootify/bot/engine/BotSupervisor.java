package ru.javaboys.wootify.bot.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import ru.javaboys.wootify.entity.DesiredState;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.entity.StopAction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Супервизор ботов одного экземпляра приложения.
 * <p>
 * В отдельном потоке периодически (и немедленно по {@link #wakeUp()}) приводит фактическое состояние
 * к желаемому:
 * <ol>
 *     <li>продлевает аренду своих ботов; если аренду перехватили, останавливает поток без действий;</li>
 *     <li>передаёт сигнал остановки ботам, которых пользователь остановил;</li>
 *     <li>завершает остановку ботов без потока (ожидавших перезапуска, упавших);</li>
 *     <li>захватывает ботов, которые должны работать, и запускает для них потоки;</li>
 *     <li>предупреждает о зависших ботах и прерывает затянувшуюся остановку.</li>
 * </ol>
 * При старте приложения первая же сверка поднимает всех ботов с желаемым состоянием RUNNING:
 * свои (по стабильному идентификатору экземпляра) сразу, чужие - после истечения их аренды.
 */
public class BotSupervisor implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BotSupervisor.class);
    private static final Duration MIN_STALL = Duration.ofSeconds(60);

    private final BotRuntimeRepository runtime;
    private final BotEventService events;
    private final BotRunner.Dependencies dependencies;
    private final BotEngineProperties properties;
    private final String instance;

    private final Map<UUID, BotHandle> handles = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> stopSignalledAt = new ConcurrentHashMap<>();
    private final Semaphore wakeUps = new Semaphore(0);
    private volatile Thread thread;
    private volatile boolean running;
    private volatile boolean shuttingDown;

    public BotSupervisor(BotRuntimeRepository runtime, BotEventService events, BotRunner.Dependencies dependencies) {
        this.runtime = runtime;
        this.events = events;
        this.dependencies = dependencies;
        this.properties = dependencies.properties();
        this.instance = properties.instanceId();
    }

    public String instanceId() {
        return instance;
    }

    /** Запустить сверку немедленно, не дожидаясь очередного периода. */
    public void wakeUp() {
        wakeUps.release();
    }

    /** Боты, потоки которых работают в этом экземпляре. */
    public Set<UUID> runningBots() {
        return Set.copyOf(handles.keySet());
    }

    // ------------------------------------------------------------------ жизненный цикл

    @Override
    public synchronized void start() {
        if (running || !properties.enabled()) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "bot-supervisor-" + instance);
        thread.setDaemon(true);
        thread.start();
        log.info("Bot supervisor started, instance '{}'", instance);
    }

    @Override
    public void stop() {
        Thread t;
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            shuttingDown = true;
            t = thread;
        }
        wakeUp();
        joinQuietly(t, Duration.ofSeconds(5));
        shutdownBots();
        log.info("Bot supervisor stopped, instance '{}'", instance);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Останавливаться раньше остальных компонентов, пока доступны БД и биржа. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    /**
     * Выключение: боты приостанавливаются и продолжат работу при следующем старте. Публичный для тестов,
     * имитирующих перезапуск сервера.
     */
    public void shutdownBots() {
        shuttingDown = true;
        handles.values().forEach(h -> h.signal(new BotHandle.Shutdown()));
        Instant deadline = Instant.now().plus(properties.shutdownTimeout());
        for (BotHandle h : handles.values()) {
            joinQuietly(h.thread(), Duration.between(Instant.now(), deadline));
        }
        for (BotHandle h : handles.values()) {
            if (h.isAlive()) {
                log.warn("Bot {} did not stop in {}, interrupting", h.botId(), properties.shutdownTimeout());
                h.thread().interrupt();
                joinQuietly(h.thread(), Duration.ofSeconds(2));
            }
        }
        handles.clear();
    }

    private void loop() {
        while (running) {
            try {
                reconcile();
            } catch (Throwable e) {
                log.warn("Bot reconciliation failed: {}", e.toString(), e);
            }
            try {
                if (wakeUps.tryAcquire(properties.reconcileInterval().toMillis(), TimeUnit.MILLISECONDS)) {
                    wakeUps.drainPermits();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ сверка

    /**
     * Один проход сверки. Публичный для тестов.
     */
    public synchronized void reconcile() {
        handles.entrySet().removeIf(e -> {
            boolean finished = !e.getValue().isAlive();
            if (finished) {
                stopSignalledAt.remove(e.getKey());
            }
            return finished;
        });

        // 1. Аренда: подтвердить владение своими ботами.
        Set<UUID> owned = runtime.renewLeases(instance, handles.keySet(), properties.leaseDuration());
        handles.forEach((id, h) -> {
            if (!owned.contains(id)) {
                h.signal(new BotHandle.Lost());
            }
        });

        // 2. Сигнал остановки ботам, которых пользователь остановил.
        for (BotRuntimeRepository.Control c : runtime.controls(handles.keySet())) {
            BotHandle h = handles.get(c.botId());
            if (h != null && c.desired() == DesiredState.STOPPED
                && h.mode() == BotRuntimeRepository.ClaimMode.RUN && h.signal() == null) {
                h.signal(new BotHandle.Stop(c.requestedStopAction() != null
                        ? c.requestedStopAction() : StopAction.KEEP_ORDERS));
                stopSignalledAt.put(c.botId(), Instant.now());
            }
        }

        if (shuttingDown) {
            return;
        }

        // 3. Остановленные без потока и без действия над ордерами.
        runtime.settleStoppedWithoutRunner(instance, handles.keySet());

        // 4. Захват ботов, которые должны работать или требуют завершения остановки.
        int capacity = properties.maxBotsPerInstance() - handles.size();
        if (capacity > 0) {
            for (BotRuntimeRepository.Claimable c : runtime.findClaimable(instance, capacity)) {
                if (handles.containsKey(c.botId()) || handles.size() >= properties.maxBotsPerInstance()) {
                    continue;
                }
                Optional<BotRuntimeRepository.Control> before = runtime.control(c.botId());
                if (before.isPresent() && runtime.tryClaim(c.botId(), c.mode(), instance, properties.leaseDuration())) {
                    launch(c.botId(), c.mode(), before.get());
                }
            }
        }

        // 5. Сторож: зависшие такты и затянувшаяся остановка.
        watchdog();
    }

    private void launch(UUID botId, BotRuntimeRepository.ClaimMode mode, BotRuntimeRepository.Control before) {
        BotHandle handle = new BotHandle(botId, mode);
        Thread t = new Thread(new BotRunner(handle, before, dependencies), "bot-" + botId.toString().substring(0, 8));
        t.setDaemon(true);
        handle.attach(t);
        handles.put(botId, handle);
        t.start();
        log.info("Bot {} launched ({})", botId, mode);
    }

    private void watchdog() {
        Instant now = Instant.now();
        List<UUID> interrupted = new ArrayList<>();
        stopSignalledAt.forEach((id, at) -> {
            BotHandle h = handles.get(id);
            if (h != null && h.isAlive() && at.plus(properties.stopTimeout()).plus(Duration.ofSeconds(30)).isBefore(now)) {
                log.warn("Bot {} stop takes too long, interrupting", id);
                h.thread().interrupt();
                interrupted.add(id);
            }
        });
        interrupted.forEach(stopSignalledAt::remove);

        for (BotHandle h : handles.values()) {
            if (h.signal() == null && h.lastProgress().plus(MIN_STALL).isBefore(now) && h.markStallReported()) {
                events.warn(h.botId(), EventCategory.SYSTEM, "Бот не отвечает больше "
                        + Duration.between(h.lastProgress(), now).toSeconds() + " с: такт выполняется слишком долго");
            }
        }
    }

    private static void joinQuietly(Thread t, Duration timeout) {
        if (t == null || timeout.isNegative()) {
            return;
        }
        try {
            t.join(Math.max(1, timeout.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
