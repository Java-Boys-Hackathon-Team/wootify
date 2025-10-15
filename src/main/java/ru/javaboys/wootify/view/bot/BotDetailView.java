package ru.javaboys.wootify.view.bot;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "bots/:id", layout = MainView.class)
@ViewController(id = "Bot.detail")
@ViewDescriptor(path = "bot-detail-view.xml")
@EditedEntityContainer("botDc")
public class BotDetailView extends StandardDetailView<Bot> {
}