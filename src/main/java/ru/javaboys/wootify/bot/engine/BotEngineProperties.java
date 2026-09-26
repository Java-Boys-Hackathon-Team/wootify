package ru.javaboys.wootify.bot.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки движка ботов ({@code wootify.bots.*}).
 *
 * @param enabled               запускать ли супервизор в этом экземпляре приложения
 * @param instanceId            идентификатор экземпляра - владельца ботов. Должен быть стабильным между
 *                              перезапусками, тогда свои боты подхватываются сразу, не дожидаясь истечения аренды
 * @param reconcileInterval     период сверки желаемого и фактического состояния
 * @param leaseDuration         срок аренды бота; если экземпляр не продлил её, бота может забрать другой
 * @param stopTimeout           сколько ждать выполнения действия при остановке, прежде чем прервать поток
 * @param shutdownTimeout       сколько ждать завершения потоков ботов при выключении приложения
 * @param restartBackoff        пауза перед первым автоматическим перезапуском, далее удваивается
 * @param maxRestartBackoff     предел паузы перед перезапуском
 * @param healthyRunDuration    после стольких минут стабильной работы счётчик перезапусков обнуляется
 * @param transientErrorLimit   сколько бот может непрерывно получать временные ошибки, прежде чем это
 *                              будет считаться падением
 * @param maxBotsPerInstance    сколько ботов одновременно исполняет один экземпляр
 * @param eventRetention        срок хранения журнала событий
 */
@ConfigurationProperties("wootify.bots")
public record BotEngineProperties(Boolean enabled, String instanceId, Duration reconcileInterval,
                                  Duration leaseDuration, Duration stopTimeout, Duration shutdownTimeout,
                                  Duration restartBackoff, Duration maxRestartBackoff, Duration healthyRunDuration,
                                  Duration transientErrorLimit, Integer maxBotsPerInstance,
                                  Duration eventRetention) {

    public BotEngineProperties {
        enabled = enabled == null || enabled;
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = defaultInstanceId();
        }
        reconcileInterval = orDefault(reconcileInterval, Duration.ofSeconds(2));
        leaseDuration = orDefault(leaseDuration, Duration.ofSeconds(30));
        stopTimeout = orDefault(stopTimeout, Duration.ofSeconds(60));
        shutdownTimeout = orDefault(shutdownTimeout, Duration.ofSeconds(20));
        restartBackoff = orDefault(restartBackoff, Duration.ofSeconds(10));
        maxRestartBackoff = orDefault(maxRestartBackoff, Duration.ofMinutes(5));
        healthyRunDuration = orDefault(healthyRunDuration, Duration.ofMinutes(10));
        transientErrorLimit = orDefault(transientErrorLimit, Duration.ofMinutes(15));
        maxBotsPerInstance = maxBotsPerInstance == null ? 100 : maxBotsPerInstance;
        eventRetention = orDefault(eventRetention, Duration.ofDays(30));
    }

    /** Копия для другого экземпляра приложения (используется в тестах). */
    public BotEngineProperties forInstance(String id) {
        return new BotEngineProperties(true, id, reconcileInterval, leaseDuration, stopTimeout, shutdownTimeout,
                restartBackoff, maxRestartBackoff, healthyRunDuration, transientErrorLimit, maxBotsPerInstance,
                eventRetention);
    }

    private static Duration orDefault(Duration value, Duration def) {
        return value == null ? def : value;
    }

    private static String defaultInstanceId() {
        String env = System.getenv("WOOTIFY_INSTANCE_ID");
        if (env != null && !env.isBlank()) {
            return env;
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "wootify";
        }
    }
}
