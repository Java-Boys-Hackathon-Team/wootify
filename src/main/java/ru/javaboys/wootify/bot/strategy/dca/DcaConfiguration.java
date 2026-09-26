package ru.javaboys.wootify.bot.strategy.dca;

import io.jmix.core.UnconstrainedDataManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.javaboys.wootify.bot.order.BotOrderService;

@Configuration
public class DcaConfiguration {

    @Bean
    public BotOrderService botOrderService(UnconstrainedDataManager dataManager) {
        return new BotOrderService(dataManager);
    }

    @Bean
    public DcaStrategyProvider dcaStrategyProvider(UnconstrainedDataManager dataManager, BotOrderService orders) {
        return new DcaStrategyProvider(dataManager, orders);
    }
}
