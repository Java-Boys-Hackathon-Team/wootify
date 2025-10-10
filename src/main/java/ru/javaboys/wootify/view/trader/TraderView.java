package ru.javaboys.wootify.view.trader;


import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "trader-view", layout = MainView.class)
@ViewController(id = "TraderView")
@ViewDescriptor(path = "trader-view.xml")
public class TraderView extends StandardView {

    @ViewComponent
    private EntityPicker<Symbol> symbolEntityPicker;

    @ViewComponent
    private JmixButton buyBtn;
    @ViewComponent
    private JmixButton sellBtn;
    @ViewComponent
    private JmixButton submitBtn;

    @ViewComponent
    private ComboBox<OrderType> orderTypeCombo;
    @ViewComponent
    private NumberField leverage;
    @ViewComponent
    private BigDecimalField priceField;
    @ViewComponent
    private BigDecimalField qtyField;
    @ViewComponent
    private BigDecimalField totalField;


    @Autowired
    private Notifications notifications;

    private OrderSide orderSide = OrderSide.BUY;

    @Subscribe
    public void onInit(InitEvent event) {
        orderTypeCombo.setItems(OrderType.values());
        orderTypeCombo.setValue(OrderType.LIMIT);

        buyBtn.addClickListener(e -> {
            orderSide = OrderSide.BUY;
            updateUI();
        });

        sellBtn.addClickListener(e -> {
            orderSide = OrderSide.SELL;
            updateUI();
        });

        orderTypeCombo.addValueChangeListener(e -> updatePriceEditable());
    }

    private void updatePriceEditable() {
        boolean isLimit = orderTypeCombo.getValue() == OrderType.LIMIT;
        priceField.setReadOnly(!isLimit);
    }

    private void updateUI() {
        boolean isBuy = orderSide == OrderSide.BUY;

        // визуально показываем активную сторону
        buyBtn.setEnabled(!isBuy);
        sellBtn.setEnabled(isBuy);

        if (isBuy) {
            submitBtn.setText("Long");
            submitBtn.removeClassNames("sell");
            submitBtn.addClassNames("buy");
        } else {
            submitBtn.setText("Short");
            submitBtn.removeClassNames("buy");
            submitBtn.addClassNames("sell");
        }

        updatePriceEditable();
    }



//    @Subscribe
//    public void onInit(final View.InitEvent event) {
//        // Создаём временный экземпляр Symbol или можете оставить символ пустым (null)
//        Symbol tempSymbol = metadata.create(Symbol.class);
//        symbolDc.setItem(tempSymbol);
//    }
}