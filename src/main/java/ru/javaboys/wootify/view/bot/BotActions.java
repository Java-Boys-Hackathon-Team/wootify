package ru.javaboys.wootify.view.bot;

import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.view.View;
import org.springframework.stereotype.Component;
import ru.javaboys.wootify.bot.engine.BotControlService;
import ru.javaboys.wootify.common.BotConfigurationException;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.StopAction;

/**
 * Запуск и остановка бота из UI с одинаковым поведением в списке и карточке.
 */
@Component
public class BotActions {

    private final BotControlService control;
    private final Dialogs dialogs;
    private final Notifications notifications;

    public BotActions(BotControlService control, Dialogs dialogs, Notifications notifications) {
        this.control = control;
        this.dialogs = dialogs;
        this.notifications = notifications;
    }

    public void start(Bot bot, Runnable afterwards) {
        try {
            control.start(bot.getId());
            notifications.create("Бот «" + bot.getName() + "» запускается")
                    .withType(Notifications.Type.SUCCESS).show();
        } catch (BotConfigurationException e) {
            notifications.create("Бот не может быть запущен", e.getMessage())
                    .withType(Notifications.Type.ERROR).withDuration(10_000).show();
        }
        afterwards.run();
    }

    public void askStop(View<?> origin, Bot bot, Runnable afterwards) {
        dialogs.createInputDialog(origin)
                .withHeader("Остановка бота «" + bot.getName() + "»")
                .withParameters(InputParameter.enumParameter("action", StopAction.class)
                        .withLabel("Что сделать с ордерами и позицией")
                        .withDefaultValue(bot.getStopAction() != null ? bot.getStopAction() : StopAction.CANCEL_ORDERS)
                        .withRequired(true))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(event -> {
                    if (event.closedWith(DialogOutcome.OK)) {
                        StopAction action = event.getValue("action");
                        control.stop(bot.getId(), action);
                        notifications.create("Бот «" + bot.getName() + "» останавливается").show();
                        afterwards.run();
                    }
                })
                .open();
    }
}
