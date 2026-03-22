# Crypto Trading System

A crypto trading backend built with **Spring Boot 3**, **H2 in-memory database**, and a scheduled price aggregation engine that pulls best bid/ask prices from Binance and Huobi every 10 seconds.

---

## Table of Contents
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Database Schema](#database-schema)
- [How to Run](#how-to-run)
- [API Endpoints](#api-endpoints)
- [Design Decisions](#design-decisions)
- [Assumptions](#assumptions)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.2 |
| Language | Java 21 |
| Database | H2 (in-memory) |
| ORM | Spring Data JPA / Hibernate |
| Build | Maven |
| Testing | JUnit 5, Mockito, AssertJ |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  External Exchanges                  │
│         Binance API          Huobi API               │
└──────────────┬───────────────────┬──────────────────┘
               │  every 10 seconds  │
               ▼                   ▼
┌─────────────────────────────────────────────────────┐
│              PriceScheduler (Spring @Scheduled)      │
│         PriceAggregationService                      │
│   • Best BID = MAX(binance.bid, huobi.bid)           │
│   • Best ASK = MIN(binance.ask, huobi.ask)           │
└──────────────────────┬──────────────────────────────┘
                       │ save
                       ▼
┌─────────────────────────────────────────────────────┐
│                  H2 In-Memory DB                     │
│  users │ wallet │ aggregated_price │ trade           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│               REST API Layer                         │
│  PriceController  TradeController  WalletController  │
└─────────────────────────────────────────────────────┘
```

---

## Database Schema

### `users`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | Auto-increment |
| username | VARCHAR | Unique |
| email | VARCHAR | Unique |
| created_at | TIMESTAMP | |

### `wallet`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| user_id | BIGINT FK | → users |
| currency | VARCHAR(10) | USDT, ETH, BTC |
| balance | DECIMAL(20,8) | |
| updated_at | TIMESTAMP | |

### `aggregated_price`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| symbol | VARCHAR(20) | ETHUSDT, BTCUSDT |
| bid_price | DECIMAL(20,8) | Best bid → for SELL |
| bid_source | VARCHAR(20) | BINANCE or HUOBI |
| ask_price | DECIMAL(20,8) | Best ask → for BUY |
| ask_source | VARCHAR(20) | BINANCE or HUOBI |
| updated_at | TIMESTAMP | |

### `trade`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| user_id | BIGINT FK | → users |
| symbol | VARCHAR(20) | |
| type | VARCHAR(10) | BUY or SELL |
| quantity | DECIMAL(20,8) | Crypto amount |
| price | DECIMAL(20,8) | Execution price |
| total_amount | DECIMAL(20,8) | quantity × price |
| status | VARCHAR(20) | COMPLETED, FAILED |
| remarks | VARCHAR(500) | Human-readable summary |
| created_at | TIMESTAMP | |

---

## How to Run

### Prerequisites
- Java 21+
- Maven 3.8+

### Steps

```bash
# 1. Clone the repository
git clone <repository-url>
cd crypto-trading

# 2. Build
mvn clean package -DskipTests

# 3. Run
mvn spring-boot:run
# OR
java -jar target/crypto-trading-1.0.0.jar
```

### H2 Console (inspect the database)
```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:cryptodb
Username: sa
Password: (empty)
```

### Run Tests
```bash
mvn test
```

---

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

---

### 1. Get Latest Aggregated Prices

**GET** `/api/v1/prices`

Returns best bid/ask for all supported pairs.

```bash
curl http://localhost:8080/api/v1/prices
```

```json
{
  "success": true,
  "data": [
    {
      "symbol": "BTCUSDT",
      "bidPrice": 70642.24,
      "bidSource": "HUOBI",
      "askPrice": 70558.52,
      "askSource": "BINANCE",
      "updatedAt": "2024-01-01 10:00:05"
    },
    {
      "symbol": "ETHUSDT",
      "bidPrice": 3201.00,
      "bidSource": "BINANCE",
      "askPrice": 3200.50,
      "askSource": "HUOBI",
      "updatedAt": "2024-01-01 10:00:05"
    }
  ],
  "timestamp": "2024-01-01T10:00:06"
}
```

---

**GET** `/api/v1/prices/{symbol}`

```bash
curl http://localhost:8080/api/v1/prices/ETHUSDT
```

---

### 2. Execute a Trade

**POST** `/api/v1/trades`

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "symbol": "ETHUSDT",
    "type": "BUY",
    "quantity": 0.5
  }'
```

```json
{
  "success": true,
  "message": "Trade executed successfully",
  "data": {
    "tradeId": 1,
    "userId": 1,
    "symbol": "ETHUSDT",
    "type": "BUY",
    "quantity": 0.5,
    "price": 3001.00,
    "totalAmount": 1500.50,
    "status": "COMPLETED",
    "remarks": "BUY 0.5 ETH at price 3001.00",
    "createdAt": "2024-01-01 10:00:10"
  },
  "timestamp": "2024-01-01T10:00:10"
}
```

**SELL example:**
```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "symbol": "BTCUSDT",
    "type": "SELL",
    "quantity": 0.01
  }'
```

**Error — Insufficient balance:**
```json
{
  "success": false,
  "message": "Insufficient USDT balance. Required: 300100.00, Available: 50000.00",
  "timestamp": "2024-01-01T10:00:15"
}
```

---

### 3. Get Wallet Balance

**GET** `/api/v1/wallet?userId=1`

```bash
curl http://localhost:8080/api/v1/wallet?userId=1
```

```json
{
  "success": true,
  "data": [
    { "walletId": 1, "userId": 1, "currency": "USDT", "balance": 48499.50, "updatedAt": "2024-01-01 10:00:10" },
    { "walletId": 2, "userId": 1, "currency": "ETH",  "balance": 0.50,     "updatedAt": "2024-01-01 10:00:10" },
    { "walletId": 3, "userId": 1, "currency": "BTC",  "balance": 0.00,     "updatedAt": "2024-01-01 10:00:00" }
  ],
  "timestamp": "2024-01-01T10:00:20"
}
```

---

### 4. Get Trade History

**GET** `/api/v1/trades?userId=1`

```bash
# All trades
curl "http://localhost:8080/api/v1/trades?userId=1"

# Paginated
curl "http://localhost:8080/api/v1/trades?userId=1&page=0&size=10"

# Filter by pair
curl "http://localhost:8080/api/v1/trades?userId=1&symbol=ETHUSDT"
```

```json
{
  "success": true,
  "data": [
    {
      "tradeId": 1,
      "userId": 1,
      "symbol": "ETHUSDT",
      "type": "BUY",
      "quantity": 0.5,
      "price": 3001.00,
      "totalAmount": 1500.50,
      "status": "COMPLETED",
      "createdAt": "2024-01-01 10:00:10"
    }
  ],
  "timestamp": "2024-01-01T10:00:20"
}
```

---

## Design Decisions

### 1. Pessimistic Locking on Wallet
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Wallet> findByUserIdAndCurrencyWithLock(Long userId, String currency);
```
Prevents race conditions when concurrent trade requests hit the same wallet simultaneously. Without this, two simultaneous BUY orders could both pass the balance check and overdraw the account.

### 2. `@Transactional` on Trade Execution
The entire trade (debit source wallet → credit destination wallet → save trade record) runs in a single database transaction. If any step fails (e.g. insufficient balance), all changes roll back atomically — no partial state is ever committed.

### 3. Scheduler Resilience
Each exchange is fetched in an isolated `try-catch`. If Binance is down, Huobi prices still update, and vice versa. The top-level scheduler method also wraps everything in a catch so the Spring scheduler thread never dies.

### 4. BigDecimal for All Financial Values
`double` / `float` have binary floating-point precision issues that compound over many transactions. All prices, quantities, and balances use `BigDecimal` with `HALF_UP` rounding — the standard for financial systems.

### 5. Consistent API Response Envelope
All endpoints return `ApiResponse<T>` with `success`, `message`, `data`, and `timestamp`. This makes it easy for frontend/mobile clients to handle both success and error cases uniformly.

### 6. Price Aggregation Rules
- **bidPrice** → `MAX(binance.bidPrice, huobi.bid)` — seller gets the best (highest) offer
- **askPrice** → `MIN(binance.askPrice, huobi.ask)` — buyer gets the best (lowest) offer
- If one exchange is unreachable, the other exchange's price is used alone

---

## Assumptions

1. Authentication/authorisation is handled at gateway level — `userId` is passed directly in requests/query params
2. Initial wallet: `50,000 USDT`, `0 ETH`, `0 BTC` per user
3. Only `ETHUSDT` and `BTCUSDT` trading pairs are supported
4. No third-party payment integration — all trades are settled in-memory
5. Single user (id=1) is seeded on startup via `data.sql`
