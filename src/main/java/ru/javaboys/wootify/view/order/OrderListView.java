package ru.javaboys.wootify.view.order;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "orders", layout = MainView.class)
@ViewController(id = "Order_.list")
@ViewDescriptor(path = "order-list-view.xml")
@LookupComponent("ordersDataGrid")
@DialogMode(width = "64em")
public class OrderListView extends StandardListView<Order> {
}