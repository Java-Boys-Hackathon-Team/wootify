package ru.javaboys.wootify.bot.strategy.dca;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import ru.javaboys.wootify.bot.order.BotOrderService;
import ru.javaboys.wootify.bot.strategy.StrategyContext;
import ru.javaboys.wootify.bot.strategy.TickResult;
import ru.javaboys.wootify.bot.strategy.TradingStrategy;
import ru.javaboys.wootify.common.TransientFailure;
import ru.javaboys.wootify.entity.Bot;
import ru.javaboys.wootify.entity.BotCycle;
import ru.javaboys.wootify.entity.CycleCloseReason;
import ru.javaboys.wootify.entity.CycleStatus;
import ru.javaboys.wootify.entity.EventCategory;
import ru.javaboys.wootify.entity.Order;
import ru.javaboys.wootify.entity.OrderRole;
import ru.javaboys.wootify.entity.OrderSide;
import ru.javaboys.wootify.entity.OrderStatus;
import ru.javaboys.wootify.entity.OrderType;
import ru.javaboys.wootify.entity.StopAction;
import ru.javaboys.wootify.entity.TradeType;
import ru.javaboys.wootify.entity.TradingMode;
import ru.javaboys.wootify.exchange.instrument.InstrumentInfo;
import ru.javaboys.wootify.exchange.marketdata.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Стратегия усреднения (DCA).
 * <p>
 * Цикл: рыночный базовый ордер → сетка страховочных лимитных ордеров от цены его исполнения →
 * лимитный reduce-only ордер фиксации прибыли от средней цены входа, переставляемый после каждого
 * исполнения → закрытие цикла, когда позиция обнулилась. Необязательный стоп-лосс закрывает позицию
 * рыночным ордером.
 * <p>
 * Каждый такт идемпотентен: ордера цикла сверяются с биржей, позиция пересчитывается по исполнениям,
 * недостающие ордера досоздаются. Поэтому после перезапуска на любом шаге бот продолжает корректно.
 */
public class DcaStrategy implements TradingStrategy {

    private static final int STOP_ATTEMPTS = 20;
    private static final Duration STOP_POLL = Duration.ofSeconds(1);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final UnconstrainedDataManager dm;
    private final BotOrderService orders;
    private final ObjectMapper mapper;
    private final Bot bot;
    private final DcaParameters params;

    /** План цикла, сохраняемый в {@link BotCycle#getPlan()}. */
    record CyclePlan(DcaParameters params, BigDecimal refPrice, BigDecimal baseQty, BigDecimal basePrice,
                     List<DcaPlan.Level> levels) {

        CyclePlan withBase(BigDecimal price, List<DcaPlan.Level> safety) {
            return new CyclePlan(params, refPrice, baseQty, price, safety);
        }
    }

    /** Ордера ещё не отменены или позиция ещё не закрыта: остановку нужно повторить. */
    static final class StopPendingException extends RuntimeException implements TransientFailure {
        StopPendingException(String message) {
            super(message);
        }
    }

    public DcaStrategy(Bot bot, UnconstrainedDataManager dm, BotOrderService orders, ObjectMapper mapper) {
        this.bot = bot;
        this.dm = dm;
        this.orders = orders;
        this.mapper = mapper;
        this.params = DcaParameters.of(bot.getDcaSettings());
    }

    // ------------------------------------------------------------------ жизненный цикл

    @Override
    public void start(StrategyContext ctx) {
        InstrumentInfo instrument = ctx.instrument();
        Quote quote = ctx.quote();
        BigDecimal ref = entryPrice(quote);
        // Проверка, что сетка с текущими настройками выполнима на текущих ценах.
        DcaPlan.baseQuantity(params, instrument, ref);
        DcaPlan.safetyLevels(params, instrument, ref);
        if (bot.getTradingMode() == TradingMode.LIVE) {
            ctx.exchange().setLeverage(ctx.symbol(), params.leverage());
        }
        activeCycle().ifPresent(c -> ctx.status("Продолжение цикла #" + c.getNumber()));
    }

    @Override
    public TickResult tick(StrategyContext ctx) {
        Optional<BotCycle> active = activeCycle();
        BotCycle cycle;
        if (active.isPresent()) {
            cycle = active.get();
        } else {
            Optional<TickResult> waiting = beforeNewCycle(ctx);
            if (waiting.isPresent()) {
                return waiting.get();
            }
            cycle = openCycle(ctx);
        }

        List<Order> cycleOrders = new ArrayList<>(orders.ordersOf(cycle));
        orders.sync(ctx, cycleOrders);
        CycleStats stats = CycleStats.of(cycleOrders);
        CyclePlan plan = readPlan(cycle);
        InstrumentInfo instrument = ctx.instrument();

        if (cycle.getStatus() == CycleStatus.CLOSING) {
            progressClosing(ctx, cycle, cycleOrders, instrument);
            return TickResult.CONTINUE;
        }

        // Базовый ордер.
        Optional<Order> base = latest(cycleOrders, OrderRole.BASE, 0);
        if (base.isEmpty() || (!BotOrderService.isActive(base.get()) && BotOrderService.filled(base.get()).signum() == 0)) {
            if (base.isPresent() && base.get().getStatus() == OrderStatus.ERROR) {
                throw new IllegalStateException("Базовый ордер отклонён биржей: " + base.get().getErrorMessage());
            }
            orders.place(ctx, cycle, OrderRole.BASE, 0, entrySide(), OrderType.MARKET, plan.baseQty(), null, false);
            ctx.status("Цикл #" + cycle.getNumber() + ": отправлен базовый ордер");
            return TickResult.CONTINUE;
        }
        if (BotOrderService.isActive(base.get())) {
            ctx.status("Цикл #" + cycle.getNumber() + ": ожидание исполнения базового ордера");
            return TickResult.CONTINUE;
        }

        // Сетка строится один раз - от фактической цены исполнения базового ордера.
        if (plan.basePrice() == null) {
            BigDecimal basePrice = base.get().getAverageExecutedPrice();
            plan = plan.withBase(basePrice, DcaPlan.safetyLevels(plan.params(), instrument, basePrice));
            cycle.setBasePrice(basePrice);
            cycle.setStopLossPrice(DcaPlan.stopLossPrice(plan.params(), instrument, basePrice));
            cycle.setPlan(write(plan));
            ctx.info(EventCategory.CYCLE, gridDescription(cycle, plan));
        }
        cycle = saveStats(cycle, stats, instrument, plan);

        // Позиция закрыта фиксацией прибыли.
        if (stats.exitQty().signum() > 0 && stats.netQty().compareTo(instrument.baseMin()) < 0) {
            finishAfterTakeProfit(ctx, cycle, cycleOrders);
            return TickResult.CONTINUE;
        }

        // Стоп-лосс.
        if (cycle.getStopLossPrice() != null && stopLossTriggered(ctx.quote(), cycle.getStopLossPrice())) {
            cycle.setStatus(CycleStatus.CLOSING);
            cycle.setCloseReason(CycleCloseReason.STOP_LOSS);
            cycle = dm.save(cycle);
            ctx.warn(EventCategory.CYCLE, "Цикл #" + cycle.getNumber() + ": сработал стоп-лосс по цене "
                                          + plain(cycle.getStopLossPrice()) + ", позиция закрывается");
            progressClosing(ctx, cycle, cycleOrders, instrument);
            return TickResult.CONTINUE;
        }

        ensureSafetyOrders(ctx, cycle, cycleOrders, plan);
        ensureTakeProfit(ctx, cycle, cycleOrders, stats, instrument, plan);
        ctx.status(summary(cycle, stats, plan));
        return TickResult.CONTINUE;
    }

    @Override
    public void stop(StrategyContext ctx, StopAction action) {
        Optional<BotCycle> active = activeCycle();
        if (active.isEmpty() || action == StopAction.KEEP_ORDERS) {
            return;
        }
        BotCycle cycle = active.get();
        InstrumentInfo instrument = ctx.instrument();
        if (action == StopAction.CLOSE_POSITION && cycle.getStatus() == CycleStatus.OPEN) {
            cycle.setStatus(CycleStatus.CLOSING);
            cycle.setCloseReason(CycleCloseReason.MANUAL);
            cycle = dm.save(cycle);
            ctx.info(EventCategory.CYCLE, "Цикл #" + cycle.getNumber() + ": закрытие позиции по запросу пользователя");
        }
        for (int attempt = 0; attempt < STOP_ATTEMPTS; attempt++) {
            List<Order> cycleOrders = new ArrayList<>(orders.ordersOf(cycle));
            orders.sync(ctx, cycleOrders);
            if (action == StopAction.CANCEL_ORDERS) {
                boolean pending = cancelActive(ctx, cycleOrders, o -> true);
                saveStats(cycle, CycleStats.of(cycleOrders), instrument, readPlan(cycle));
                if (!pending) {
                    return;
                }
            } else {
                BotCycle current = dm.load(BotCycle.class).id(cycle.getId()).one();
                if (current.getStatus() == CycleStatus.CLOSED) {
                    return;
                }
                progressClosing(ctx, current, cycleOrders, instrument);
                if (dm.load(BotCycle.class).id(cycle.getId()).one().getStatus() == CycleStatus.CLOSED) {
                    return;
                }
            }
            sleep();
        }
        throw new StopPendingException(action == StopAction.CANCEL_ORDERS
                ? "Ордера ещё отменяются" : "Позиция ещё закрывается");
    }

    // ------------------------------------------------------------------ циклы

    private Optional<BotCycle> activeCycle() {
        return dm.load(BotCycle.class)
                .query("select c from BotCycle c where c.bot = :bot and c.status <> :closed")
                .parameter("bot", bot)
                .parameter("closed", CycleStatus.CLOSED.getId())
                .fetchPlan(FetchPlan.BASE)
                .optional();
    }

    /**
     * @return результат такта, если новый цикл пока открывать нельзя
     */
    private Optional<TickResult> beforeNewCycle(StrategyContext ctx) {
        List<BotCycle> closed = dm.load(BotCycle.class)
                .query("select c from BotCycle c where c.bot = :bot and c.status = :closed order by c.number desc")
                .parameter("bot", bot)
                .parameter("closed", CycleStatus.CLOSED.getId())
                .fetchPlan(FetchPlan.BASE)
                .list();
        Integer maxCycles = bot.getDcaSettings().getMaxCycles();
        if (maxCycles != null && closed.size() >= maxCycles) {
            ctx.status("Выполнено циклов: " + closed.size() + " из " + maxCycles);
            return Optional.of(TickResult.COMPLETED);
        }
        int cooldown = bot.getDcaSettings().getCycleCooldownSec();
        if (!closed.isEmpty() && cooldown > 0 && closed.getFirst().getClosedAt() != null) {
            OffsetDateTime resumeAt = closed.getFirst().getClosedAt().plusSeconds(cooldown);
            if (resumeAt.isAfter(OffsetDateTime.now(ctx.clock()))) {
                ctx.status("Пауза между циклами до " + resumeAt.atZoneSameInstant(ZoneId.systemDefault()).format(TIME));
                return Optional.of(TickResult.CONTINUE);
            }
        }
        return Optional.empty();
    }

    private BotCycle openCycle(StrategyContext ctx) {
        InstrumentInfo instrument = ctx.instrument();
        BigDecimal ref = entryPrice(ctx.quote());
        BigDecimal baseQty = DcaPlan.baseQuantity(params, instrument, ref);
        DcaPlan.safetyLevels(params, instrument, ref);

        Integer last = dm.loadValue("select max(c.number) from BotCycle c where c.bot = :bot", Integer.class)
                .parameter("bot", bot).optional().orElse(null);
        BotCycle cycle = dm.create(BotCycle.class);
        cycle.setBot(bot);
        cycle.setNumber(last == null ? 1 : last + 1);
        cycle.setStatus(CycleStatus.OPEN);
        cycle.setDirection(params.direction());
        cycle.setStartedAt(OffsetDateTime.now(ctx.clock()));
        cycle.setEntryQty(BigDecimal.ZERO);
        cycle.setEntryCost(BigDecimal.ZERO);
        cycle.setExitQty(BigDecimal.ZERO);
        cycle.setExitProceeds(BigDecimal.ZERO);
        cycle.setFees(BigDecimal.ZERO);
        cycle.setFilledSafetyOrders(0);
        cycle.setPlan(write(new CyclePlan(params, ref, baseQty, null, List.of())));
        cycle = dm.save(cycle);
        ctx.info(EventCategory.CYCLE, "Цикл #" + cycle.getNumber() + " открыт: " + params.direction()
                                      + ", базовый ордер " + plain(baseQty) + " по рынку (~" + plain(ref) + ")");
        return cycle;
    }

    private BotCycle saveStats(BotCycle cycle, CycleStats stats, InstrumentInfo instrument, CyclePlan plan) {
        cycle.setEntryQty(stats.entryQty());
        cycle.setEntryCost(stats.entryCost());
        cycle.setExitQty(stats.exitQty());
        cycle.setExitProceeds(stats.exitProceeds());
        cycle.setFees(stats.fees());
        cycle.setFilledSafetyOrders(stats.filledSafetyOrders());
        cycle.setAvgEntryPrice(stats.avgEntryPrice());
        if (stats.avgEntryPrice() != null) {
            cycle.setTakeProfitPrice(DcaPlan.takeProfitPrice(plan.params(), instrument, stats.avgEntryPrice()));
        }
        return dm.save(cycle);
    }

    private void finishAfterTakeProfit(StrategyContext ctx, BotCycle cycle, List<Order> cycleOrders) {
        if (cancelActive(ctx, cycleOrders, o -> true)) {
            ctx.status("Цикл #" + cycle.getNumber() + ": позиция закрыта, отмена оставшихся ордеров");
            return;
        }
        CycleStats stats = CycleStats.of(cycleOrders);
        if (stats.netQty().compareTo(ctx.instrument().baseMin()) >= 0) {
            // Пока отменяли сетку, исполнился страховочный ордер: цикл продолжается.
            return;
        }
        closeCycle(ctx, cycle, stats, CycleCloseReason.TAKE_PROFIT);
    }

    private void progressClosing(StrategyContext ctx, BotCycle cycle, List<Order> cycleOrders, InstrumentInfo instrument) {
        if (cancelActive(ctx, cycleOrders, o -> o.getRole() != OrderRole.CLOSE)) {
            ctx.status("Цикл #" + cycle.getNumber() + ": закрытие, ожидание отмены ордеров");
            return;
        }
        CycleStats stats = CycleStats.of(cycleOrders);
        BigDecimal remaining = instrument.roundQtyDown(stats.netQty().max(BigDecimal.ZERO));
        if (remaining.compareTo(instrument.baseMin()) >= 0) {
            boolean closing = cycleOrders.stream()
                    .anyMatch(o -> o.getRole() == OrderRole.CLOSE && BotOrderService.isActive(o));
            if (!closing) {
                orders.place(ctx, cycle, OrderRole.CLOSE, 0, exitSide(), OrderType.MARKET, remaining, null, true);
            }
            ctx.status("Цикл #" + cycle.getNumber() + ": закрытие позиции " + plain(remaining) + " по рынку");
            return;
        }
        closeCycle(ctx, cycle, stats, cycle.getCloseReason() != null ? cycle.getCloseReason() : CycleCloseReason.MANUAL);
    }

    private void closeCycle(StrategyContext ctx, BotCycle cycle, CycleStats stats, CycleCloseReason reason) {
        cycle.setEntryQty(stats.entryQty());
        cycle.setEntryCost(stats.entryCost());
        cycle.setExitQty(stats.exitQty());
        cycle.setExitProceeds(stats.exitProceeds());
        cycle.setFees(stats.fees());
        cycle.setAvgEntryPrice(stats.avgEntryPrice());
        cycle.setFilledSafetyOrders(stats.filledSafetyOrders());
        cycle.setRealizedPnl(stats.realizedPnl(cycle.getDirection() != TradeType.SHORT));
        cycle.setStatus(CycleStatus.CLOSED);
        cycle.setCloseReason(reason);
        cycle.setClosedAt(OffsetDateTime.now(ctx.clock()));
        cycle = dm.save(cycle);
        String why = switch (reason) {
            case TAKE_PROFIT -> "фиксация прибыли";
            case STOP_LOSS -> "стоп-лосс";
            case MANUAL -> "по запросу пользователя";
            case EXTERNAL -> "позиция закрыта вне бота";
        };
        ctx.info(EventCategory.CYCLE, "Цикл #" + cycle.getNumber() + " закрыт (" + why + "). Результат: "
                                      + cycle.getRealizedPnl().setScale(4, RoundingMode.HALF_UP).toPlainString()
                                      + " USDC, комиссии " + stats.fees().setScale(4, RoundingMode.HALF_UP).toPlainString());
        ctx.status("Цикл #" + cycle.getNumber() + " закрыт: " + why);
    }

    // ------------------------------------------------------------------ ордера

    private void ensureSafetyOrders(StrategyContext ctx, BotCycle cycle, List<Order> cycleOrders, CyclePlan plan) {
        for (DcaPlan.Level level : plan.levels()) {
            List<Order> forLevel = cycleOrders.stream()
                    .filter(o -> o.getRole() == OrderRole.SAFETY && level.level() == o.getGridLevel())
                    .toList();
            boolean covered = forLevel.stream().anyMatch(o -> BotOrderService.isActive(o)
                                                              || o.getStatus() == OrderStatus.CLOSED
                                                              || o.getStatus() == OrderStatus.ERROR
                                                              || BotOrderService.filled(o).signum() > 0);
            if (!covered) {
                // Уровня нет или его ордер отменён без исполнения (например, при остановке бота).
                cycleOrders.add(orders.place(ctx, cycle, OrderRole.SAFETY, level.level(), entrySide(), OrderType.LIMIT,
                        level.quantity(), level.price(), false));
            }
        }
    }

    private void ensureTakeProfit(StrategyContext ctx, BotCycle cycle, List<Order> cycleOrders, CycleStats stats,
                                  InstrumentInfo instrument, CyclePlan plan) {
        BigDecimal desiredQty = instrument.roundQtyDown(stats.netQty().max(BigDecimal.ZERO));
        if (desiredQty.compareTo(instrument.baseMin()) < 0 || stats.avgEntryPrice() == null) {
            return;
        }
        BigDecimal desiredPrice = DcaPlan.takeProfitPrice(plan.params(), instrument, stats.avgEntryPrice());
        List<Order> active = cycleOrders.stream()
                .filter(o -> o.getRole() == OrderRole.TAKE_PROFIT && BotOrderService.isActive(o))
                .sorted(Comparator.comparing(Order::getCreatedDate).thenComparing(Order::getClientOrderId))
                .toList();
        if (active.isEmpty()) {
            cycleOrders.add(orders.place(ctx, cycle, OrderRole.TAKE_PROFIT, 0, exitSide(), OrderType.LIMIT,
                    desiredQty, desiredPrice, true));
            return;
        }
        for (int i = 0; i < active.size(); i++) {
            Order tp = active.get(i);
            if (tp.getStatus() == OrderStatus.SENT_CANCEL) {
                continue;
            }
            BigDecimal remaining = tp.getQuantity().subtract(BotOrderService.filled(tp));
            boolean outdated = i > 0
                               || remaining.subtract(desiredQty).abs().compareTo(instrument.baseTick()) >= 0
                               || tp.getPrice().compareTo(desiredPrice) != 0;
            if (outdated) {
                // Новый ордер будет выставлен на следующем такте, когда отмена подтвердится.
                orders.cancel(ctx, tp);
            }
        }
    }

    /**
     * Отменяет активные ордера, подходящие под фильтр.
     *
     * @return остались ли ордера, отмена которых ещё не подтверждена
     */
    private boolean cancelActive(StrategyContext ctx, List<Order> cycleOrders, Predicate<Order> filter) {
        boolean pending = false;
        for (int i = 0; i < cycleOrders.size(); i++) {
            Order o = cycleOrders.get(i);
            if (BotOrderService.isActive(o) && filter.test(o)) {
                o = orders.cancel(ctx, o);
                cycleOrders.set(i, o);
                pending |= BotOrderService.isActive(o);
            }
        }
        return pending;
    }

    private static Optional<Order> latest(List<Order> cycleOrders, OrderRole role, int level) {
        return cycleOrders.stream()
                .filter(o -> o.getRole() == role && o.getGridLevel() != null && o.getGridLevel() == level)
                .max(Comparator.comparing(Order::getCreatedDate).thenComparing(Order::getClientOrderId));
    }

    // ------------------------------------------------------------------ вспомогательное

    private OrderSide entrySide() {
        return params.isLong() ? OrderSide.BUY : OrderSide.SELL;
    }

    private OrderSide exitSide() {
        return params.isLong() ? OrderSide.SELL : OrderSide.BUY;
    }

    private BigDecimal entryPrice(Quote quote) {
        return params.isLong() ? quote.ask() : quote.bid();
    }

    private boolean stopLossTriggered(Quote quote, BigDecimal stopLoss) {
        return params.isLong() ? quote.bid().compareTo(stopLoss) <= 0 : quote.ask().compareTo(stopLoss) >= 0;
    }

    private String gridDescription(BotCycle cycle, CyclePlan plan) {
        StringBuilder sb = new StringBuilder("Цикл #").append(cycle.getNumber()).append(": базовый ордер исполнен по ")
                .append(plain(plan.basePrice()));
        if (!plan.levels().isEmpty()) {
            sb.append(". Сетка: ").append(plan.levels().size()).append(" страховочных ордеров от ")
                    .append(plain(plan.levels().getFirst().price())).append(" до ")
                    .append(plain(plan.levels().getLast().price()));
        }
        if (cycle.getStopLossPrice() != null) {
            sb.append(", стоп-лосс ").append(plain(cycle.getStopLossPrice()));
        }
        return sb.toString();
    }

    private String summary(BotCycle cycle, CycleStats stats, CyclePlan plan) {
        return "Цикл #" + cycle.getNumber() + " " + params.direction() + ": позиция " + plain(stats.netQty())
               + " по " + plain(stats.avgEntryPrice() == null ? null : stats.avgEntryPrice().setScale(6, RoundingMode.HALF_UP))
               + ", страховочных исполнено " + stats.filledSafetyOrders() + "/" + plan.levels().size()
               + ", фиксация прибыли " + plain(cycle.getTakeProfitPrice());
    }

    private CyclePlan readPlan(BotCycle cycle) {
        try {
            return mapper.readValue(cycle.getPlan(), CyclePlan.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Повреждён план цикла #" + cycle.getNumber(), e);
        }
    }

    private String write(CyclePlan plan) {
        try {
            return mapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(STOP_POLL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Остановка прервана", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
