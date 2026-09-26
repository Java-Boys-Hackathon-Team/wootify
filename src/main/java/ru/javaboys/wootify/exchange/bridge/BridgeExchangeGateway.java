package ru.javaboys.wootify.exchange.bridge;

import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.exchange.ExchangeException;
import ru.javaboys.wootify.exchange.ExchangeGateway;
import ru.javaboys.wootify.exchange.ExchangeOrder;
import ru.javaboys.wootify.exchange.ExchangeOrderStatus;
import ru.javaboys.wootify.exchange.MarketSymbol;
import ru.javaboys.wootify.exchange.PlaceOrderCommand;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Реальная торговля на WOOFi Pro через woofipro-bridge с ключами API конкретного аккаунта.
 */
public class BridgeExchangeGateway implements ExchangeGateway {

    /**
     * Учётные данные Orderly; {@code env} - {@code mainnet} или {@code testnet}.
     */
    public record Credentials(String apiKey, String apiSecret, String accountId, String brokerId, String env) {
        @Override
        public String toString() {
            return "Credentials[accountId=" + accountId + ", env=" + env + "]";
        }
    }

    private final BridgeTradingClient client;
    private final Credentials c;

    public BridgeExchangeGateway(BridgeTradingClient client, Credentials credentials) {
        this.client = client;
        this.c = credentials;
    }

    @Override
    public ExchangeOrder placeOrder(PlaceOrderCommand cmd) {
        BridgeOrderRequest request = new BridgeOrderRequest(
                cmd.symbol().ccxt(),
                cmd.side().getId().toLowerCase(),
                cmd.type().getId().toLowerCase(),
                cmd.quantity().toPlainString(),
                cmd.type() == OrderType.LIMIT ? cmd.price().toPlainString() : null,
                cmd.clientOrderId(),
                cmd.reduceOnly());
        try {
            return toExchangeOrder(invoke(() -> client.createOrder(c.apiKey(), c.apiSecret(), c.accountId(),
                    c.brokerId(), c.env(), request)), cmd.clientOrderId());
        } catch (ExchangeException e) {
            if (e.getKind() == ExchangeException.Kind.REJECTED) {
                return new ExchangeOrder(null, cmd.clientOrderId(), ExchangeOrderStatus.REJECTED,
                        BigDecimal.ZERO, null, BigDecimal.ZERO, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public Optional<ExchangeOrder> findOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId) {
        try {
            BridgeOrder order = clientOrderId != null
                    ? invoke(() -> client.getOrderByClientId(c.apiKey(), c.apiSecret(), c.accountId(), c.brokerId(),
                    c.env(), clientOrderId, symbol.ccxt()))
                    : invoke(() -> client.getOrder(c.apiKey(), c.apiSecret(), c.accountId(), c.brokerId(), c.env(),
                    exchangeOrderId, symbol.ccxt()));
            return Optional.of(toExchangeOrder(order, clientOrderId));
        } catch (ExchangeException e) {
            if (e.getKind() == ExchangeException.Kind.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void cancelOrder(MarketSymbol symbol, String clientOrderId, String exchangeOrderId) {
        invoke(() -> client.cancelOrderByClientId(c.apiKey(), c.apiSecret(), c.accountId(), c.brokerId(), c.env(),
                clientOrderId, symbol.ccxt()));
    }

    @Override
    public void setLeverage(MarketSymbol symbol, int leverage) {
        invoke(() -> client.setLeverage(c.apiKey(), c.apiSecret(), c.accountId(), c.brokerId(), c.env(),
                symbol.ccxt(), Map.of("leverage", leverage)));
    }

    private static <T> T invoke(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            throw BridgeFeignConfig.translate(e);
        }
    }

    static ExchangeOrder toExchangeOrder(BridgeOrder o, String clientOrderId) {
        ExchangeOrderStatus status = switch (o.status() == null ? "" : o.status()) {
            case "closed" -> ExchangeOrderStatus.FILLED;
            case "canceled", "expired" -> ExchangeOrderStatus.CANCELLED;
            case "rejected" -> ExchangeOrderStatus.REJECTED;
            default -> ExchangeOrderStatus.OPEN;
        };
        return new ExchangeOrder(o.id(), o.clientOrderId() != null ? o.clientOrderId() : clientOrderId, status,
                o.filled() != null ? o.filled() : BigDecimal.ZERO,
                o.average() != null && o.average().signum() > 0 ? o.average() : null,
                o.fee() != null ? o.fee().abs() : BigDecimal.ZERO,
                status == ExchangeOrderStatus.REJECTED ? "Биржа отклонила ордер (" + o.rawStatus() + ")" : null);
    }
}
