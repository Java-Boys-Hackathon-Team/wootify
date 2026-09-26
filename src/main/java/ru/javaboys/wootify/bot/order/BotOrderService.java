package ru.javaboys.wootify.bot.order;

import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import ru.javaboys.wootify.bot.strategy.StrategyContext;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotCycle;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderRole;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderStatus;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.TradingMode;
import ru.javaboys.wootify.exchange.ExchangeException;
import ru.javaboys.wootify.exchange.ExchangeOrder;
import ru.javaboys.wootify.exchange.PlaceOrderCommand;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ордера бота: запись, отправка на биржу, сверка и отмена с гарантией отсутствия дублей.
 * <p>
 * Порядок размещения: сначала ордер сохраняется в БД со статусом CREATED и детерминированным
 * {@code clientOrderId}, затем отправляется. Если процесс упал между этими шагами или ответ биржи
 * потерялся, следующая сверка ищет ордер на бирже по {@code clientOrderId}: нашёлся - привязывает,
 * нет - отправляет повторно с тем же идентификатором. Так ордер не теряется и не дублируется.
 */
public class BotOrderService {

    public static final Set<OrderStatus> ACTIVE =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.SENT_OPEN, OrderStatus.OPEN, OrderStatus.SENT_CANCEL);

    private final UnconstrainedDataManager dataManager;

    public BotOrderService(UnconstrainedDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public static boolean isActive(Order order) {
        return ACTIVE.contains(order.getStatus());
    }

    public static BigDecimal filled(Order order) {
        return order.getTotalExecutedQuantity() == null ? BigDecimal.ZERO : order.getTotalExecutedQuantity();
    }

    public List<Order> ordersOf(BotCycle cycle) {
        return dataManager.load(Order.class)
                .query("select o from Order_ o where o.cycle = :cycle order by o.createdDate, o.clientOrderId")
                .parameter("cycle", cycle)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("symbol", FetchPlan.BASE))
                .list();
    }

    /**
     * Записывает и отправляет ордер.
     *
     * @throws ExchangeException.Transient если биржа недоступна; ордер останется CREATED и будет
     *                                     досоздан при следующей сверке
     */
    public Order place(StrategyContext ctx, BotCycle cycle, OrderRole role, int level, OrderSide side, OrderType type,
                       BigDecimal quantity, BigDecimal price, boolean reduceOnly) {
        Bot bot = ctx.bot();
        long sequence = dataManager.loadValue(
                        "select count(o) from Order_ o where o.cycle = :cycle and o.role = :role and o.gridLevel = :level",
                        Long.class)
                .parameter("cycle", cycle).parameter("role", role.getId()).parameter("level", level)
                .one();

        Order order = dataManager.create(Order.class);
        order.setBot(bot);
        order.setCycle(cycle);
        order.setSymbol(bot.getSymbol());
        order.setTradingMode(bot.getTradingMode());
        if (bot.getTradingMode() == TradingMode.LIVE) {
            order.setAccount(bot.getAccount());
            order.setApiKey(bot.getApiKey());
        }
        order.setRole(role);
        order.setGridLevel(level);
        order.setSide(side);
        order.setType(type);
        order.setQuantity(quantity);
        order.setPrice(type == OrderType.LIMIT ? price : null);
        order.setReduceOnly(reduceOnly);
        order.setStatus(OrderStatus.CREATED);
        order.setClientOrderId(ClientOrderIds.of(bot.getId(), cycle.getNumber(), role, level, sequence));
        order.setCreatedDate(LocalDateTime.now());
        order = dataManager.save(order);
        return send(ctx, order);
    }

    /**
     * Приводит локальные активные ордера в соответствие с биржей.
     */
    public void sync(StrategyContext ctx, List<Order> orders) {
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            if (isActive(order)) {
                orders.set(i, sync(ctx, order));
            }
        }
    }

    public Order sync(StrategyContext ctx, Order order) {
        Optional<ExchangeOrder> remote = ctx.exchange().findOrder(ctx.symbol(), order.getClientOrderId(),
                order.getOrderlyOrderId());
        if (remote.isPresent()) {
            return apply(ctx, order, remote.get());
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            // Отправка не дошла до биржи: безопасно повторить с тем же clientOrderId.
            return send(ctx, order);
        }
        order.setStatus(OrderStatus.ERROR);
        order.setErrorMessage("Ордер не найден на бирже");
        order.setClosedDate(LocalDateTime.now());
        ctx.warn(EventCategory.ORDER, "Ордер " + order.getClientOrderId() + " не найден на бирже, помечен ошибочным");
        return save(order);
    }

    /**
     * Отмена. Итоговый статус может оказаться «исполнен», если ордер успел исполниться.
     */
    public Order cancel(StrategyContext ctx, Order order) {
        if (!isActive(order)) {
            return order;
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            Optional<ExchangeOrder> remote = ctx.exchange().findOrder(ctx.symbol(), order.getClientOrderId(), null);
            if (remote.isEmpty()) {
                order.setStatus(OrderStatus.CANCELLED);
                order.setClosedDate(LocalDateTime.now());
                return save(order);
            }
            order = apply(ctx, order, remote.get());
            if (!isActive(order)) {
                return order;
            }
        }
        try {
            ctx.exchange().cancelOrder(ctx.symbol(), order.getClientOrderId(), order.getOrderlyOrderId());
        } catch (ExchangeException e) {
            if (e.getKind() != ExchangeException.Kind.NOT_FOUND) {
                throw e;
            }
        }
        order.setStatus(OrderStatus.SENT_CANCEL);
        order = save(order);
        return sync(ctx, order);
    }

    private Order send(StrategyContext ctx, Order order) {
        order.setSendingDate(LocalDateTime.now());
        ExchangeOrder result = ctx.exchange().placeOrder(new PlaceOrderCommand(ctx.symbol(), order.getClientOrderId(),
                order.getSide(), order.getType(), order.getQuantity(), order.getPrice(),
                Boolean.TRUE.equals(order.getReduceOnly())));
        order = apply(ctx, order, result);
        if (order.getStatus() == OrderStatus.ERROR) {
            ctx.warn(EventCategory.ORDER, describe(order) + " отклонён биржей: " + order.getErrorMessage());
        } else {
            ctx.info(EventCategory.ORDER, describe(order) + " отправлен");
        }
        return order;
    }

    private Order apply(StrategyContext ctx, Order order, ExchangeOrder remote) {
        OrderStatus before = order.getStatus();
        BigDecimal filledBefore = filled(order);
        OrderStatus status = switch (remote.status()) {
            case OPEN -> before == OrderStatus.SENT_CANCEL ? OrderStatus.SENT_CANCEL : OrderStatus.OPEN;
            case FILLED -> OrderStatus.CLOSED;
            case CANCELLED -> OrderStatus.CANCELLED;
            case REJECTED -> OrderStatus.ERROR;
        };
        order.setStatus(status);
        if (remote.exchangeOrderId() != null) {
            order.setOrderlyOrderId(remote.exchangeOrderId());
        }
        order.setTotalExecutedQuantity(remote.filledQty());
        order.setAverageExecutedPrice(remote.avgPrice());
        order.setTotalFee(remote.fee());
        order.setWoofiStatus(remote.status().name());
        if (remote.rejectReason() != null) {
            order.setErrorMessage(remote.rejectReason());
        }
        if (!isActive(order) && order.getClosedDate() == null) {
            order.setClosedDate(LocalDateTime.now());
        }
        if (status == OrderStatus.CLOSED && before != OrderStatus.CLOSED) {
            ctx.info(EventCategory.ORDER, describe(order) + " исполнен: " + plain(remote.filledQty()) + " по "
                                          + plain(remote.avgPrice()));
        } else if (status == OrderStatus.CANCELLED && before != OrderStatus.CANCELLED) {
            ctx.info(EventCategory.ORDER, describe(order) + " отменён"
                                          + (filled(order).signum() > 0 ? ", исполнено " + plain(filled(order)) : ""));
        } else if (filled(order).compareTo(filledBefore) > 0 && status == OrderStatus.OPEN) {
            ctx.info(EventCategory.ORDER, describe(order) + " исполнен частично: " + plain(filled(order)));
        }
        return save(order);
    }

    private Order save(Order order) {
        order.setUpdatedDate(LocalDateTime.now());
        return dataManager.save(order);
    }

    public static String describe(Order order) {
        String role = switch (order.getRole()) {
            case BASE -> "Базовый ордер";
            case SAFETY -> "Страховочный ордер " + order.getGridLevel();
            case TAKE_PROFIT -> "Ордер фиксации прибыли";
            case CLOSE -> "Ордер закрытия позиции";
            case MANUAL -> "Ордер";
        };
        return role + " " + order.getSide() + " " + plain(order.getQuantity())
               + (order.getType() == OrderType.LIMIT ? " по " + plain(order.getPrice()) : " по рынку");
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
