package ru.javaboys.wootify.bot.engine;

import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import ru.javaboys.wootify.bot.strategy.StrategyRegistry;
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.DesiredState;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.entity.StopAction;
import ru.javaboys.wootify.exchange.ExchangeGatewayFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Управление ботами из UI: запуск, остановка, удаление. Меняет только желаемое состояние;
 * фактическое приводит к нему {@link BotSupervisor}.
 */
public class BotControlService {

    private final BotLoader loader;
    private final BotRuntimeRepository runtime;
    private final BotEventService events;
    private final StrategyRegistry strategies;
    private final ExchangeGatewayFactory gateways;
    private final BotSupervisor supervisor;
    private final UnconstrainedDataManager dataManager;
    private final CurrentAuthentication currentAuthentication;

    public BotControlService(BotLoader loader, BotRuntimeRepository runtime, BotEventService events,
                             StrategyRegistry strategies, ExchangeGatewayFactory gateways, BotSupervisor supervisor,
                             UnconstrainedDataManager dataManager, CurrentAuthentication currentAuthentication) {
        this.loader = loader;
        this.runtime = runtime;
        this.events = events;
        this.strategies = strategies;
        this.gateways = gateways;
        this.supervisor = supervisor;
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
    }

    /**
     * Проверка настроек без обращения к бирже.
     *
     * @return список проблем, пустой если бота можно запускать
     */
    public List<String> validate(Bot bot) {
        List<String> problems = new ArrayList<>();
        try {
            problems.addAll(strategies.provider(bot).validate(bot));
            gateways.forBot(bot);
        } catch (BotConfigurationException e) {
            problems.add(e.getMessage());
        }
        return problems;
    }

    /**
     * @throws BotConfigurationException если настройки некорректны
     */
    public void start(UUID botId) {
        Bot bot = loader.load(botId).orElseThrow(() -> new IllegalArgumentException("Бот не найден"));
        List<String> problems = validate(bot);
        if (!problems.isEmpty()) {
            throw new BotConfigurationException(String.join("; ", problems));
        }
        runtime.ensureExists(botId);
        runtime.requestStart(botId);
        events.info(botId, EventCategory.LIFECYCLE, "Запуск запрошен пользователем " + user());
        supervisor.wakeUp();
    }

    public void stop(UUID botId, StopAction action) {
        runtime.ensureExists(botId);
        runtime.requestStop(botId, action);
        events.info(botId, EventCategory.LIFECYCLE,
                "Остановка запрошена пользователем " + user() + " (" + BotRunner.describe(action) + ")");
        supervisor.wakeUp();
    }

    /**
     * Настройки можно менять, только пока бот остановлен и не исполняется.
     */
    public boolean isEditable(UUID botId) {
        return runtime.control(botId)
                .map(c -> c.desired() == DesiredState.STOPPED
                          && (c.status() == BotStatus.STOPPED || c.status() == BotStatus.FAILED))
                .orElse(true);
    }

    /**
     * Удаление остановленного бота вместе с журналом и циклами; ордера остаются в истории.
     */
    public void delete(UUID botId) {
        if (!isEditable(botId)) {
            throw new IllegalStateException("Сначала остановите бота");
        }
        loader.load(botId).ifPresent(dataManager::remove);
    }

    public Optional<BotRuntimeRepository.Control> control(UUID botId) {
        return runtime.control(botId);
    }

    private String user() {
        try {
            return currentAuthentication.isSet() ? currentAuthentication.getUser().getUsername() : "system";
        } catch (RuntimeException e) {
            return "system";
        }
    }
}
