package ru.javaboys.wootify.bot.engine;

import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import ru.javaboys.wootify.entity.Bot;

import java.util.Optional;
import java.util.UUID;

/**
 * Загружает конфигурацию бота целиком: символ, аккаунт, ключ API, настройки стратегии.
 */
public class BotLoader {

    private final UnconstrainedDataManager dataManager;

    public BotLoader(UnconstrainedDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public Optional<Bot> load(UUID botId) {
        return dataManager.load(Bot.class).id(botId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("symbol", FetchPlan.BASE)
                        .add("account", FetchPlan.BASE)
                        .add("apiKey", FetchPlan.BASE)
                        .add("dcaSettings", FetchPlan.BASE))
                .optional();
    }
}
