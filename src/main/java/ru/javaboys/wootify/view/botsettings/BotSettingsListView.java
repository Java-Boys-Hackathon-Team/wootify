package ru.javaboys.wootify.view.botsettings;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.BotSettings;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "bot-settingses", layout = MainView.class)
@ViewController(id = "BotSettings.list")
@ViewDescriptor(path = "bot-settings-list-view.xml")
@LookupComponent("botSettingsesDataGrid")
@DialogMode(width = "64em")
public class BotSettingsListView extends StandardListView<BotSettings> {
}