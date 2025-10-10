package ru.javaboys.wootify.view.trader;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.wootify.entity.*;
import ru.javaboys.wootify.service.AssetsService;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "trader-view", layout = MainView.class)
@ViewController(id = "TraderView")
@ViewDescriptor(path = "trader-view.xml")
public class TraderView extends StandardView {

    @ViewComponent
    private EntityPicker<Account> accountEntityPicker;
    @ViewComponent
    private EntityPicker<ApiKey> apiKeyEntityPicker;
    @ViewComponent
    private TextField myAssets;
    @ViewComponent
    private JmixButton assetsRefreshButton;

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
    @Autowired
    AssetsService assetsService;

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
            submitBtn.setText("Buy / Long");
            submitBtn.removeClassNames("sell");
            submitBtn.addClassNames("buy");
        } else {
            submitBtn.setText("Sell / Short");
            submitBtn.removeClassNames("buy");
            submitBtn.addClassNames("sell");
        }

        updatePriceEditable();
    }

    @Subscribe("assetsRefreshButton")
    public void onAssetsRefreshButtonClick(ClickEvent<JmixButton> event) {
        if (!checkAccountAndApiKeyFieldsFilling()) {
            return;
        }

        Double usdcBalance = assetsService.getCurrentAssetsInUSDC(
                accountEntityPicker.getValue(), apiKeyEntityPicker.getValue());
        myAssets.setValue(String.format("%.2f", usdcBalance));

        notifications.create("Assets refreshed successfully")
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

    public boolean checkAccountAndApiKeyFieldsFilling() {
        boolean result = true;
        if (accountEntityPicker.getValue() == null) {
            notifications.create("Account field isn't filled")
                    .withType(Notifications.Type.WARNING)
                    .show();
            result = false;
        }

        if (apiKeyEntityPicker.getValue() == null) {
            notifications.create("Api Key field isn't filled")
                    .withType(Notifications.Type.WARNING)
                    .show();
            result = false;
        }

        return result;
    }

}