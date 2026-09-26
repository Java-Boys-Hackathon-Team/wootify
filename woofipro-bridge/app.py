"""
WOOFi Pro Thin Bridge: HTTP-доступ к WOOFi Pro (Orderly) через CCXT.

Учётные данные Orderly передаются заголовками X-Woo-* (для совместимости принимаются и параметры
строки запроса, но так они попадают в журналы доступа). Доступ к самому мосту защищён общим
секретом в заголовке X-API-Key.

Ошибки CCXT отображаются на HTTP-коды по категориям, чтобы клиент мог отличить временный сбой
от отказа биржи:
    401 - ключи недействительны, 404 - ордер не найден, 422 - биржа отклонила запрос,
    429 - превышен лимит запросов, 503 - сеть или биржа недоступны, 502 - прочие ошибки биржи.
Тело ошибки: {"error_type": "<класс исключения CCXT>", "detail": "<сообщение>"}.
"""
import asyncio
import hmac
import json
import logging
import os
import threading
from typing import Any, Dict, Optional, Tuple

import ccxt
from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field, field_validator

try:
    import ccxt.pro as ccxtpro  # optional
    HAS_CCXTPRO = True
except Exception:
    ccxtpro = None
    HAS_CCXTPRO = False

# ------------ CONFIG ------------
APP_API_KEY = os.getenv("APP_API_KEY")
EXCHANGE_ID = os.getenv("EXCHANGE_ID", "woofipro")

log = logging.getLogger("woofipro-bridge")
app = FastAPI(title="WOOFi Pro Thin Bridge", version="0.3.0")


# ------------ AUTH ------------
def verify_api_key(request: Request):
    if not APP_API_KEY:
        return  # auth disabled
    hdr = request.headers.get("X-API-Key") or ""
    if not hmac.compare_digest(hdr, APP_API_KEY):
        raise HTTPException(status_code=401, detail="Unauthorized")


# ------------ ERRORS ------------
def _error_status(e: Exception) -> int:
    if isinstance(e, (ccxt.AuthenticationError, ccxt.PermissionDenied, ccxt.AccountSuspended)):
        return 401
    if isinstance(e, ccxt.OrderNotFound):
        return 404
    if isinstance(e, (ccxt.RateLimitExceeded, ccxt.DDoSProtection)):
        return 429
    if isinstance(e, ccxt.NetworkError):
        return 503
    if isinstance(e, (ccxt.InsufficientFunds, ccxt.InvalidOrder, ccxt.BadRequest, ccxt.BadSymbol,
                      ccxt.ArgumentsRequired, ccxt.NotSupported)):
        return 422
    if isinstance(e, ccxt.ExchangeError):
        return 502
    return 500


@app.exception_handler(ccxt.BaseError)
async def ccxt_error_handler(request: Request, e: ccxt.BaseError):
    status = _error_status(e)
    if status >= 500:
        log.warning("%s %s -> %s: %s", request.method, request.url.path, type(e).__name__, e)
    return JSONResponse(status_code=status, content={"error_type": type(e).__name__, "detail": str(e)})


# ------------ CREDENTIALS ------------
class ExchangeParams:
    """Учётные данные Orderly: сначала заголовки X-Woo-*, затем параметры запроса, затем окружение."""

    def __init__(
            self,
            request: Request,
            apiKey: Optional[str] = Query(default=None, description="Устарело: используйте X-Woo-Api-Key"),
            apiSecret: Optional[str] = Query(default=None, description="Устарело: используйте X-Woo-Api-Secret"),
            accountId: Optional[str] = Query(default=None, description="Устарело: используйте X-Woo-Account-Id"),
            brokerId: Optional[str] = Query(default=None, description="Устарело: используйте X-Woo-Broker-Id"),
            env: Optional[str] = Query(default=None, description="Устарело: используйте X-Woo-Env"),
    ):
        h = request.headers
        self.apiKey = h.get("X-Woo-Api-Key") or apiKey or os.getenv("WOO_API_KEY")
        self.apiSecret = h.get("X-Woo-Api-Secret") or apiSecret or os.getenv("WOO_API_SECRET")
        self.accountId = h.get("X-Woo-Account-Id") or accountId or os.getenv("WOO_ACCOUNT_ID")
        self.brokerId = h.get("X-Woo-Broker-Id") or brokerId or os.getenv("WOO_BROKER_ID", "woofi_pro")
        self.env = (h.get("X-Woo-Env") or env or os.getenv("WOO_ENV", "mainnet")).lower()

    def key(self) -> Tuple:
        return (self.apiKey, self.apiSecret, self.accountId, self.brokerId, self.env)


class _CachedExchange:
    def __init__(self, exchange):
        self.exchange = exchange
        # Экземпляр CCXT не рассчитан на одновременные вызовы из нескольких потоков.
        self.lock = threading.Lock()


_exchanges: Dict[Tuple, _CachedExchange] = {}
_exchanges_lock = threading.Lock()


def _build_exchange(params: ExchangeParams):
    klass = getattr(ccxt, EXCHANGE_ID)
    ex = klass({
        "apiKey": params.apiKey,
        "secret": params.apiSecret,
        "accountId": params.accountId,
        "brokerId": params.brokerId,
        "enableRateLimit": True,
        "timeout": 15000,
    })
    if params.env == "testnet":
        ex.set_sandbox_mode(True)
    return ex


def exchange_for(params: ExchangeParams) -> _CachedExchange:
    """Экземпляр биржи на набор учётных данных. Рынки загружаются один раз и кэшируются CCXT."""
    key = params.key()
    with _exchanges_lock:
        cached = _exchanges.get(key)
        if cached is None:
            cached = _CachedExchange(_build_exchange(params))
            _exchanges[key] = cached
        return cached


def call(params: ExchangeParams, fn):
    cached = exchange_for(params)
    with cached.lock:
        return fn(cached.exchange)


# ------------ MODELS ------------
class OrderReq(BaseModel):
    symbol: str
    side: str  # "buy" / "sell"
    type: str  # "market" / "limit"
    amount: float
    price: Optional[float] = None
    clientOrderId: Optional[str] = Field(default=None, alias="clientOrderId")
    params: Dict[str, Any] = Field(default_factory=dict)


class OrderV2Req(BaseModel):
    symbol: str
    side: str
    type: str
    amount: str
    price: Optional[str] = None
    clientOrderId: str
    reduceOnly: bool = False


class LeverageReq(BaseModel):
    leverage: int = Field(..., gt=0, lt=201, description="Целое плечо от 1 до 200")
    marginMode: Optional[str] = Field(
        default=None, description="isolated | cross (woofipro не поддерживает переключение)"
    )

    @field_validator("leverage", mode="before")
    @classmethod
    def ensure_int(cls, v):
        """Разрешаем '15' или 15.0, запрещаем 15.5"""
        try:
            fv = float(v)
        except Exception:
            raise ValueError("leverage must be a number")
        if not fv.is_integer():
            raise ValueError("leverage must be an integer")
        return int(fv)


# ------------ HELPERS ------------
def _first(*values):
    for v in values:
        if v is not None and v != "":
            return v
    return None


def normalize_order(order: Dict[str, Any]) -> Dict[str, Any]:
    """Приводит ордер CCXT к компактной форме, дополняя пропуски полями ответа Orderly."""
    info = order.get("info") or {}
    fee = order.get("fee") or {}
    status = order.get("status")
    raw_status = info.get("status")
    if raw_status in ("CANCEL_SENT", "CANCEL_ALL_SENT"):
        status = "open"  # отмена ещё не подтверждена биржей
    return {
        "id": _first(order.get("id"), info.get("order_id")),
        "clientOrderId": _first(order.get("clientOrderId"), info.get("client_order_id")),
        "symbol": order.get("symbol"),
        "side": order.get("side"),
        "type": order.get("type"),
        "status": status,
        "rawStatus": raw_status,
        "price": order.get("price"),
        "amount": order.get("amount"),
        "filled": _first(order.get("filled"), info.get("total_executed_quantity"), info.get("executed"), 0),
        "average": _first(order.get("average"), info.get("average_executed_price")),
        "fee": _first(fee.get("cost"), info.get("total_fee"), 0),
        "reduceOnly": order.get("reduceOnly"),
    }


# ------------ ENDPOINTS: service ------------
@app.get("/health")
def health():
    return {"status": "ok", "ccxtpro": HAS_CCXTPRO, "exchange": EXCHANGE_ID}


@app.get("/whoami", dependencies=[Depends(verify_api_key)])
def whoami(params: ExchangeParams = Depends()):
    return {
        "exchange": EXCHANGE_ID,
        "env": params.env,
        "accountId": params.accountId,
        "brokerId": params.brokerId,
        "apiKeyConfigured": bool(params.apiKey),
    }


# ------------ ENDPOINTS: v2 (движок ботов) ------------
@app.post("/v2/orders", dependencies=[Depends(verify_api_key)])
def create_order_v2(req: OrderV2Req, params: ExchangeParams = Depends()):
    def place(ex):
        ex.load_markets()
        p = {"clientOrderId": req.clientOrderId}
        if req.reduceOnly:
            p["reduceOnly"] = True
        price = float(req.price) if req.price is not None else None
        order = ex.create_order(req.symbol, req.type, req.side, float(req.amount), price, p)
        # Orderly подтверждает только приём ордера; актуальное состояние запрашиваем отдельно.
        try:
            order = ex.fetch_order(order.get("id"), req.symbol)
        except ccxt.BaseError:
            pass
        return normalize_order(order)

    return call(params, place)


@app.get("/v2/orders/by-client-id/{client_order_id}", dependencies=[Depends(verify_api_key)])
def get_order_by_client_id_v2(client_order_id: str, symbol: str, params: ExchangeParams = Depends()):
    def fetch(ex):
        ex.load_markets()
        return normalize_order(ex.fetch_order(None, symbol, {"clientOrderId": client_order_id}))

    try:
        return call(params, fetch)
    except (ccxt.OrderNotFound, ccxt.InvalidOrder) as e:
        # Orderly отвечает "-1006 RESOURCE_NOT_FOUND" (InvalidOrder в CCXT) для неизвестного ордера.
        raise ccxt.OrderNotFound(str(e))


@app.get("/v2/orders/{order_id}", dependencies=[Depends(verify_api_key)])
def get_order_v2(order_id: str, symbol: str, params: ExchangeParams = Depends()):
    def fetch(ex):
        ex.load_markets()
        return normalize_order(ex.fetch_order(order_id, symbol))

    return call(params, fetch)


@app.delete("/v2/orders/by-client-id/{client_order_id}", dependencies=[Depends(verify_api_key)])
def cancel_order_by_client_id_v2(client_order_id: str, symbol: str, params: ExchangeParams = Depends()):
    """Идемпотентная отмена: если ордер уже завершён, возвращается его текущее состояние."""

    def cancel(ex):
        ex.load_markets()
        try:
            ex.cancel_order(None, symbol, {"clientOrderId": client_order_id})
        except ccxt.InvalidOrder:
            pass  # уже отменён или исполнен: проверим ниже
        try:
            return normalize_order(ex.fetch_order(None, symbol, {"clientOrderId": client_order_id}))
        except ccxt.InvalidOrder as e:
            raise ccxt.OrderNotFound(str(e))

    return call(params, cancel)


@app.put("/v2/leverage", dependencies=[Depends(verify_api_key)])
def set_leverage_v2(symbol: str, body: LeverageReq, params: ExchangeParams = Depends()):
    def apply(ex):
        ex.load_markets()
        result = ex.set_leverage(int(body.leverage), symbol, {})
        return {"symbol": symbol, "leverage": body.leverage, "result": result}

    return call(params, apply)


# ------------ ENDPOINTS: v1 (торговый терминал) ------------
@app.get("/markets", dependencies=[Depends(verify_api_key)])
def get_markets(params: ExchangeParams = Depends()):
    mkts = call(params, lambda ex: ex.load_markets())
    return {"symbols": list(mkts.keys()), "markets": mkts}


@app.get("/balance", dependencies=[Depends(verify_api_key)])
def get_balance(params: ExchangeParams = Depends()):
    return call(params, lambda ex: ex.fetch_balance())


@app.get("/positions", dependencies=[Depends(verify_api_key)])
def get_positions(
        params: ExchangeParams = Depends(),
        symbol: str | None = None,
        only_open: bool = True
):
    positions = call(params, lambda ex: ex.fetch_positions())
    if symbol:
        positions = [p for p in positions if p.get("symbol") == symbol]
    if only_open:
        positions = [p for p in positions if abs(float(p.get("contracts", 0) or 0)) > 0]
    return {"count": len(positions), "positions": positions}


@app.put("/positions/{symbol:path}/leverage", dependencies=[Depends(verify_api_key)])
def set_leverage(symbol: str, body: LeverageReq, params: ExchangeParams = Depends()):
    def apply(ex):
        warning = None
        if body.marginMode:
            try:
                ex.set_margin_mode(body.marginMode, symbol=symbol, params={})
            except (AttributeError, ccxt.NotSupported):
                warning = "Биржа не поддерживает смену режима маржи; продолжаем в режиме по умолчанию"
            except ccxt.BaseError as e:
                warning = f"set_margin_mode failed: {e}"

        lev = int(body.leverage)
        result = ex.set_leverage(lev, symbol=symbol, params={})
        response = {"symbol": symbol, "requestedLeverage": lev, "result": result}
        if body.marginMode is not None:
            response["requestedMarginMode"] = body.marginMode
        if warning:
            response["warning"] = warning
        return response

    return call(params, apply)


@app.post("/order", dependencies=[Depends(verify_api_key)])
def create_order(req: OrderReq, params: ExchangeParams = Depends()):
    def place(ex):
        p = dict(req.params or {})
        if req.clientOrderId:
            p["clientOrderId"] = req.clientOrderId
        return ex.create_order(req.symbol, req.type, req.side, req.amount, req.price, p)

    order = call(params, place)
    return {
        "id": order.get("id"),
        "status": order.get("status"),
        "symbol": order.get("symbol"),
        "type": order.get("type"),
        "side": order.get("side"),
        "price": order.get("price"),
        "amount": order.get("amount"),
        "filled": order.get("filled"),
        "remaining": order.get("remaining"),
        "info": order.get("info"),
    }


@app.post("/cancel", dependencies=[Depends(verify_api_key)])
def cancel_order(symbol: str, order_id: str, params: ExchangeParams = Depends()):
    res = call(params, lambda ex: ex.cancel_order(order_id, symbol))
    return {"canceled": True, "result": res}


@app.get("/orders", dependencies=[Depends(verify_api_key)])
def get_orders(
        params: ExchangeParams = Depends(),
        symbol: Optional[str] = None,
        status: str = "open",
        limit: Optional[int] = 50,
        since: Optional[int] = None
):
    status = status.lower()
    if status == "open":
        orders = call(params, lambda ex: ex.fetch_open_orders(symbol, since=since, limit=limit))
    elif status == "closed":
        orders = call(params, lambda ex: ex.fetch_closed_orders(symbol, since=since, limit=limit))
    elif status == "all":
        orders = call(params, lambda ex: ex.fetch_orders(symbol, since=since, limit=limit))
    else:
        raise HTTPException(status_code=400, detail="status must be one of: open|closed|all")
    return {"status": status, "count": len(orders), "orders": orders}


@app.get("/ticker/{symbol:path}", dependencies=[Depends(verify_api_key)])
def get_ticker(symbol: str, params: ExchangeParams = Depends()):
    t = call(params, lambda ex: ex.fetch_ticker(symbol))
    return {
        "symbol": symbol,
        "last": t.get("last"),
        "bid": t.get("bid"),
        "ask": t.get("ask"),
        "timestamp": t.get("timestamp"),
        "info": t.get("info")
    }


@app.get("/ohlcv", dependencies=[Depends(verify_api_key)])
def get_ohlcv(symbol: str, timeframe: str = "1m", limit: int = 100, params: ExchangeParams = Depends()):
    candles = call(params, lambda ex: ex.fetch_ohlcv(symbol, timeframe=timeframe, limit=limit))
    return {"symbol": symbol, "timeframe": timeframe, "candles": candles}


@app.get("/stream/ticker", dependencies=[Depends(verify_api_key)])
async def stream_ticker(symbol: str, interval_ms: int = 1000, params: ExchangeParams = Depends()):
    async def event_generator():
        while True:
            t = await asyncio.to_thread(call, params, lambda ex: ex.fetch_ticker(symbol))
            yield f"data: {json.dumps(t, default=str)}\n\n"
            await asyncio.sleep(max(0.05, interval_ms / 1000))

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@app.get("/order/{order_id}", dependencies=[Depends(verify_api_key)])
def get_order_by_id(
        order_id: str,
        symbol: str,  # обязателен для woofipro
        params: ExchangeParams = Depends(),
):
    return call(params, lambda ex: ex.fetch_order(order_id, symbol))


@app.get("/positions/{symbol:path}/leverage", dependencies=[Depends(verify_api_key)])
def get_leverage(
        symbol: str,
        include_tiers: bool = False,
        params: ExchangeParams = Depends(),
):
    """
    Возвращает текущее плечо по символу: через implicit API Orderly, при неудаче - по позициям.
    """

    def fetch(ex):
        ex.load_markets()
        market_id = ex.market(symbol).get("id", symbol)
        leverage_value = None
        source = None
        warning = None

        try:
            raw = ex.v1PrivateGetClientLeverage({"symbol": market_id})
            lev_str = ((raw or {}).get("data") or {}).get("leverage")
            if lev_str is not None:
                leverage_value = int(float(lev_str))
                source = "private.client.leverage"
        except (AttributeError, ccxt.BaseError) as e:
            warning = f"client leverage fetch failed: {e}"

        if leverage_value is None:
            pos = next((p for p in ex.fetch_positions() if p.get("symbol") == symbol), None)
            lev = None
            if pos:
                lev = _first(pos.get("leverage"), (pos.get("info") or {}).get("leverage"))
            if lev is not None:
                leverage_value = int(float(lev))
                source = "fetch_positions"

        tiers = None
        if include_tiers:
            try:
                tiers = ex.fetch_market_leverage_tiers(symbol)
            except (AttributeError, ccxt.BaseError) as e:
                warning = (warning + "; " if warning else "") + f"leverage tiers fetch failed: {e}"

        if leverage_value is None:
            raise HTTPException(status_code=404, detail=warning or "Leverage not found")

        resp = {"symbol": symbol, "leverage": leverage_value, "source": source}
        if warning:
            resp["warning"] = warning
        if include_tiers:
            resp["tiers"] = tiers
        return resp

    return call(params, fetch)
