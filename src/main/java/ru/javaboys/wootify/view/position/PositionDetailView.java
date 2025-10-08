package ru.javaboys.wootify.view.position;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.wootify.entity.Position;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "positions/:id", layout = MainView.class)
@ViewController(id = "Position_.detail")
@ViewDescriptor(path = "position-detail-view.xml")
@EditedEntityContainer("positionDc")
public class PositionDetailView extends StandardDetailView<Position> {
}