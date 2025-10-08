package ru.javaboys.wootify.view.symbol;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "symbols", layout = MainView.class)
@ViewController(id = "Symbol.list")
@ViewDescriptor(path = "symbol-list-view.xml")
@LookupComponent("symbolsDataGrid")
@DialogMode(width = "64em")
public class SymbolListView extends StandardListView<Symbol> {
}