# SoleStreet CRM — AI-Native Mini CRM

An AI-native mini CRM that helps a D2C footwear brand (SoleStreet) decide **who** to reach, **what** to say, **which channel** to use, and **what to do next** — with AI woven into every stage of the campaign loop, not bolted on.

The marketer never writes a filter or hand-edits copy: they describe intent in natural language and approve AI-proposed segments, messages, channels, and follow-ups. Under the hood it is a two-service, callback-driven delivery pipeline with conscious handling of out-of-order callbacks, idempotent conversions, retries, and a concurrency-capped async sender.

## Repository Layout

```text
xeno-crm/
├── backend/
│   ├── crm-backend/        — Spring Boot CRM (core service)
│   └── channel-service/    — Node.js stubbed carrier simulator
└── frontend/               — React + Vite + Tailwind UI
```

This README documents both backend services:

1. [CRM Backend Service](#1-crm-backend-service)
2. [Channel Service (Stubbed Carrier Simulator)](#2-channel-service-stubbed-carrier-simulator)

---

# 1. CRM Backend Service

AI-native campaign intelligence backend for a D2C footwear brand. Built with Spring Boot, PostgreSQL, and Groq AI.

## What This Service Does

The CRM backend is the core of SoleStreet. It handles:

- **Customer and order data** — 800 seeded customers with realistic purchase history across 20 Indian cities
- **AI-powered segmentation** — natural language queries translated to parameterized SQL via Groq LLaMA 3.3
- **Campaign lifecycle** — create, launch, track, and analyze campaigns
- **Async message dispatch** — fires messages to the channel service on a Spring thread pool, non-blocking
- **Receipt processing** — ingests async delivery callbacks and updates message state via a monotonic state machine
- **AI message drafting, refining, and follow-up recommendation** — three distinct Groq calls woven into the campaign loop

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Framework | Spring Boot 3.5 + Java 21 | Strong async support, JPA, familiarity |
| Database | PostgreSQL 15 | JSONB for order items and segment rules, native aggregation |
| AI | Groq API (LLaMA 3.3 70B) | Sub-second inference, free tier, OpenAI-compatible |
| HTTP Client | RestTemplate | Synchronous Groq calls, simple to reason about |
| Async | Spring @Async + thread pool | Decouples campaign send from HTTP request lifecycle |
| Deploy | Railway | Managed Postgres + service hosting in one place |

## Architecture Decision: Two-Service Callback Loop

The most important design decision in this system is the separation of the CRM and the channel service.

```text
CRM Backend  ──────POST /send──────►  Channel Service
CRM Backend  ◄───POST /api/receipt───  Channel Service
```

When a campaign launches:

1. CRM creates one `campaign_log` row per customer (status: `queued`)
2. `AsyncSenderService.sendAll()` fires on a Spring `@Async` thread pool — the HTTP request returns immediately
3. Channel service receives each message, waits 1–8 seconds, picks a probabilistic outcome
4. Channel service POSTs back to `/api/receipt` with `{logId, status}`
5. Receipt endpoint updates log status via a monotonic state machine

This mirrors how real providers like Twilio and Gupshup work — fire-and-forget with async delivery receipts.

## Segmentation Engine

Natural language queries are translated to SQL by Groq, then executed by a dynamic rules engine.

**Example input:**

```text
"male customers in Delhi who spent over ₹25,000 and bought running shoes"
```

**AI output (JSON rules):**

```json
{
  "operator": "AND",
  "conditions": [
    {"field": "gender", "op": "eq", "value": "male"},
    {"field": "city", "op": "eq", "value": "Delhi"},
    {"field": "total_spent", "op": "gt", "value": "25000"},
    {"field": "product_category", "op": "eq", "value": "running"}
  ]
}
```

**Rules engine decisions:**

- Scalar customer attributes (`city`, `gender`) → `WHERE` clause — filtered at index layer before grouping
- Aggregate order fields (`total_spent`, `order_count`, `last_order_days_ago`) → `HAVING` clause — evaluated on grouped aggregates
- All values are parameterized via `params.forEach(query::setParameter)` — zero SQL injection risk

## State Machine: Campaign Log Status

Status transitions are monotonic — a log can only move forward:

```text
QUEUED → SENT → DELIVERED → OPENED → READ → CLICKED
              ↘ FAILED (terminal from QUEUED or SENT only)
```

A late or out-of-order callback cannot move status backwards. `FAILED` and `CLICKED` are terminal states — once reached, no further updates are accepted.

## AI Integration Points

Three Groq calls power the AI-native loop:

| Call | Endpoint | What it does |
|---|---|---|
| NL → Segment Rules | `POST /api/segments/parse` | Translates plain English to JSON rules |
| Message Draft + Refine | `POST /api/segments/ai-draft`, `POST /api/segments/refine-message` | Drafts and refines personalized WhatsApp/SMS/Email copy |
| Campaign Analysis + Follow-up | `POST /api/campaigns/{id}/ai-summary`, `GET /api/campaigns/{id}/follow-up-recommendation` | Summarizes performance, identifies non-openers, recommends re-targeting |

All calls go through `AIService.callGroq()` — one method, one place to change prompts, one place to handle errors.

## Key Engineering Decisions

### @Async Self-Invocation Bug

Spring `@Async` uses a proxy. Calling an `@Async` method from within the same bean bypasses the proxy and runs synchronously. **Fix:** moved async send logic to a separate `AsyncSenderService` bean, injected into `CampaignService`.

### Detached Entity Across Transaction Boundaries

Passing `List<CampaignLog>` (JPA entities) to the async thread caused `OptimisticLockingFailureException` — the entities were detached after the parent transaction committed. **Fix:** pass `List<UUID>` instead, and fetch fresh inside the async thread. Each fetch gets its own clean persistence context.

### N+1 Query Prevention

Customer list with stats (order count, total spent, last order date) originally queried orders per customer inside a loop. **Fix:** a single `LEFT JOIN` aggregation query returning a Spring Projection interface — one query regardless of page size.

### WHERE vs HAVING Semantics

Early implementation used `MAX(c.city)` in `HAVING` for city filtering — works by accident but semantically wrong and misleading to anyone reading the code. **Fix:** scalar customer attributes go to `WHERE`, aggregate order metrics go to `HAVING`.

## Database Schema

```text
customers          — id, name, email, phone, city, gender, created_at
orders             — id, customer_id, amount, items (JSONB), channel, ordered_at
segments           — id, name, nl_query, rules (JSONB), customer_count
segment_customers  — segment_id, customer_id (composite PK)
campaigns          — id, name, segment_id, message_template, channel, status, ai_summary
campaign_logs      — id, campaign_id, customer_id, personalized_message, status, retry_count, sent_at
campaign_events    — id, log_id, event_type, occurred_at (append-only event log)
```

`campaign_events` is an append-only log — every status change is recorded as a new row with a timestamp. This enables event timeline views and auditing without overwriting history.

## API Reference

### Customers

```text
GET  /api/customers              — paginated list with search
GET  /api/customers/{id}         — customer detail
GET  /api/customers/{id}/orders  — order history
```

### Segments

```text
POST /api/segments/parse           — NL query → AI rules + preview (main AI entry point)
POST /api/segments/refine-message  — refine message copy via AI
POST /api/segments                 — save segment
GET  /api/segments                 — list all segments
GET  /api/segments/{id}            — segment detail
```

### Campaigns

```text
POST /api/campaigns                                — create campaign
POST /api/campaigns/{id}/launch                    — launch campaign (triggers async send)
GET  /api/campaigns                                — list all campaigns
GET  /api/campaigns/{id}                           — campaign detail + live stats
POST /api/campaigns/{id}/ai-summary                — generate AI performance summary
GET  /api/campaigns/{id}/follow-up-recommendation  — AI follow-up suggestion
POST /api/campaigns/{id}/follow-up                 — launch follow-up to non-openers
POST /api/receipt                                  — channel service callback receiver
```

## Scale Assumptions and Tradeoffs

| What I did | What I'd do at scale |
|---|---|
| Spring `@Async` thread pool for sends | Kafka topic between CRM and channel service — decouples producers and consumers, handles backpressure |
| 4-second frontend polling for live stats | WebSockets or SSE — push updates instead of pull |
| Single `campaign_logs` table | Partition by `campaign_id` or archive completed campaigns — at 1B rows scans become expensive |
| Direct HTTP to channel service | Queue with dead-letter topic — failed callbacks retry automatically without manual intervention |
| In-process batch partitioner (200 IDs per batch) | Cursor-based streaming from PostgreSQL — avoids loading large ID sets into heap memory |

## Running Locally

**Prerequisites:** Java 21, Maven, Docker

```bash
# Start PostgreSQL
docker-compose up -d

# Run schema
docker exec -i xeno-crm-db psql -U postgres -d xeno_crm < init.sql

# Seed data
pip install psycopg2-binary faker
python seed.py

# Start backend
cd backend/crm-backend
./mvnw spring-boot:run
```

**Environment variables (`application.properties`):**

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/xeno_crm
spring.datasource.username=postgres
spring.datasource.password=postgres
groq.api.key=YOUR_GROQ_KEY
groq.api.url=https://api.groq.com/openai/v1/chat/completions
channel.service.url=http://localhost:9090
server.port=8087
```

## Project Structure

```text
crm-backend/
├── config/
│   └── AppConfig.java              — RestTemplate bean
├── controller/
│   ├── CustomerController.java
│   ├── SegmentController.java
│   └── CampaignController.java
├── domain/
│   ├── entity/                     — JPA entities
│   └── enums/                      — CampaignStatus, MessageStatus
├── dto/
│   ├── request/                    — inbound DTOs
│   └── response/                   — outbound DTOs + projections
├── repository/                     — Spring Data JPA interfaces
└── service/
    ├── AIService.java              — all Groq API calls
    ├── AsyncSenderService.java     — @Async campaign dispatch
    ├── CampaignService.java        — campaign lifecycle
    ├── CustomerService.java        — customer queries
    └── SegmentService.java         — rules engine + segmentation
```

---

# 2. Channel Service (Stubbed Carrier Simulator)

A lightweight Node.js service that simulates a real messaging carrier (WhatsApp, SMS, Email). It does **not** deliver any real messages — it models the full async delivery lifecycle and fires webhook callbacks back to the CRM.

## Why This Service Exists

Real messaging providers like Twilio, Gupshup, and Meta's WhatsApp Business API work on a fire-and-forget + webhook model:

1. Your system POSTs a message to the provider
2. Provider returns `202 Accepted` immediately
3. Provider asynchronously POSTs delivery receipts to your webhook as events happen

This service faithfully simulates that model. The CRM calls `POST /send`, gets an immediate `202`, and receives callbacks at realistic random delays for each stage of the delivery lifecycle.

## Delivery Simulation

Each message goes through a probabilistic pipeline:

```text
POST /send received
  ↓
Wait 1–5 seconds (network + queuing simulation)
  ↓
Outcome: DELIVERED (85%) or FAILED (15%)
  ↓ (if delivered)
Wait 3–10 seconds (user behavior simulation)
  ↓
Outcome: OPENED (60%) or no engagement (40%)
  ↓ (if opened)
Wait 2–8 seconds
  ↓
Outcome: CLICKED (25%) or no click (75%)
```

All delays use `setTimeout` — fully async, non-blocking. Multiple campaigns can run simultaneously without interference.

## Retry Logic

If a callback to the CRM fails (network error, CRM temporarily down), the service retries once after 3 seconds. This mirrors real carrier retry behavior and tests the CRM's idempotency handling.

```javascript
async function sendCallback(logId, status) {
  try {
    await axios.post(CRM_RECEIPT_URL, { logId, status });
  } catch (err) {
    // Retry once after 3 seconds
    setTimeout(async () => {
      await axios.post(CRM_RECEIPT_URL, { logId, status });
    }, 3000);
  }
}
```

## API

### POST /send

Accepts a message dispatch request. Returns `202 Accepted` immediately. Simulation runs asynchronously in the background.

**Request:**

```json
{
  "logId": "uuid",
  "recipient": "9876543210",
  "message": "Hey Priya, how's your Nike Air Max run?",
  "channel": "whatsapp"
}
```

**Response:**

```json
{
  "message": "Accepted",
  "logId": "uuid"
}
```

### GET /health

```json
{ "status": "ok", "service": "channel-service" }
```

## Callbacks Fired to CRM

The service POSTs to `POST /api/receipt` on the CRM with:

```json
{
  "logId": "uuid",
  "status": "delivered | failed | opened | clicked"
}
```

Callbacks arrive at genuinely different timestamps — not batched, not synchronous. This tests the CRM's ability to handle concurrent out-of-order receipts correctly.

## Running Locally

```bash
cd backend/channel-service
npm install
node index.js
```

Service starts on port 9090.

**Environment variable:**

```bash
CRM_RECEIPT_URL=http://localhost:8087/api/receipt
```

Defaults to `http://localhost:8087/api/receipt` if not set. Set this when deploying to point at the production CRM URL.

## Why Node.js (Not Spring Boot)

The channel service is intentionally simple — ~100 lines of JavaScript. A full Spring Boot JVM would be engineering overkill for a stateless HTTP stub. Node.js's event loop model is also a natural fit for fire-and-forget async simulation with multiple concurrent `setTimeout` chains running in parallel.

This also demonstrates a realistic microservice boundary — two services written in different languages communicating over HTTP, which is closer to real production architectures than two Spring Boot apps on different ports.

## Project Structure

```text
channel-service/
├── index.js      — Express server, /send endpoint, simulation logic
└── package.json
```

## Scale Notes

This stub is intentionally minimal. At production scale a real channel service would need:

- A message queue (Kafka/SQS) to buffer sends and prevent overload
- Persistent delivery state (Redis or DB) to survive restarts
- Rate limiting per channel and recipient
- Dead-letter queue for permanently failed messages
- Signed webhooks to prevent spoofed callbacks

For this assignment scope, stateless in-memory simulation demonstrates the same architectural pattern without the operational complexity.

---

## Notes

- The design prioritizes safe transaction boundaries, predictable analytics, and an independent simulator for callback testing.
- Replace the stubbed channel service with a real carrier integration for production.
- Use cursor-based streaming for very large customer audiences.
