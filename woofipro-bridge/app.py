import os
import asyncio
from typing import Optional, Any, Dict

from fastapi import FastAPI, HTTPException, Request, Depends, WebSocket, WebSocketDisconnect, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, field_validator
from ccxt.base.errors import NotSupported

import ccxt
try:
    import ccxt.pro as ccxtpro  # optional
    HAS_CCXTPRO = True
except Exception:
    ccxtpro = None
    HAS_CCXTPRO = False

# ------------ CONFIG ------------
APP_API_KEY = os.getenv("APP_API_KEY")
EXCHANGE_ID = os.getenv("EXCHANGE_ID", "woofipro")

app = FastAPI(title="WOOFi Pro Thin Bridge", version="0.2.0")

# ------------ AUTH ------------
async def verify_api_key(request: Request):
    if not APP_API_KEY:
        return  # auth disabled
    hdr = request.headers.get("X-API-Key")
    if hdr != APP_API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")

# ------------ MODELS ------------

class ExchangeParams(BaseModel):
    apiKey: Optional[str] = Query(default=None, description="WOO_API_KEY override")
    apiSecret: Optional[str] = Query(default=None, description="WOO_API_SECRET override")
    accountId: Optional[str] = Query(default=None, description="WOO_ACCOUNT_ID override")
    brokerId: Optional[str] = Query(default=None, description="WOO_BROKER_ID override")
    env: Optional[str] = Query(default=None, description="WOO_ENV override (mainnet/testnet)")

class OrderReq(BaseModel):
    symbol: str
    side: str  # "buy" / "sell"
    type: str  # "market" / "limit"
    amount: float
    price: Optional[float] = None
    clientOrderId: Optional[str] = Field(default=None, alias="clientOrderId")
    params: Dict[str, Any] = Field(default_factory=dict)

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

def make_exchange_rest(params: ExchangeParams | None = None):
    exchange_id = os.getenv("EXCHANGE_ID", "woofipro")
    klass = getattr(ccxt, exchange_id)
    env = (params.env if params and params.env else os.getenv("WOO_ENV", "mainnet"))

    config = {
        "apiKey": (params.apiKey if params and params.apiKey else os.getenv("WOO_API_KEY")),
        "secret": (params.apiSecret if params and params.apiSecret else os.getenv("WOO_API_SECRET")),
        "accountId": (params.accountId if params and params.accountId else os.getenv("WOO_ACCOUNT_ID")),
        "brokerId": (params.brokerId if params and params.brokerId else os.getenv("WOO_BROKER_ID", "woofi_pro")),
        "enableRateLimit": True,
    }
    if env == "testnet":
        # Переопределяем только в тестнете
        config["urls"] = {
            "api": {
                "public": "https://testnet-api-evm.orderly.org",
                "private": "https://testnet-api-evm.orderly.org",
            }
        }
    return klass(config)


def make_exchange_ws(params: ExchangeParams | None = None):
    if not HAS_CCXTPRO:
        raise RuntimeError("ccxt.pro not installed")
    env = (params.env if params and params.env else os.getenv("WOO_ENV", "mainnet"))
    klass = getattr(ccxtpro, EXCHANGE_ID, None)
    if not klass:
        raise RuntimeError(f"Exchange '{EXCHANGE_ID}' not found in ccxt.pro")

    config = {
        "apiKey": (params.apiKey if params and params.apiKey else os.getenv("WOO_API_KEY")),
        "secret": (params.apiSecret if params and params.apiSecret else os.getenv("WOO_API_SECRET")),
        "accountId": (params.accountId if params and params.accountId else os.getenv("WOO_ACCOUNT_ID")),
        "brokerId": (params.brokerId if params and params.brokerId else os.getenv("WOO_BROKER_ID", "woofi_pro")),
        "enableRateLimit": True,
    }
    if env == "testnet":
        config["urls"] = {
            "api": {
                "public": "https://testnet-api-evm.orderly.org",
                "private": "https://testnet-api-evm.orderly.org",
            }
        }
    return klass(config)

# ------------ ENDPOINTS ------------

@app.get("/health")
async def health():
    return {"status": "ok", "ccxtpro": HAS_CCXTPRO, "exchange": EXCHANGE_ID}

@app.get("/whoami", dependencies=[Depends(verify_api_key)])
async def whoami(params: ExchangeParams = Depends()):
    return {
        "exchange": EXCHANGE_ID,
        "env": params.env or os.getenv("WOO_ENV", "mainnet"),
        "apiKey": params.apiKey or os.getenv("WOO_API_KEY"),
        "accountId": params.accountId or os.getenv("WOO_ACCOUNT_ID"),
        "brokerId": params.brokerId or os.getenv("WOO_BROKER_ID", "woofi_pro"),
    }

@app.get("/markets", dependencies=[Depends(verify_api_key)])
async def get_markets(params: ExchangeParams = Depends()):
    ex = make_exchange_rest(params)
    try:
        mkts = ex.load_markets()
        return {"symbols": list(mkts.keys()), "markets": mkts}
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.get("/balance", dependencies=[Depends(verify_api_key)])
async def get_balance(params: ExchangeParams = Depends()):
    ex = make_exchange_rest(params)
    try:
        return ex.fetch_balance()
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.get("/positions", dependencies=[Depends(verify_api_key)])
async def get_positions(
    params: ExchangeParams = Depends(),
    symbol: str | None = None,
    only_open: bool = True
):
    ex = make_exchange_rest(params)
    try:
        positions = ex.fetch_positions()
        if symbol:
            positions = [p for p in positions if p.get("symbol") == symbol]
        if only_open:
            positions = [p for p in positions if abs(float(p.get("contracts", 0) or 0)) > 0]
        return {"count": len(positions), "positions": positions}
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.put("/positions/{symbol:path}/leverage", dependencies=[Depends(verify_api_key)])
async def set_leverage(symbol: str, body: LeverageReq, params: ExchangeParams = Depends()):
    ex = make_exchange_rest(params)
    try:
        warning = None
        if body.marginMode:
            try:
                ex.set_margin_mode(body.marginMode, symbol=symbol, params={})
            except AttributeError:
                warning = "set_margin_mode не реализован; продолжаем в режиме по умолчанию"
            except NotSupported:
                warning = "Биржа не поддерживает смену режима маржи; продолжаем в режиме по умолчанию"
            except Exception as e:
                warning = f"set_margin_mode failed: {e}"

        lev = int(body.leverage)
        try:
            result = ex.set_leverage(lev, symbol=symbol, params={})
        except AttributeError:
            raise HTTPException(status_code=400, detail="set_leverage не поддерживается этим адаптером CCXT")
        except Exception as e:
            raise HTTPException(status_code=400, detail=str(e))

        response = {"symbol": symbol, "requestedLeverage": lev, "result": result}
        if body.marginMode is not None:
            response["requestedMarginMode"] = body.marginMode
        if warning:
            response["warning"] = warning
        return response
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.post("/order", dependencies=[Depends(verify_api_key)])
async def create_order(req: OrderReq, params: ExchangeParams = Depends()):
    ex = make_exchange_rest(params)
    try:
        p = dict(req.params or {})
        if req.clientOrderId:
            p["clientOrderId"] = req.clientOrderId
        order = ex.create_order(req.symbol, req.type, req.side, req.amount, req.price, p)
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
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.post("/cancel", dependencies=[Depends(verify_api_key)])
async def cancel_order(symbol: str, order_id: str, params: ExchangeParams = Depends()):
    ex = make_exchange_rest(params)
    try:
        res = ex.cancel_order(order_id, symbol)
        return {"canceled": True, "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.get("/orders", dependencies=[Depends(verify_api_key)])
async def get_orders(
    params: ExchangeParams = Depends(),
    symbol: Optional[str] = None,
    status: str = "open",
    limit: Optional[int] = 50,
    since: Optional[int] = None
):
    ex = make_exchange_rest(params)
    try:
        status = status.lower()
        if status == "open":
            orders = ex.fetch_open_orders(symbol, since=since, limit=limit)
        elif status == "closed":
            orders = ex.fetch_closed_orders(symbol, since=since, limit=limit)
        elif status == "all":
            orders = ex.fetch_orders(symbol, since=since, limit=limit)
        else:
            raise HTTPException(status_code=400, detail="status must be one of: open|closed|all")
        return {"status": status, "count": len(orders), "orders": orders}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        if hasattr(ex, "close"):
            ex.close()

# ---- PATCH: allow slashes in symbol ----
@app.get("/ticker/{symbol:path}", dependencies=[Depends(verify_api_key)])
async def get_ticker(symbol: str, params: ExchangeParams = Depends()):
    ex = make_exchange_rest(params)
    try:
        t = ex.fetch_ticker(symbol)
        return {
            "symbol": symbol,
            "last": t.get("last"),
            "bid": t.get("bid"),
            "ask": t.get("ask"),
            "timestamp": t.get("timestamp"),
            "info": t.get("info")
        }
    finally:
        if hasattr(ex, "close"):
            ex.close()

@app.get("/stream/ticker", dependencies=[Depends(verify_api_key)])
async def stream_ticker(symbol: str, interval_ms: int = 1000, params: ExchangeParams = Depends()):
    async def event_generator():
        if HAS_CCXTPRO:
            exws = make_exchange_ws(params)
            try:
                while True:
                    t = await exws.watch_ticker(symbol)
                    yield f"data: {t}\n\n"
            finally:
                await exws.close()
        else:
            ex = make_exchange_rest(params)
            try:
                while True:
                    t = ex.fetch_ticker(symbol)
                    yield f"data: {t}\n\n"
                    await asyncio.sleep(max(0.05, interval_ms / 1000))
            finally:
                if hasattr(ex, "close"):
                    ex.close()

    return StreamingResponse(event_generator(), media_type="text/event-stream")

# ---- NEW: get order by id ----
@app.get("/order/{order_id}", dependencies=[Depends(verify_api_key)])
async def get_order_by_id(
    order_id: str,
    symbol: str,                         # обязателен для woofipro
    params: ExchangeParams = Depends(),
):
    ex = make_exchange_rest(params)
    try:
        return ex.fetch_order(order_id, symbol)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        if hasattr(ex, "close"):
            ex.close()

# ---- FIXED: get current leverage for a symbol ----
@app.get("/positions/{symbol:path}/leverage", dependencies=[Depends(verify_api_key)])
async def get_leverage(
        symbol: str,
        include_tiers: bool = False,
        params: ExchangeParams = Depends(),
):
    """
    Возвращает текущее плечо по символу.

    Логика:
      1) Загружаем маркеты (нужно для маппинга unified→id).
      2) Пытаемся вызвать implicit API: v1PrivateGetClientLeverage({"symbol": <market_id>}).
      3) Если нет/ошибка — fallback через fetch_positions().
      4) (опционально) подтягиваем leverage tiers, если поддерживается.
    """
    ex = make_exchange_rest(params)
    try:
        # 1) markets
        market_id = symbol
        try:
            ex.load_markets()  # ВАЖНО: обязателен перед ex.market(...)
            if hasattr(ex, "market"):
                m = ex.market(symbol)
                market_id = m.get("id", symbol)
        except Exception:
            # Не критично: попробуем продолжить, используя symbol как есть
            pass

        leverage_value = None
        source = None
        raw = None
        warning = None

        # 2) Прямая попытка через implicit API Orderly (если метод есть в адаптере)
        try:
            if hasattr(ex, "v1PrivateGetClientLeverage"):
                raw = ex.v1PrivateGetClientLeverage({"symbol": market_id})
                data = (raw or {}).get("data") or {}
                lev_str = data.get("leverage")
                if lev_str is not None:
                    leverage_value = int(float(lev_str))
                    source = "private.client.leverage"
            else:
                warning = "Implicit API v1PrivateGetClientLeverage is not available for this adapter"
        except Exception as e:
            warning = f"client leverage fetch failed: {e}"

        # 3) Fallback: через позиции
        if leverage_value is None:
            try:
                positions = ex.fetch_positions()
                pos = next((p for p in positions if p.get("symbol") == symbol), None)
                if pos:
                    if pos.get("leverage") is not None:
                        leverage_value = int(float(pos["leverage"]))
                        source = "fetch_positions.unified"
                        raw = pos
                    else:
                        info = pos.get("info") or {}
                        if info.get("leverage") is not None:
                            leverage_value = int(float(info["leverage"]))
                            source = "fetch_positions.info"
                            raw = pos
                if leverage_value is None and warning is None:
                    warning = "No leverage found in positions for this symbol"
            except Exception as e:
                warning = (warning + f"; positions fallback failed: {e}") if warning else f"positions fallback failed: {e}"

        # 4) (опционально) тировые лимиты
        tiers = None
        if include_tiers:
            try:
                if hasattr(ex, "fetch_market_leverage_tiers"):
                    tiers = ex.fetch_market_leverage_tiers(symbol)
                elif hasattr(ex, "fetch_leverage_tiers"):
                    all_tiers = ex.fetch_leverage_tiers([symbol])
                    tiers = all_tiers.get(symbol) if isinstance(all_tiers, dict) else all_tiers
                else:
                    warning = (warning + "; leverage tiers not supported") if warning else "leverage tiers not supported"
            except Exception as e:
                warning = (warning + f"; leverage tiers fetch failed: {e}") if warning else f"leverage tiers fetch failed: {e}"

        if leverage_value is None:
            raise HTTPException(status_code=404, detail=warning or "Leverage not found")

        resp = {
            "symbol": symbol,
            "leverage": leverage_value,
            "source": source,
        }
        if warning:
            resp["warning"] = warning
        if include_tiers:
            resp["tiers"] = tiers
        return resp

    finally:
        if hasattr(ex, "close"):
            ex.close()