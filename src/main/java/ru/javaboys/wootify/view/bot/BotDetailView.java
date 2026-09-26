package ru.javaboys.wootify.view.bot;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.facet.Timer;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.model.InstanceLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.wootify.bot.engine.BotControlService;
import ru.javaboys.wootify.bot.strategy.dca.DcaParameters;
import ru.javaboys.wootify.bot.strategy.dca.DcaPlan;
import ru.javaboys.wootify.entity.*;
import ru.javaboys.wootify.view.main.MainView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "bots/:id", layout = MainView.class)
@ViewController(id = "Bot.detail")
@ViewDescriptor(path = "bot-detail-view.xml")
@EditedEntityContainer("botDc")
public class BotDetailView extends StandardDetailView<Bot> {

    @ViewComponent
    private DataContext dataContext;
    @ViewComponent
    private InstanceContainer<DcaSettings> dcaSettingsDc;
    @ViewComponent
    private InstanceContainer<BotRuntime> runtimeDc;
    @ViewComponent
    private InstanceLoader<BotRuntime> runtimeDl;
    @ViewComponent
    private CollectionLoader<BotCycle> cyclesDl;
    @ViewComponent
    private CollectionLoader<Order> ordersDl;
    @ViewComponent
    private CollectionLoader<BotEvent> eventsDl;
    @ViewComponent
    private HorizontalLayout statusBox;
    @ViewComponent
    private Span activityText;
    @ViewComponent
    private Span readOnlyHint;
    @ViewComponent
    private Span planPreview;
    @ViewComponent
    private JmixButton startButton;
    @ViewComponent
    private JmixButton stopButton;

    @Autowired
    private BotControlService control;
    @Autowired
    private BotActions botActions;
    @Autowired
    private Notifications notifications;
    @Autowired
    private EntityStates entityStates;

    @Subscribe
    public void onInitEntity(InitEntityEvent<Bot> event) {
        Bot bot = event.getEntity();
        bot.setStrategyType(StrategyType.DCA);
        bot.setTradingMode(TradingMode.PAPER);
        bot.setNetwork(Network.MAINNET);
        bot.setTickIntervalSec(5);
        bot.setMaxRestarts(3);
        bot.setStopAction(StopAction.CANCEL_ORDERS);

        DcaSettings settings = dataContext.create(DcaSettings.class);
        settings.setDirection(TradeType.LONG);
        settings.setDeposit(new BigDecimal("200"));
        settings.setLeverage(1);
        settings.setOrdersCount(5);
        settings.setGridRangePercent(new BigDecimal("5"));
        settings.setVolumeMultiplier(new BigDecimal("1.2"));
        settings.setTakeProfitPercent(new BigDecimal("1"));
        settings.setCycleCooldownSec(0);
        bot.setDcaSettings(settings);
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        dcaSettingsDc.addItemPropertyChangeListener(e -> updatePlanPreview());
        updatePlanPreview();
        updateState();
    }

    @Subscribe("refreshTimer")
    public void onRefreshTimerTick(Timer.TimerActionEvent event) {
        if (entityStates.isNew(getEditedEntity())) {
            return;
        }
        runtimeDl.load();
        cyclesDl.load();
        ordersDl.load();
        eventsDl.load();
        updateState();
    }

    private void updateState() {
        boolean isNew = entityStates.isNew(getEditedEntity());
        BotRuntime runtime = isNew ? null : runtimeDc.getItemOrNull();
        BotStatus status = runtime == null || runtime.getStatus() == null ? BotStatus.STOPPED : runtime.getStatus();
        boolean wantsRunning = runtime != null && runtime.getDesiredState() == DesiredState.RUNNING;

        statusBox.removeAll();
        statusBox.add(BotUiSupport.statusBadge(status));
        activityText.setText(runtime == null || runtime.getStatusMessage() == null ? "" : runtime.getStatusMessage());

        startButton.setVisible(!isNew && !wantsRunning);
        startButton.setEnabled(status != BotStatus.STOPPING);
        stopButton.setVisible(!isNew && wantsRunning);

        boolean editable = isNew || control.isEditable(getEditedEntity().getId());
        if (isReadOnly() == editable) {
            setReadOnly(!editable);
        }
        readOnlyHint.setVisible(!editable);
    }

    private void updatePlanPreview() {
        DcaSettings s = dcaSettingsDc.getItemOrNull();
        List<String> problems = DcaParameters.validate(s);
        if (!problems.isEmpty()) {
            planPreview.setText("Сетка: " + String.join("; ", problems));
            return;
        }
        List<DcaPlan.PreviewLevel> levels = DcaPlan.preview(DcaParameters.of(s));
        String text = levels.stream()
                .map(l -> (l.level() == 0 ? "базовый" : "№" + l.level() + " (" + l.offsetPercent().stripTrailingZeros()
                        .setScale(2, RoundingMode.HALF_UP).toPlainString() + "%)")
                          + " " + l.quoteAmount().toPlainString())
                .collect(Collectors.joining(" · "));
        BigDecimal total = levels.stream().map(DcaPlan.PreviewLevel::quoteAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        planPreview.setText("Сетка (стоимость ордеров, USDC): " + text + ". Всего " + total.toPlainString()
                            + " USDC с учётом плеча.");
    }

    @Subscribe
    public void onValidation(ValidationEvent event) {
        List<String> problems = control.validate(getEditedEntity());
        if (!problems.isEmpty()) {
            ValidationErrors errors = new ValidationErrors();
            problems.forEach(errors::add);
            event.addErrors(errors);
        }
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        if (!entityStates.isNew(getEditedEntity()) && !control.isEditable(getEditedEntity().getId())) {
            notifications.create("Бот работает: остановите его, чтобы изменить настройки")
                    .withType(Notifications.Type.WARNING).show();
            event.preventSave();
        }
    }

    @Subscribe(id = "startButton", subject = "clickListener")
    public void onStartButtonClick(ClickEvent<JmixButton> event) {
        if (hasUnsavedChanges()) {
            notifications.create("Сначала сохраните изменения").withType(Notifications.Type.WARNING).show();
            return;
        }
        botActions.start(getEditedEntity(), this::refreshNow);
    }

    @Subscribe(id = "stopButton", subject = "clickListener")
    public void onStopButtonClick(ClickEvent<JmixButton> event) {
        botActions.askStop(this, getEditedEntity(), this::refreshNow);
    }

    private void refreshNow() {
        runtimeDl.load();
        updateState();
    }

    @Supply(to = "cyclesDataGrid.pnl", subject = "renderer")
    private Renderer<BotCycle> cyclePnlRenderer() {
        return new ComponentRenderer<>(c -> BotUiSupport.pnl(c.getRealizedPnl()));
    }

    @Supply(to = "eventsDataGrid.level", subject = "renderer")
    private Renderer<BotEvent> levelRenderer() {
        return new ComponentRenderer<>(e -> BotUiSupport.levelBadge(e.getLevel()));
    }
}
