package ru.javaboys.wootify.view;

import io.jmix.core.UnconstrainedDataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.javaboys.wootify.WootifyApplication;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.DcaSettings;
import ru.javaboys.wootify.test_support.BotFixtures;
import ru.javaboys.wootify.test_support.TestExchangeConfig;
import ru.javaboys.wootify.view.bot.BotDetailView;
import ru.javaboys.wootify.view.bot.BotEventListView;
import ru.javaboys.wootify.view.bot.BotListView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Экраны ботов открываются без ошибок: список, новый бот, существующий бот, журнал.
 */
@UiTest
@SpringBootTest(classes = {WootifyApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
@Import(TestExchangeConfig.class)
class BotViewsUiTest {

    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    UnconstrainedDataManager dataManager;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM BOT WHERE NAME LIKE 'ui-bot%'");
    }

    @Test
    void botListShowsBotsWithControls() {
        Bot bot = BotFixtures.bot(dataManager, "ui-bot", BotFixtures.symbol(dataManager, "PERP_UI_USDC"), b -> {
        }, s -> {
        });
        viewNavigators.view(UiTestUtils.getCurrentView(), BotListView.class).navigate();
        BotListView view = UiTestUtils.getCurrentView();
        @SuppressWarnings("unchecked")
        DataGrid<Bot> grid = (DataGrid<Bot>) UiTestUtils.getComponent(view, "botsDataGrid");
        assertThat(grid.getGenericDataView().getItems()).extracting(Bot::getId).contains(bot.getId());
    }

    @Test
    void newBotFormHasDefaultsAndHidesControls() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Bot.class).newEntity().navigate();
        BotDetailView view = UiTestUtils.getCurrentView();
        Bot bot = view.getEditedEntity();
        DcaSettings settings = bot.getDcaSettings();
        assertThat(settings).isNotNull();
        assertThat(settings.getOrdersCount()).isEqualTo(5);
        assertThat(bot.getTickIntervalSec()).isEqualTo(5);
        assertThat(((JmixButton) UiTestUtils.getComponent(view, "startButton")).isVisible()).isFalse();
        assertThat(((JmixButton) UiTestUtils.getComponent(view, "stopButton")).isVisible()).isFalse();
        assertThat(view.isReadOnly()).isFalse();
    }

    @Test
    void existingStoppedBotIsEditableAndCanBeStarted() {
        Bot bot = BotFixtures.bot(dataManager, "ui-bot", BotFixtures.symbol(dataManager, "PERP_UI_USDC"), b -> {
        }, s -> {
        });
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Bot.class).editEntity(bot).navigate();
        BotDetailView view = UiTestUtils.getCurrentView();
        assertThat(view.getEditedEntity().getId()).isEqualTo(bot.getId());
        assertThat(((JmixButton) UiTestUtils.getComponent(view, "startButton")).isVisible()).isTrue();
        assertThat(view.isReadOnly()).isFalse();
    }

    @Test
    void runningBotIsReadOnly() {
        Bot bot = BotFixtures.bot(dataManager, "ui-bot", BotFixtures.symbol(dataManager, "PERP_UI_USDC"), b -> {
        }, s -> {
        });
        jdbc.update("UPDATE BOT_RUNTIME SET DESIRED_STATE = 'RUNNING', STATUS = 'RUNNING' WHERE BOT_ID = ?", bot.getId());
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Bot.class).editEntity(bot).navigate();
        BotDetailView view = UiTestUtils.getCurrentView();
        assertThat(view.isReadOnly()).isTrue();
        assertThat(((JmixButton) UiTestUtils.getComponent(view, "stopButton")).isVisible()).isTrue();
    }

    @Test
    void eventJournalOpens() {
        viewNavigators.view(UiTestUtils.getCurrentView(), BotEventListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(BotEventListView.class);
    }
}
