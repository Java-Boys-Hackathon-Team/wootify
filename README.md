# 🦉 Wootify — умный торговый бот для WOOFi Pro

**Wootify** — это open-source проект, разработанный в рамках конкурса
[**WOOFi ✖ DeFrens ✖ GoMining — Trading-Bot Competition**](https://dzen.ru/a/aMk0SZnSS0UlmR0F).
Наша цель — создать **интеллектуального трейдинг-бота** для торговли perpetual контрактами на **WOOFi Pro**,
ориентируясь на устойчивый **Trading Impact (TI)** и качественное исполнение сделок.

---

## 🚀 Цели проекта

* **Автоматизация торговли** на WOOFi Pro через API.
* **Оптимизация Trading Impact (TI)** с минимизацией искусственных циклов.
* **Гибкость стратегии** — возможность адаптации под разные пары и рыночные условия.
* **Прозрачность и воспроизводимость**: open-source структура, конфигурация через `.env`, Docker и CI/CD.

---

## 🧠 Основная идея

> *Wootify* = «умный» трейдинг с контролем рисков и осознанной экспозицией.

Бот анализирует рыночные данные и ордера WOOFi Pro через публичные API,
и принимает решения по открытию/закрытию позиций, учитывая ликвидность, спред, funding rate и динамику цены.
Логика строится вокруг принципов **реальной торговой активности**, а не искусственного объёма.

---

## ⚙️ Технологический стек

| Компонент         | Технология                                                      |
| ----------------- | --------------------------------------------------------------- |
| Язык              | **Java 21**                                                     |
| Backend Framework | **Spring Boot + Jmix**                                          |
| Сборка            | **Gradle (Groovy DSL)**                                         |
| База данных       | **PostgreSQL**                                                  |
| Контейнеризация   | **Docker + docker-compose**                                     |
| Интеграция        | **WOOFi Pro API**, **Chainlink Data Feeds**, **Ethers.js (v6)** |
| DevOps            | **Docker**, возможна интеграция с **GitHub Actions**            |

---

## 🧩 Архитектура

```
wootify/
 ├── backend/
 │    ├── src/main/java/com/wootify/...
 │    ├── config/         # Конфигурация Spring Boot и Jmix
 │    ├── strategy/       # Логика стратегий торговли
 │    ├── service/        # Сервисы интеграции (API, Chainlink, Oracles)
 │    ├── model/          # Модели данных и ORM-сущности (PostgreSQL)
 │    ├── scheduler/      # Планировщики задач и heartbeat бота
 │    └── utils/          # Утилиты и вспомогательные классы
 ├── docker/
 │    ├── Dockerfile
 │    └── docker-compose.yml
 ├── scripts/
 │    ├── init_db.sql
 │    └── start_local.sh
 ├── .env.example
 ├── build.gradle
 └── README.md
```

---

## 🔑 Основные модули

* **Core Trading Engine** — базовая логика исполнения ордеров на WOOFi Pro.
* **Risk Manager** — ограничения по размерам позиций, стоп-лосс, таймауты, макс. просадка.
* **Metrics Layer** — сбор метрик TI, PnL, latency и health-check.
* **Connector API** — универсальный клиент для взаимодействия с WOOFi и сторонними источниками.
* **Persistence Layer** — PostgreSQL для хранения истории и параметров стратегий.

---

## 🐳 Быстрый старт (локально)

```bash
# 1. Клонируйте репозиторий
git clone git@github.com:Java-Boys-Hackathon-Team/wootify.git
cd wootify

# 2. Создайте .env файл
cp .env.example .env
# Внесите свои ключи и параметры WOOFi API

# 3. Запустите Docker окружение
docker-compose up --build

# 4. Откройте приложение
http://localhost:8080
```

---

## 🧪 Тестирование и симуляция

Для стратегий предусмотрены юнит-тесты и режим симуляции исполнения (off-chain).
Можно протестировать стратегию без реальных сделок через mock API и historical data.

```bash
./gradlew test
```

---

## 🧰 Полезные ссылки

* 📘 [WOOFi Dev Docs](https://learn.woo.org/woofi-docs/)
* 🔗 [Контракты по сетям](https://learn.woo.org/woofi-docs/woofi-dev-docs/references/contract-addresses)
* ⚙️ [sPMM (математика за моделью)](https://learn.woo.org/woofi-docs/woofi-dev-docs/resources/the-math-behind-spmm)
* 📊 [Public Stats API (read-only)](https://fi-api.woo.org/)
* 🧩 [Chainlink Data Feeds](https://docs.chain.link/data-feeds)
* 🧱 [Ethers.js v6](https://docs.ethers.org/)

---

## 🏆 Участие в хакатоне

**Конкурс:** [WOOFi ✖ DeFrens ✖ GoMining — Trading-Bot Competition](https://dzen.ru/a/aMk0SZnSS0UlmR0F)
**Период торговли:** 29 сентября — 31 октября 2025
**Номинации:**

* 🧠 Самый креативный бот
* 🛡 Лучшие риск-контроли
* 🧩 Лучший open-source репозиторий
* 🚀 Rising Builder

---

## 👥 Команда Java Boys

| Участник                | Роль               | Контакт   |
|-------------------------|--------------------|-----------|
| 🧑‍💻 Рустам Зулкарниев | Trading Logic      | @WerderR |
| 🧠 Рустам Гулямов       | Backend / Research | @gulyamovrustam |
| 🧩 Александр Янчий      | API Integration    | @AlexYanchiy_ru |
| 📈 Рустам Курамшин      | Team Lead / DevOps | @KuramshinRustam          |

---

> 💬 *Wootify — меньше спама, больше импакта.*
