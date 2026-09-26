package ru.javaboys.wootify.exchange.paper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import ru.javaboys.wootify.entity.Network;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.PaperOrderStatus;
import ru.javaboys.wootify.exchange.ExchangeException;
import ru.javaboys.wootify.exchange.ExchangeOrder;
import ru.javaboys.wootify.exchange.ExchangeOrderStatus;
import ru.javaboys.wootify.exchange.PlaceOrderCommand;
import ru.javaboys.wootify.exchange.marketdata.MarketDataService;
import ru.javaboys.wootify.exchange.marketdata.MarketDataUnavailableException;
import ru.javaboys.wootify.exchange.marketdata.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Симулятор биржи для бумажной торговли на живых ценах.
 * <p>
 * Рыночный ордер исполняется сразу по лучшей встречной цене (комиссия тейкера). Лимитный ордер,
 * пересекающий рынок, тоже исполняется сразу по встречной цене; остальные ждут, пока цена до них
 * дойдёт, и исполняются по своей цене с комиссией мейкера. Ордер исполняется целиком.
 * Проскальзывание и глубина стакана не моделируются, признак reduce-only не проверяется.
 * <p>
 * Состояние хранится в таблице PAPER_ORDER, поэтому переживает перезапуск приложения.
 * Переходы статуса выполняются условным UPDATE, что исключает гонку исполнения и отмены.
 */
public class PaperExchange {

    private static final Logger log = LoggerFactory.getLogger(PaperExchange.class);
    private static final int FEE_SCALE = 8;

    private final JdbcTemplate jdbc;
    private final MarketDataService marketData;
    private final PaperTradingProperties properties;
    private final Clock clock;

    private final RowMapper<Row> rowMapper = (rs, n) -> new Row(
            rs.getObject("ID", UUID.class),
            rs.getString("CLIENT_ORDER_ID"),
            Network.fromId(rs.getString("NETWORK")),
            rs.getString("SYMBOL"),
            OrderSide.fromId(rs.getString("SIDE")),
            OrderType.fromId(rs.getString("TYPE_")),
            rs.getBigDecimal("PRICE"),
            rs.getBigDecimal("QUANTITY"),
            PaperOrderStatus.fromId(rs.getString("STATUS")),
            rs.getBigDecimal("FILLED_QTY"),
            rs.getBigDecimal("AVG_PRICE"),
            rs.getBigDecimal("FEE"),
            rs.getString("REJECT_REASON"));

    record Row(UUID id, String clientOrderId, Network network, String symbol, OrderSide side, OrderType type,
               BigDecimal price, BigDecimal quantity, PaperOrderStatus status, BigDecimal filledQty,
               BigDecimal avgPrice, BigDecimal fee, String rejectReason) {
    }

    public PaperExchange(JdbcTemplate jdbc, MarketDataService marketData, PaperTradingProperties properties,
                         Clock clock) {
        this.jdbc = jdbc;
        this.marketData = marketData;
        this.properties = properties;
        this.clock = clock;
    }

    public ExchangeOrder place(Network network, PlaceOrderCommand cmd) {
        Optional<Row> existing = findRow(cmd.clientOrderId());
        if (existing.isPresent()) {
            return toExchangeOrder(existing.get());
        }
        String symbol = cmd.symbol().orderly();
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);

        String rejectReason = validate(cmd);
        if (rejectReason != null) {
            insert(id, network, cmd, PaperOrderStatus.REJECTED, BigDecimal.ZERO, null, BigDecimal.ZERO, rejectReason, now);
            return toExchangeOrder(findRow(cmd.clientOrderId()).orElseThrow());
        }

        Quote quote = marketData.freshQuote(network, symbol);
        BigDecimal takerPrice = cmd.side() == OrderSide.BUY ? quote.ask() : quote.bid();
        boolean immediate = cmd.type() == OrderType.MARKET
                || (cmd.side() == OrderSide.BUY && cmd.price().compareTo(quote.ask()) >= 0)
                || (cmd.side() == OrderSide.SELL && cmd.price().compareTo(quote.bid()) <= 0);

        if (immediate) {
            BigDecimal fee = fee(cmd.quantity(), takerPrice, properties.takerFeeRate());
            insert(id, network, cmd, PaperOrderStatus.FILLED, cmd.quantity(), takerPrice, fee, null, now);
        } else {
            insert(id, network, cmd, PaperOrderStatus.NEW, BigDecimal.ZERO, null, BigDecimal.ZERO, null, now);
        }
        return toExchangeOrder(findRow(cmd.clientOrderId()).orElseThrow());
    }

    public Optional<ExchangeOrder> find(String clientOrderId, String exchangeOrderId) {
        Optional<Row> row = clientOrderId != null ? findRow(clientOrderId) : Optional.empty();
        if (row.isEmpty() && exchangeOrderId != null) {
            row = jdbc.query("SELECT * FROM PAPER_ORDER WHERE ID = ?", rowMapper, UUID.fromString(exchangeOrderId))
                    .stream().findFirst();
        }
        return row.map(this::toExchangeOrder);
    }

    public void cancel(String clientOrderId) {
        int updated = jdbc.update("UPDATE PAPER_ORDER SET STATUS = ?, UPDATED_AT = ? WHERE CLIENT_ORDER_ID = ? AND STATUS = ?",
                PaperOrderStatus.CANCELLED.getId(), OffsetDateTime.now(clock), clientOrderId, PaperOrderStatus.NEW.getId());
        if (updated == 0 && findRow(clientOrderId).isEmpty()) {
            throw new ExchangeException(ExchangeException.Kind.NOT_FOUND, "Ордер " + clientOrderId + " не найден");
        }
    }

    /**
     * Исполняет ожидающие лимитные ордера, до которых дошла цена.
     */
    @Scheduled(fixedDelayString = "${wootify.paper.match-interval-ms:1000}")
    public void matchRestingOrders() {
        List<Row> resting = jdbc.query("SELECT * FROM PAPER_ORDER WHERE STATUS = ? ORDER BY CREATED_AT",
                rowMapper, PaperOrderStatus.NEW.getId());
        for (Row row : resting) {
            Quote quote;
            try {
                quote = marketData.freshQuote(row.network(), row.symbol());
            } catch (MarketDataUnavailableException e) {
                continue;
            }
            boolean touched = row.side() == OrderSide.BUY
                    ? quote.ask().compareTo(row.price()) <= 0
                    : quote.bid().compareTo(row.price()) >= 0;
            if (touched) {
                BigDecimal fee = fee(row.quantity(), row.price(), properties.makerFeeRate());
                int updated = jdbc.update("""
                                UPDATE PAPER_ORDER SET STATUS = ?, FILLED_QTY = QUANTITY, AVG_PRICE = PRICE, FEE = ?, UPDATED_AT = ?
                                WHERE ID = ? AND STATUS = ?""",
                        PaperOrderStatus.FILLED.getId(), fee, OffsetDateTime.now(clock), row.id(), PaperOrderStatus.NEW.getId());
                if (updated == 1) {
                    log.debug("Paper order {} filled at {}", row.clientOrderId(), row.price());
                }
            }
        }
    }

    private static String validate(PlaceOrderCommand cmd) {
        if (cmd.quantity() == null || cmd.quantity().signum() <= 0) {
            return "Объём ордера должен быть положительным";
        }
        if (cmd.type() == OrderType.LIMIT && (cmd.price() == null || cmd.price().signum() <= 0)) {
            return "Цена лимитного ордера должна быть положительной";
        }
        return null;
    }

    private void insert(UUID id, Network network, PlaceOrderCommand cmd, PaperOrderStatus status, BigDecimal filled,
                        BigDecimal avgPrice, BigDecimal fee, String rejectReason, OffsetDateTime now) {
        jdbc.update("""
                        INSERT INTO PAPER_ORDER (ID, CLIENT_ORDER_ID, NETWORK, SYMBOL, SIDE, TYPE_, PRICE, QUANTITY, REDUCE_ONLY,
                                                 STATUS, FILLED_QTY, AVG_PRICE, FEE, REJECT_REASON, CREATED_AT, UPDATED_AT)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (CLIENT_ORDER_ID) DO NOTHING""",
                id, cmd.clientOrderId(), network.getId(), cmd.symbol().orderly(), cmd.side().getId(), cmd.type().getId(),
                cmd.price(), cmd.quantity(), cmd.reduceOnly(), status.getId(), filled, avgPrice, fee, rejectReason, now, now);
    }

    private Optional<Row> findRow(String clientOrderId) {
        return jdbc.query("SELECT * FROM PAPER_ORDER WHERE CLIENT_ORDER_ID = ?", rowMapper, clientOrderId)
                .stream().findFirst();
    }

    private ExchangeOrder toExchangeOrder(Row row) {
        ExchangeOrderStatus status = switch (row.status()) {
            case NEW -> ExchangeOrderStatus.OPEN;
            case FILLED -> ExchangeOrderStatus.FILLED;
            case CANCELLED -> ExchangeOrderStatus.CANCELLED;
            case REJECTED -> ExchangeOrderStatus.REJECTED;
        };
        return new ExchangeOrder(row.id().toString(), row.clientOrderId(), status, row.filledQty(), row.avgPrice(),
                row.fee(), row.rejectReason());
    }

    private static BigDecimal fee(BigDecimal qty, BigDecimal price, BigDecimal rate) {
        return qty.multiply(price).multiply(rate).setScale(FEE_SCALE, RoundingMode.HALF_UP);
    }
}
