package ru.javaboys.wootify.bot.strategy.dca;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.UnconstrainedDataManager;
import ru.javaboys.wootify.bot.order.BotOrderService;
import ru.javaboys.wootify.bot.strategy.StrategyProvider;
import ru.javaboys.wootify.bot.strategy.TradingStrategy;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.StrategyType;

import java.util.List;

public class DcaStrategyProvider implements StrategyProvider {

    private final UnconstrainedDataManager dataManager;
    private final BotOrderService orders;
    /** План цикла хранится в БД; лишние поля игнорируются, чтобы старые планы читались после изменений. */
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DcaStrategyProvider(UnconstrainedDataManager dataManager, BotOrderService orders) {
        this.dataManager = dataManager;
        this.orders = orders;
    }

    @Override
    public StrategyType type() {
        return StrategyType.DCA;
    }

    @Override
    public List<String> validate(Bot bot) {
        return DcaParameters.validate(bot.getDcaSettings());
    }

    @Override
    public TradingStrategy create(Bot bot) {
        return new DcaStrategy(bot, dataManager, orders, mapper);
    }
}
