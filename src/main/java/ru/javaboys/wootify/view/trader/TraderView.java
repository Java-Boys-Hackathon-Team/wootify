package ru.javaboys.wootify.view.trader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderStatus;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.Position;
import ru.javaboys.wootify.entity.PositionStatus;
import ru.javaboys.wootify.entity.Symbol;
import ru.javaboys.wootify.orderly.client.OrderlyStreamingClient;
import ru.javaboys.wootify.service.AssetsService;
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
    @ViewComponent private JmixButton assetsRefreshButton;
    @ViewComponent private TextField myAssets;

    @ViewComponent private JmixButton buyBtn;
    @ViewComponent private JmixButton sellBtn;
    @ViewComponent private JmixButton submitBtn;
    @ViewComponent private JmixButton createPositionBtn;
    @ViewComponent private JmixButton createOrderBtn;

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
    @Autowired private ViewNavigators viewNavigators;
    @Autowired private Metadata metadata;
    @Value("${orderly.account-id}") private String accountId;

    private OrderlyStreamingClient streamingClient;
    private OrderSide orderSide = OrderSide.BUY;

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

        createPositionBtn.addClickListener(this::onCreatePositionClick);
        createOrderBtn.addClickListener(this::onCreateOrderClick);
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

    @Subscribe("submitBtn")
    public void onSubmitButtonClick(ClickEvent<JmixButton> event) {

        tradingTerminalService.submitOrder(
                accountEntityPicker.getValue(),
                apiKeyEntityPicker.getValue(),
                symbolEntityPicker.getValue(),
                null,
                orderTypeCombo.getValue(),
                leverage.getValue(),
                priceField.getValue(),
                qtyField.getValue()
        );



    }

    private void reloadPositions() {
        positionsDl.setParameter("symbol", symbolEntityPicker.getValue());
        positionsDl.setParameter("account", accountEntityPicker.getValue());
        positionsDl.setParameter("apiKey", apiKeyEntityPicker.getValue());
        positionsDl.load();
    }

    private void onCreatePositionClick(ClickEvent<Button> event) {
        Position newPosition = metadata.create(Position.class);

        newPosition.setApiKey(apiKeyEntityPicker.getValue());
        newPosition.setAccount(accountEntityPicker.getValue());
        newPosition.setSymbol(symbolEntityPicker.getValue());

        newPosition.setStatus(PositionStatus.CREATED);
        newPosition.setCreatedDate(LocalDateTime.now());

        if (leverage.getValue() != null) {
            newPosition.setLeverage(leverage.getValue());
        }
        if (qtyField.getValue() != null) {
            newPosition.setPositionQty(qtyField.getValue());
        }
        if (priceField.getValue() != null) {
            newPosition.setAverageOpenPrice(priceField.getValue());
        }

        newPosition.setRealizedPnl(BigDecimal.ZERO);

        // Переход на форму редактирования с новым объектом
        viewNavigators.detailView(this, Position.class)
                .newEntity()
                .withBackwardNavigation(true)
                .navigate();
    }

    private void onCreateOrderClick(ClickEvent<Button> event) {
        Position selectedPosition = positionsDc.getItem();
        if (selectedPosition == null) {
            notifications.create("Выберите позицию для создания ордера")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        Order newOrder = metadata.create(Order.class);

        newOrder.setPosition(selectedPosition);
        newOrder.setAccount(selectedPosition.getAccount());
        newOrder.setApiKey(selectedPosition.getApiKey());
        newOrder.setSymbol(selectedPosition.getSymbol());
        newOrder.setCreatedDate(LocalDateTime.now());

        newOrder.setSide(orderSide);
        newOrder.setType(orderTypeCombo.getValue());
        newOrder.setStatus(OrderStatus.CREATED);

        // Остальные поля будут заполняться в форме
        viewNavigators.detailView(this, Order.class)
                .newEntity()
                .withBackwardNavigation(true)
                .navigate();
    }

    @Supply(to = "positionsTable.actions", subject = "renderer")
    protected Renderer<Position> positionActionsRenderer() {
        return new ComponentRenderer<>(position -> {
            HorizontalLayout layout = new HorizontalLayout();
            Button cancelBtn = new Button("Cancel", e -> onCancelPosition(position));
            Button infoBtn = new Button("Info", e -> onInfoPosition(position));
            layout.add(cancelBtn, infoBtn);
            return layout;
        });
    }

    @Supply(to = "ordersTable.actions", subject = "renderer")
    protected Renderer<Order> ordersTableActionsRenderer() {
        return new ComponentRenderer<>(order -> {
            HorizontalLayout layout = new HorizontalLayout();
            Button cancelBtn = new Button("Cancel", e -> onCancelOrder(order));
            Button infoBtn = new Button("Info", e -> onInfoOrder(order));
            layout.add(cancelBtn, infoBtn);
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

    private void onCancelPosition(Position position) {
        // todo: логика отмены позиции
        notifications.create("Отмена позиции: " + getSymbolSafe(position)).show();
    }

    private void onInfoPosition(Position position) {
        notifications.create("Инфо по позиции: " + getSymbolSafe(position)).show();
    }

    private void onCancelOrder(Order order) {
        // todo: логика отмены ордера
        notifications.create("Отмена ордера: " + getSymbolSafe(order)).show();
    }

    private void onInfoOrder(Order order) {
        notifications.create("Инфо по ордеру: " + getSymbolSafe(order)).show();
    }

    private String getSymbolSafe(Position position) {
        return position.getSymbol() != null ? position.getSymbol().getName() : "(нет символа)";
    }

    private String getSymbolSafe(Order order) {
        return order.getSymbol() != null ? order.getSymbol().getName() : "(нет символа)";
    }
}