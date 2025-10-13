package ru.javaboys.wootify.view.trader;

import java.math.BigDecimal;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.Route;

import io.jmix.core.Metadata;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.wootify.dto.trade.CurrentAccountState;
import ru.javaboys.wootify.dto.trade.CurrentDealState;
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.Position;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.orderly.client.OrderlyStreamingClient;
import ru.javaboys.wootify.service.AssetsService;
import ru.javaboys.wootify.service.OrderService;
import ru.javaboys.wootify.service.PositionService;
import ru.javaboys.wootify.service.TradingTerminalService;
import ru.javaboys.wootify.view.main.MainView;

@Slf4j
@Route(value = "trader-view", layout = MainView.class)
@ViewController(id = "TraderView")
@ViewDescriptor(path = "trader-view.xml")
public class TraderView extends StandardView {

    @ViewComponent private EntityPicker<Account> accountEntityPicker;
    @ViewComponent private EntityPicker<ApiKey> apiKeyEntityPicker;
    @ViewComponent private EntityPicker<Symbol> symbolEntityPicker;

    @ViewComponent private JmixComboBox<OrderType> orderTypeCombo;
    @ViewComponent private TextField myAssets;

    @ViewComponent private NumberField leverage;
    @ViewComponent private BigDecimalField priceField;
    @ViewComponent private BigDecimalField qtyField;
    @ViewComponent private BigDecimalField totalField;
    @ViewComponent private BigDecimalField bidPrice;
    @ViewComponent private BigDecimalField avgPrice;
    @ViewComponent private BigDecimalField askPrice;

    @ViewComponent private CollectionLoader<Order> ordersDl;
    @ViewComponent private CollectionContainer<Order> ordersDc;

    @ViewComponent private CollectionLoader<Position> positionsDl;
    @ViewComponent private CollectionContainer<Position> positionsDc;

    @Autowired private TradingTerminalService tradingTerminalService;
    @Autowired private Notifications notifications;
    @Autowired private AssetsService assetsService;
    @Autowired private PositionService positionService;
    @Autowired private OrderService orderService;
    @Autowired private ViewNavigators viewNavigators;
    @Autowired private Metadata metadata;
    @Value("${orderly.account-id}") private String accountId;

    private OrderlyStreamingClient streamingClient;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ApiKey apiKey = apiKeyEntityPicker.getValue();
        Account account = accountEntityPicker.getValue();
        Symbol symbol = symbolEntityPicker.getValue();

        positionsDl.setParameter("apiKey", apiKey);
        positionsDl.setParameter("account", account);
        positionsDl.setParameter("symbol", symbol);
        ordersDl.setParameter("position", null);

        positionsDl.load();
        ordersDl.load();
    }

    @Subscribe
    public void onInit(InitEvent event) {
        orderTypeCombo.setItems(OrderType.values());
        orderTypeCombo.setValue(OrderType.MARKET);
    }

    @Subscribe("positionsTable")
    public void onPositionsTableSelection(final SelectionEvent<DataGrid<Position>, Position> event) {
        Position selected = event.getFirstSelectedItem().orElse(null);
        if (selected != null) {
            ordersDl.setParameter("position", selected);
            ordersDl.load();
        } else {
            ordersDc.setItems(Collections.emptyList());
        }
    }

    @Subscribe("apiKeyEntityPicker")
    public void onApiKeyEntityPickerValueChange(AbstractField.ComponentValueChangeEvent<EntityPicker<ApiKey>, ApiKey> event) {
        reloadPositions();
    }

    @Subscribe("accountEntityPicker")
    public void onAccountEntityPickerValueChange(AbstractField.ComponentValueChangeEvent<EntityPicker<Account>, Account> event) {
        reloadPositions();
    }

    @Subscribe("symbolEntityPicker")
    public void onSymbolEntityPickerValueChange(AbstractField.ComponentValueChangeEvent<EntityPicker<Symbol>, Symbol> event) {
        reloadPositions();
    }

    private void updatePriceEditable() {
        boolean isLimit = orderTypeCombo.getValue() == OrderType.LIMIT;
        priceField.setReadOnly(!isLimit);
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

    @Subscribe("checkLeverageButton")
    public void onCheckLeverageButton(ClickEvent<JmixButton> event) {
        Double currentLeverage = leverage.getValue();
        Double leverageValue = positionService.getLeverageForTicker(
                getCurrentAccountState(),
                getCurrentDealState()
        );

        if (leverageValue.equals(currentLeverage)) {
            notifications.create("That's ok")
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } else {
            notifications.create("Current leverage" + currentLeverage + ", Server leverage " + leverageValue)
                    .withType(Notifications.Type.WARNING)
                    .show();
        }
    }

    @Subscribe("setLeverageButton")
    public void onSetLeverageButton(ClickEvent<JmixButton> event) {
        Double leverageBefore = leverage.getValue();
        Double leverageValue = positionService.setLeverageForTicker(
                getCurrentAccountState(),
                getCurrentDealState()
        );
        leverage.setValue(leverageValue);

        if (leverageValue.equals(leverageBefore)) {
            notifications.create("Leverage set successfully")
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } else {
            notifications.create("Leverage set not successfully")
                    .withType(Notifications.Type.WARNING)
                    .show();
        }
    }

    @Subscribe("buyButton")
    public void onBuyButtonClick(ClickEvent<JmixButton> event) {
        onTradeButtonClick(OrderSide.BUY);
    }

    @Subscribe("sellButton")
    public void onSellButtonClick(ClickEvent<JmixButton> event) {
        onTradeButtonClick(OrderSide.SELL);
    }

    private void onTradeButtonClick(OrderSide orderSide) {
        CurrentDealState currentDealState = getCurrentDealState();
        currentDealState.setOrderSide(orderSide);
        tradingTerminalService.submitOrder(getCurrentAccountState(), currentDealState);

        notifications.create("Ордер создан и отправлен").show();

        //Изменения на форме
    }

    private CurrentAccountState getCurrentAccountState() {
        return CurrentAccountState.builder()
                .account(accountEntityPicker.getValue())
                .apiKey(apiKeyEntityPicker.getValue())
                .build();
    }

    private CurrentDealState getCurrentDealState() {
        return CurrentDealState.builder()
                .symbol(symbolEntityPicker.getValue())
                .orderSide(null)
                .orderType(orderTypeCombo.getValue())
                .leverage(leverage.getValue())
                .price(priceField.getValue())
                .quantity(qtyField.getValue())
                .build();
    }

    private void reloadPositions() {
        positionsDl.setParameter("symbol", symbolEntityPicker.getValue());
        positionsDl.setParameter("account", accountEntityPicker.getValue());
        positionsDl.setParameter("apiKey", apiKeyEntityPicker.getValue());
        positionsDl.load();
    }

    @Supply(to = "positionsTable.close", subject = "renderer")
    protected Renderer<Position> positionCancelRenderer() {
        return new ComponentRenderer<>(position -> {
            HorizontalLayout layout = new HorizontalLayout();
            Button closeBtn = new Button("Close Market", e -> onClosePosition(position));
            layout.add(closeBtn);
            return layout;
        });
    }

    @Supply(to = "positionsTable.update", subject = "renderer")
    protected Renderer<Position> positionUpdateRenderer() {
        return new ComponentRenderer<>(position -> {
            HorizontalLayout layout = new HorizontalLayout();
            Button updateBtn = new Button("Update", e -> onUpdatePosition(position));
            layout.add(updateBtn);
            return layout;
        });
    }

    @Supply(to = "ordersTable.cancel", subject = "renderer")
    protected Renderer<Order> ordersTableCancelRenderer() {
        return new ComponentRenderer<>(order -> {
            HorizontalLayout layout = new HorizontalLayout();
            Button cancelBtn = new Button("Cancel", e -> onCancelOrder(order));
            layout.add(cancelBtn);
            return layout;
        });
    }

    @Supply(to = "ordersTable.update", subject = "renderer")
    protected Renderer<Order> ordersTableUpdateRenderer() {
        return new ComponentRenderer<>(order -> {
            HorizontalLayout layout = new HorizontalLayout();
            Button updateBtn = new Button("Update", e -> onUpdateOrder(order));
            layout.add(updateBtn);
            return layout;
        });
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

    private void onClosePosition(Position position) {
        positionService.closePosition(position);
        notifications.create("Закрытие позиции: " + getSymbolSafe(position)).show();
    }

    private void onUpdatePosition(Position position) {
        positionService.updatePositionInfo(position);
        notifications.create("Обновление позиции: " + getSymbolSafe(position))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

    private void onCancelOrder(Order order) {
        if (order.getType().equals(OrderType.MARKET)) {
            notifications.create("Отмена ордера c типом MARKET невозможна!")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }

        orderService.cancelOrder(getCurrentAccountState(), order);
        notifications.create("Отмена ордера: " + getSymbolSafe(order))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

    private void onUpdateOrder(Order order) {
        orderService.updateOrderStatus(getCurrentAccountState(), order);
        notifications.create("Статус ордера обновлен: " + getSymbolSafe(order))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

    private String getSymbolSafe(Position position) {
        return position.getSymbol() != null ? position.getSymbol().getName() : "(нет символа)";
    }

    private String getSymbolSafe(Order order) {
        return order.getSymbol() != null ? order.getSymbol().getName() : "(нет символа)";
    }
}