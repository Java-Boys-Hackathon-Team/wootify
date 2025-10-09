# WOOFi Pro Thin Bridge (FastAPI + CCXT)

Лёгкий HTTP/WS сервис для доступа к WOOFi Pro через CCXT/CCXT Pro.
Подходит как "мост" для Java-бота: Java обращается к REST/WS эндпоинтам, а сервис общается с биржей.

## Возможности
- REST:
  - `GET /health` — проверка живости
  - `GET /ticker/{symbol}` — последний тик, bid/ask, timestamp
  - `GET /ohlcv?symbol=BTC/USDT&timeframe=1m&limit=100` — свечи
  - `GET /balance` — балансы аккаунта
  - `POST /order` — создание ордера (market/limit), поддержка clientOrderId
  - `POST /cancel` — отмена ордера
- WebSocket:
  - `GET /stream/ticker?symbol=BTC/USDT` — стрим тиков через SSE (если нет ccxt.pro — будет HTTP-поллинг)
  - `WS /ws/ticker?symbol=BTC/USDT` — WebSocket стрим (требуется ccxt.pro)

## Переменные окружения
- `APP_API_KEY` — ключ вашего клиента для доступа к мосту (простой shared secret). Если не задан, авторизация отключена.
- `WOO_API_KEY`, `WOO_API_SECRET` — API ключи WOOFi Pro / WOO X (если нужны приватные методы).
- `EXCHANGE_ID` — по умолчанию `woofipro`.

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
curl -H "X-API-Key: dev-bridge-key" http://localhost:8081/balance

# Создать лимитный ордер
curl -X POST -H "Content-Type: application/json" -H "X-API-Key: dev-bridge-key"   -d '{"symbol":"BTC/USDT","side":"buy","type":"limit","amount":0.001,"price":10000,"clientOrderId":"test-1"}'   http://localhost:8081/order
```

## Примечания
- Для WebSocket стрима через ccxt.pro требуется пакет `ccxtpro` (коммерческий). Если он недоступен, сервис автоматически использует поллинг.
- В проде добавьте rate limit / backoff и мониторинг. Это базовый каркас.
