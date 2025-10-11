package ru.javaboys.wootify.view.trader;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;

import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.orderly.client.OrderlyStreamingClient;
import ru.javaboys.wootify.view.main.MainView;

@Slf4j
@Route(value = "trader-view", layout = MainView.class)
@ViewController(id = "TraderView")
@ViewDescriptor(path = "trader-view.xml")
public class TraderView extends StandardView {

    @ViewComponent private EntityPicker<Symbol> symbolEntityPicker;
    @ViewComponent private JmixButton buyBtn;
    @ViewComponent private JmixButton sellBtn;
    @ViewComponent private JmixButton submitBtn;
    @ViewComponent private ComboBox<OrderType> orderTypeCombo;
    @ViewComponent private NumberField leverage;
    @ViewComponent private BigDecimalField priceField;
    @ViewComponent private BigDecimalField qtyField;
    @ViewComponent private BigDecimalField totalField;
    @ViewComponent private BigDecimalField bidPrice;
    @ViewComponent private BigDecimalField avgPrice;
    @ViewComponent private BigDecimalField askPrice;

    @Value("${orderly.account-id}") private String accountId;
    @Autowired private Notifications notifications;

    private OrderlyStreamingClient streamingClient;
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


    @Subscribe("symbolEntityPicker")
    private void onSymbolEntityPickerChange(AbstractField.ComponentValueChangeEvent<EntityPicker<Symbol>, Symbol> event) {
        Symbol oldSymbol = event.getOldValue();
        Symbol symbol = event.getValue();
        if (oldSymbol != null && oldSymbol.equals(symbol)) {
            log.info("New symbol is the same, skip change price streaming, old: {}, new: {}",
                    oldSymbol.getWoofiTicker(), symbol.getWoofiTicker());
            return;
        }

        unsubscribeIfNeed();

        if (symbol != null) {
            streamingClient = new OrderlyStreamingClient(accountId, symbol.getWoofiTicker(), data -> {
                try {
                    getUI().get().access(() -> {
                        bidPrice.setValue(data.getData().getBid());
                        avgPrice.setValue(BigDecimal.ZERO);
                        askPrice.setValue(data.getData().getAsk());
                    });
                } catch (UIDetachedException e) {
                    log.warn("UIDetachedException while updating bid/avg/ask prices");
                    unsubscribeIfNeed();
                }
            });
        }
    }

    private void unsubscribeIfNeed() {
        if (streamingClient != null) {
            streamingClient.unsubscribeAndWait();
            bidPrice.clear();
            avgPrice.clear();
            askPrice.clear();
        }
    }

}