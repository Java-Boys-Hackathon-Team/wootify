# WOOFi Pro Thin Bridge (FastAPI + CCXT)

Лёгкий HTTP/WS сервис для доступа к WOOFi Pro через CCXT/CCXT Pro.
Подходит как "мост" для Java-бота: Java обращается к REST/WS эндпоинтам, а сервис общается с биржей.

## Учётные данные

Ключи Orderly передаются заголовками запроса, чтобы не попадать в журналы доступа:

| Заголовок | Значение |
|---|---|
| `X-API-Key` | общий секрет доступа к мосту (`APP_API_KEY`) |
| `X-Woo-Api-Key`, `X-Woo-Api-Secret` | ключ и секрет Orderly |
| `X-Woo-Account-Id` | идентификатор аккаунта Orderly |
| `X-Woo-Env` | `mainnet` или `testnet` |
| `X-Woo-Broker-Id` | необязательно, по умолчанию `woofi_pro` |

Для совместимости принимаются и параметры строки запроса (`apiKey`, `apiSecret`, ...), но так делать не стоит.
Экземпляры биржи кэшируются по набору учётных данных, рынки загружаются один раз.

## API для движка ботов (`/v2`)

- `POST /v2/orders` - создание ордера: `symbol`, `side`, `type`, `amount`, `price`, `clientOrderId`, `reduceOnly`
- `GET /v2/orders/by-client-id/{clientOrderId}?symbol=` - ордер по идентификатору клиента
- `GET /v2/orders/{id}?symbol=` - ордер по идентификатору биржи
- `DELETE /v2/orders/by-client-id/{clientOrderId}?symbol=` - идемпотентная отмена
- `PUT /v2/leverage?symbol=` - плечо

Ответ - нормализованный ордер: `id`, `clientOrderId`, `status` (`open`, `closed`, `canceled`, `rejected`),
`filled`, `average`, `fee`.

## Коды ошибок

Ошибки CCXT переводятся в HTTP-коды по категориям, тело: `{"error_type": "...", "detail": "..."}`.

| Код | Категория |
|---|---|
| 401 | ключи недействительны |
| 404 | ордер не найден |
| 422 | биржа отклонила запрос (средства, параметры) |
| 429 | превышен лимит запросов |
| 503 | сеть или биржа недоступны |
| 502 | прочие ошибки биржи |

## API торгового терминала

`/health`, `/whoami`, `/markets`, `/balance`, `/positions`, `/positions/{symbol}/leverage`, `/order`,
`/order/{id}`, `/cancel`, `/orders`, `/ticker/{symbol}`, `/ohlcv`, `/stream/ticker` (SSE, опрос).

## Запуск (локально)
```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
export APP_API_KEY=dev-bridge-key
export WOO_API_KEY=...; export WOO_API_SECRET=...
uvicorn app:app --host 0.0.0.0 --port 8081 --reload
```

## Docker
```bash
docker build -t woofipro-bridge:local .
docker run --rm -p 8081:8081   -e APP_API_KEY=dev-bridge-key   -e WOO_API_KEY=... -e WOO_API_SECRET=...   woofipro-bridge:local
```

или через `docker-compose`:
```bash
docker compose up --build
```

## Примеры запросов
```bash
curl -H "X-API-Key: dev-bridge-key" http://localhost:8081/health
curl -H "X-API-Key: dev-bridge-key" http://localhost:8081/ticker/BTC/USDT
curl -H "X-API-Key: dev-bridge-key" "http://localhost:8081/ohlcv?symbol=BTC/USDT&timeframe=1m&limit=5"
curl -H "X-API-Key: dev-bridge-key" -H "X-Woo-Env: testnet" -H "X-Woo-Api-Key: ..." \
     -H "X-Woo-Api-Secret: ..." -H "X-Woo-Account-Id: ..." http://localhost:8081/balance

# Создать лимитный ордер
curl -X POST -H "Content-Type: application/json" -H "X-API-Key: dev-bridge-key"   -d '{"symbol":"BTC/USDT","side":"buy","type":"limit","amount":0.001,"price":10000,"clientOrderId":"test-1"}'   http://localhost:8081/order
```

## Примечания
- В проде добавьте rate limit / backoff и мониторинг. Это базовый каркас.
