package ru.javaboys.wootify.view.bot;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.facet.Timer;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.wootify.bot.engine.BotControlService;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotRuntime;
import ru.javaboys.wootify.entity.BotStatus;
import ru.javaboys.wootify.entity.DesiredState;
import ru.javaboys.wootify.view.main.MainView;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "bots", layout = MainView.class)
@ViewController(id = "Bot.list")
@ViewDescriptor(path = "bot-list-view.xml")
@LookupComponent("botsDataGrid")
@DialogMode(width = "64em")
public class BotListView extends StandardListView<Bot> {

    @ViewComponent
    private CollectionLoader<Bot> botsDl;
    @ViewComponent
    private CollectionContainer<Bot> botsDc;
    @ViewComponent
    private DataGrid<Bot> botsDataGrid;
    @ViewComponent
    private Span summaryText;

    @Autowired
    private BotControlService control;
    @Autowired
    private BotUiSupport ui;
    @Autowired
    private BotActions botActions;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private Notifications notifications;
    @Autowired
    private ViewNavigators viewNavigators;

    private Map<UUID, BotUiSupport.CycleSummary> summaries = Map.of();
    private OffsetDateTime now = OffsetDateTime.now();

    @Subscribe(id = "botsDc", target = Target.DATA_CONTAINER)
    public void onBotsDcCollectionChange(CollectionContainer.CollectionChangeEvent<Bot> event) {
        now = ui.dbNow();
        summaries = ui.cycleSummaries(botsDc.getItems().stream().map(Bot::getId).collect(Collectors.toSet()));
        Map<BotStatus, Long> byStatus = new EnumMap<>(BotStatus.class);
        botsDc.getItems().forEach(b -> byStatus.merge(BotUiSupport.status(b), 1L, Long::sum));
        long stale = botsDc.getItems().stream().filter(b -> BotUiSupport.heartbeatStale(b, now)).count();
        summaryText.setText("Всего ботов: " + botsDc.getItems().size()
                            + " · работают: " + byStatus.getOrDefault(BotStatus.RUNNING, 0L)
                            + " · ожидают перезапуска: " + byStatus.getOrDefault(BotStatus.RESTARTING, 0L)
                            + " · сбой: " + byStatus.getOrDefault(BotStatus.FAILED, 0L)
                            + (stale > 0 ? " · без пульса: " + stale : ""));
    }

    @Subscribe("refreshTimer")
    public void onRefreshTimerTick(Timer.TimerActionEvent event) {
        Set<UUID> selected = botsDataGrid.getSelectedItems().stream().map(Bot::getId).collect(Collectors.toSet());
        botsDl.load();
        botsDc.getItems().stream().filter(b -> selected.contains(b.getId())).forEach(botsDataGrid::select);
    }

    @Supply(to = "botsDataGrid.status", subject = "renderer")
    private Renderer<Bot> statusRenderer() {
        return new ComponentRenderer<>(bot -> BotUiSupport.statusBadge(BotUiSupport.status(bot)));
    }

    @Supply(to = "botsDataGrid.activity", subject = "renderer")
    private Renderer<Bot> activityRenderer() {
        return new ComponentRenderer<>(bot -> {
            BotRuntime r = bot.getRuntime();
            String text = r == null || r.getStatusMessage() == null ? "" : r.getStatusMessage();
            Span span = new Span(text);
            span.setTitle(text);
            return span;
        });
    }

    @Supply(to = "botsDataGrid.heartbeat", subject = "renderer")
    private Renderer<Bot> heartbeatRenderer() {
        return new ComponentRenderer<>(bot -> BotUiSupport.heartbeat(bot, now));
    }

    @Supply(to = "botsDataGrid.cycles", subject = "renderer")
    private Renderer<Bot> cyclesRenderer() {
        return new ComponentRenderer<>(bot -> {
            BotUiSupport.CycleSummary s = summaries.get(bot.getId());
            if (s == null) {
                return new Span("0");
            }
            return new Span(s.closedCycles() + (s.activeCycle() != null ? " + #" + s.activeCycle() + " открыт" : ""));
        });
    }

    @Supply(to = "botsDataGrid.pnl", subject = "renderer")
    private Renderer<Bot> pnlRenderer() {
        return new ComponentRenderer<>(bot -> {
            BotUiSupport.CycleSummary s = summaries.get(bot.getId());
            return BotUiSupport.pnl(s == null ? null : s.realizedPnl());
        });
    }

    @Supply(to = "botsDataGrid.lastError", subject = "renderer")
    private Renderer<Bot> lastErrorRenderer() {
        return new ComponentRenderer<>(bot -> {
            BotRuntime r = bot.getRuntime();
            boolean relevant = r != null && r.getLastError() != null
                               && (r.getStatus() == BotStatus.FAILED || r.getStatus() == BotStatus.RESTARTING
                                   || (r.getLastErrorAt() != null && r.getLastErrorAt().plusMinutes(10).isAfter(now)));
            Span span = new Span(relevant ? r.getLastError() : "");
            if (relevant) {
                span.setTitle(r.getLastError());
                span.addClassNames("bot-error-text");
            }
            return span;
        });
    }

    @Supply(to = "botsDataGrid.controls", subject = "renderer")
    private Renderer<Bot> controlsRenderer() {
        return new ComponentRenderer<>(bot -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setPadding(false);
            layout.setSpacing(true);
            BotRuntime r = bot.getRuntime();
            boolean wantsRunning = r != null && r.getDesiredState() == DesiredState.RUNNING;
            if (wantsRunning) {
                Button stop = new Button("Стоп", VaadinIcon.STOP.create(), e -> botActions.askStop(this, bot, this::refresh));
                stop.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                layout.add(stop);
            } else {
                Button start = new Button("Старт", VaadinIcon.PLAY.create(), e -> botActions.start(bot, this::refresh));
                start.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
                start.setEnabled(BotUiSupport.status(bot) != BotStatus.STOPPING);
                layout.add(start);
            }
            return layout;
        });
    }

    @Subscribe(id = "removeButton", subject = "clickListener")
    public void onRemoveButtonClick(ClickEvent<JmixButton> event) {
        Bot bot = botsDataGrid.getSingleSelectedItem();
        if (bot == null) {
            notifications.create("Выберите бота").withType(Notifications.Type.WARNING).show();
            return;
        }
        if (!control.isEditable(bot.getId())) {
            notifications.create("Сначала остановите бота").withType(Notifications.Type.WARNING).show();
            return;
        }
        dialogs.createOptionDialog()
                .withHeader("Удаление бота")
                .withText("Удалить бота «" + bot.getName() + "» вместе с журналом и циклами? Ордера останутся в истории.")
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> {
                            control.delete(bot.getId());
                            refresh();
                        }),
                        new DialogAction(DialogAction.Type.NO))
                .open();
    }

    @Subscribe(id = "eventsButton", subject = "clickListener")
    public void onEventsButtonClick(ClickEvent<JmixButton> event) {
        viewNavigators.view(this, BotEventListView.class).navigate();
    }

    private void refresh() {
        botsDl.load();
    }
}
