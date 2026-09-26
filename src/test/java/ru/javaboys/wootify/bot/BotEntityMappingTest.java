package ru.javaboys.wootify.bot;

import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.javaboys.wootify.entity.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BotEntityMappingTest {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void botAggregateRoundTrip() {
        Symbol symbol = dataManager.create(Symbol.class);
        symbol.setName("ETH-" + UUID.randomUUID());
        symbol.setWoofiTicker("PERP_ETH_USDC");
        symbol.setAnalogTicker("ETH/USDC:USDC");

        DcaSettings settings = dataManager.create(DcaSettings.class);
        settings.setDirection(TradeType.LONG);
        settings.setDeposit(new BigDecimal("100"));
        settings.setLeverage(2);
        settings.setOrdersCount(5);
        settings.setGridRangePercent(new BigDecimal("5"));
        settings.setVolumeMultiplier(new BigDecimal("1.5"));
        settings.setTakeProfitPercent(new BigDecimal("1"));
        settings.setCycleCooldownSec(0);

        Bot bot = dataManager.create(Bot.class);
        bot.setName("mapping-" + UUID.randomUUID());
        bot.setStrategyType(StrategyType.DCA);
        bot.setTradingMode(TradingMode.PAPER);
        bot.setNetwork(Network.MAINNET);
        bot.setSymbol(symbol);
        bot.setTickIntervalSec(5);
        bot.setMaxRestarts(3);
        bot.setStopAction(StopAction.CANCEL_ORDERS);
        bot.setDcaSettings(settings);

        BotCycle cycle = dataManager.create(BotCycle.class);
        cycle.setBot(bot);
        cycle.setNumber(1);
        cycle.setStatus(CycleStatus.OPEN);
        cycle.setDirection(TradeType.LONG);
        cycle.setStartedAt(OffsetDateTime.now());
        cycle.setEntryQty(BigDecimal.ZERO);
        cycle.setEntryCost(BigDecimal.ZERO);
        cycle.setExitQty(BigDecimal.ZERO);
        cycle.setExitProceeds(BigDecimal.ZERO);
        cycle.setFees(BigDecimal.ZERO);
        cycle.setFilledSafetyOrders(0);

        dataManager.save(new SaveContext().saving(symbol, settings, bot, cycle));

        Bot loaded = dataManager.load(Bot.class).id(bot.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("dcaSettings", FetchPlan.BASE)
                        .add("symbol", FetchPlan.BASE))
                .one();
        assertThat(loaded.getTradingMode()).isEqualTo(TradingMode.PAPER);
        assertThat(loaded.getDcaSettings().getVolumeMultiplier()).isEqualByComparingTo("1.5");
        assertThat(loaded.getCreatedDate()).isNotNull();

        dataManager.remove(loaded);
        assertThat(dataManager.load(BotCycle.class).id(cycle.getId()).optional()).isEmpty();
        assertThat(dataManager.load(DcaSettings.class).id(settings.getId()).optional()).isEmpty();
    }
}
