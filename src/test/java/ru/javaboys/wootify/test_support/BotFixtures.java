package ru.javaboys.wootify.test_support;

import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Создание тестовых ботов.
 */
public final class BotFixtures {

    private BotFixtures() {
    }

    public static Symbol symbol(UnconstrainedDataManager dm, String orderlyTicker) {
        Symbol symbol = dm.create(Symbol.class);
        symbol.setName(orderlyTicker + "-" + UUID.randomUUID().toString().substring(0, 8));
        symbol.setWoofiTicker(orderlyTicker);
        symbol.setAnalogTicker(orderlyTicker.replace("PERP_", "").replace("_USDC", "") + "/USDC:USDC");
        return dm.save(symbol);
    }

    public static DcaSettings dcaSettings(UnconstrainedDataManager dm) {
        DcaSettings s = dm.create(DcaSettings.class);
        s.setDirection(TradeType.LONG);
        s.setDeposit(new BigDecimal("100"));
        s.setLeverage(1);
        s.setOrdersCount(3);
        s.setGridRangePercent(new BigDecimal("4"));
        s.setVolumeMultiplier(new BigDecimal("1"));
        s.setTakeProfitPercent(new BigDecimal("1"));
        s.setCycleCooldownSec(0);
        return s;
    }

    public static Bot bot(UnconstrainedDataManager dm, String name, Symbol symbol, Consumer<Bot> botCustomizer,
                          Consumer<DcaSettings> settingsCustomizer) {
        DcaSettings settings = dcaSettings(dm);
        settingsCustomizer.accept(settings);
        Bot bot = dm.create(Bot.class);
        bot.setName(name + "-" + UUID.randomUUID().toString().substring(0, 8));
        bot.setStrategyType(StrategyType.DCA);
        bot.setTradingMode(TradingMode.PAPER);
        bot.setNetwork(Network.MAINNET);
        bot.setSymbol(symbol);
        bot.setTickIntervalSec(1);
        bot.setMaxRestarts(3);
        bot.setStopAction(StopAction.CANCEL_ORDERS);
        bot.setDcaSettings(settings);
        botCustomizer.accept(bot);
        dm.save(new SaveContext().saving(settings, bot));
        return bot;
    }
}
