package ru.javaboys.wootify.view.symbol;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "symbols/:id", layout = MainView.class)
@ViewController(id = "Symbol.detail")
@ViewDescriptor(path = "symbol-detail-view.xml")
@EditedEntityContainer("symbolDc")
public class SymbolDetailView extends StandardDetailView<Symbol> {
}