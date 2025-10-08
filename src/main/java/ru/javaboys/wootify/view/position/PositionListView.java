package ru.javaboys.wootify.view.position;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.Position;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "positions", layout = MainView.class)
@ViewController(id = "Position_.list")
@ViewDescriptor(path = "position-list-view.xml")
@LookupComponent("positionsDataGrid")
@DialogMode(width = "64em")
public class PositionListView extends StandardListView<Position> {
}