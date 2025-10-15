package ru.javaboys.wootify.view.bot;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "bots", layout = MainView.class)
@ViewController(id = "Bot.list")
@ViewDescriptor(path = "bot-list-view.xml")
@LookupComponent("botsDataGrid")
@DialogMode(width = "64em")
public class BotListView extends StandardListView<Bot> {
}