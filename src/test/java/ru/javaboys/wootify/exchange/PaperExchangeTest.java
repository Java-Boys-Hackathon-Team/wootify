package ru.javaboys.wootify.exchange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.exchange.marketdata.MarketDataUnavailableException;
import ru.javaboys.wootify.exchange.paper.PaperExchange;
import ru.javaboys.wootify.test_support.IntegrationTest;
import ru.javaboys.wootify.test_support.ManualMarketData;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PaperExchangeTest {

    private static final MarketSymbol SYMBOL = new MarketSymbol("PERP_TEST_USDC", "TEST/USDC:USDC");

    @Autowired
    PaperExchange exchange;
    @Autowired
    ManualMarketData marketData;

    @BeforeEach
    void setUp() {
        marketData.set(Network.MAINNET, SYMBOL.orderly(), "99.9", "100.1");
    }

    private PlaceOrderCommand cmd(OrderSide side, OrderType type, String qty, String price) {
        return new PlaceOrderCommand(SYMBOL, "t-" + UUID.randomUUID(), side, type, new BigDecimal(qty),
                price == null ? null : new BigDecimal(price), false);
    }

    @Test
    void marketOrderFillsAtOppositeBestPriceWithTakerFee() {
        ExchangeOrder buy = exchange.place(Network.MAINNET, cmd(OrderSide.BUY, OrderType.MARKET, "2", null));
        assertThat(buy.status()).isEqualTo(ExchangeOrderStatus.FILLED);
        assertThat(buy.avgPrice()).isEqualByComparingTo("100.1");
        assertThat(buy.filledQty()).isEqualByComparingTo("2");
        // 2 * 100.1 * 0.0005
        assertThat(buy.fee()).isEqualByComparingTo("0.1001");

        ExchangeOrder sell = exchange.place(Network.MAINNET, cmd(OrderSide.SELL, OrderType.MARKET, "1", null));
        assertThat(sell.avgPrice()).isEqualByComparingTo("99.9");
    }

    @Test
    void restingLimitFillsWhenPriceReachesItWithMakerFee() {
        PlaceOrderCommand command = cmd(OrderSide.BUY, OrderType.LIMIT, "1", "95");
        ExchangeOrder placed = exchange.place(Network.MAINNET, command);
        assertThat(placed.status()).isEqualTo(ExchangeOrderStatus.OPEN);

        exchange.matchRestingOrders();
        assertThat(exchange.find(command.clientOrderId(), null)).get()
                .extracting(ExchangeOrder::status).isEqualTo(ExchangeOrderStatus.OPEN);

        marketData.set(Network.MAINNET, SYMBOL.orderly(), "94.8", "94.9");
        exchange.matchRestingOrders();
        ExchangeOrder filled = exchange.find(command.clientOrderId(), null).orElseThrow();
        assertThat(filled.status()).isEqualTo(ExchangeOrderStatus.FILLED);
        assertThat(filled.avgPrice()).isEqualByComparingTo("95");
        assertThat(filled.fee()).isEqualByComparingTo("0.019");
    }

    @Test
    void marketableLimitFillsImmediately() {
        ExchangeOrder sell = exchange.place(Network.MAINNET, cmd(OrderSide.SELL, OrderType.LIMIT, "1", "99"));
        assertThat(sell.status()).isEqualTo(ExchangeOrderStatus.FILLED);
        assertThat(sell.avgPrice()).isEqualByComparingTo("99.9");
    }

    @Test
    void placementIsIdempotentByClientOrderId() {
        PlaceOrderCommand command = cmd(OrderSide.BUY, OrderType.LIMIT, "1", "90");
        ExchangeOrder first = exchange.place(Network.MAINNET, command);
        ExchangeOrder second = exchange.place(Network.MAINNET, command);
        assertThat(second.exchangeOrderId()).isEqualTo(first.exchangeOrderId());
    }

    @Test
    void cancelIsIdempotentAndDoesNotAffectFilledOrders() {
        PlaceOrderCommand resting = cmd(OrderSide.BUY, OrderType.LIMIT, "1", "90");
        exchange.place(Network.MAINNET, resting);
        exchange.cancel(resting.clientOrderId());
        exchange.cancel(resting.clientOrderId());
        assertThat(exchange.find(resting.clientOrderId(), null).orElseThrow().status())
                .isEqualTo(ExchangeOrderStatus.CANCELLED);

        PlaceOrderCommand market = cmd(OrderSide.BUY, OrderType.MARKET, "1", null);
        exchange.place(Network.MAINNET, market);
        exchange.cancel(market.clientOrderId());
        assertThat(exchange.find(market.clientOrderId(), null).orElseThrow().status())
                .isEqualTo(ExchangeOrderStatus.FILLED);

        assertThatThrownBy(() -> exchange.cancel("unknown-" + UUID.randomUUID()))
                .isInstanceOf(ExchangeException.class)
                .extracting(e -> ((ExchangeException) e).getKind()).isEqualTo(ExchangeException.Kind.NOT_FOUND);
    }

    @Test
    void rejectsInvalidOrdersAndRequiresFreshPrices() {
        ExchangeOrder rejected = exchange.place(Network.MAINNET, cmd(OrderSide.BUY, OrderType.LIMIT, "0", "90"));
        assertThat(rejected.status()).isEqualTo(ExchangeOrderStatus.REJECTED);
        assertThat(rejected.rejectReason()).isNotBlank();

        marketData.remove(Network.MAINNET, SYMBOL.orderly());
        assertThatThrownBy(() -> exchange.place(Network.MAINNET, cmd(OrderSide.BUY, OrderType.MARKET, "1", null)))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
