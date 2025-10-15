package ru.javaboys.wootify.view.botsettings;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.wootify.entity.BotSettings;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "bot-settingses/:id", layout = MainView.class)
@ViewController(id = "BotSettings.detail")
@ViewDescriptor(path = "bot-settings-detail-view.xml")
@EditedEntityContainer("botSettingsDc")
public class BotSettingsDetailView extends StandardDetailView<BotSettings> {
}