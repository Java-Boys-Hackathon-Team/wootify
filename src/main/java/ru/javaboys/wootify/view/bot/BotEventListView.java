package ru.javaboys.wootify.view.bot;

import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.facet.Timer;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.BotEvent;
import ru.javaboys.wootify.view.main.MainView;

/**
 * Журнал событий всех ботов.
 */
@Route(value = "bot-events", layout = MainView.class)
@ViewController(id = "BotEvent.list")
@ViewDescriptor(path = "bot-event-list-view.xml")
@LookupComponent("eventsDataGrid")
public class BotEventListView extends StandardListView<BotEvent> {

    @ViewComponent
    private CollectionLoader<BotEvent> eventsDl;
    @ViewComponent
    private CollectionContainer<BotEvent> eventsDc;
    @ViewComponent
    private DataGrid<BotEvent> eventsDataGrid;

    @Subscribe("refreshTimer")
    public void onRefreshTimerTick(Timer.TimerActionEvent event) {
        BotEvent selected = eventsDataGrid.getSingleSelectedItem();
        eventsDl.load();
        if (selected != null && eventsDc.containsItem(selected.getId())) {
            eventsDataGrid.select(eventsDc.getItem(selected.getId()));
        }
    }

    @Supply(to = "eventsDataGrid.level", subject = "renderer")
    private Renderer<BotEvent> levelRenderer() {
        return new ComponentRenderer<>(e -> BotUiSupport.levelBadge(e.getLevel()));
    }
}
