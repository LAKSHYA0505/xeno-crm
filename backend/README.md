# SoleStreet CRM Intelligence — Backend

AI-native campaign orchestration and simulator-backed delivery workflow for modern D2C footwear brands.

## Overview

This repository contains two core components:

- **CRM Backend Engine** — Spring Boot + Java + JPA/Hibernate with PostgreSQL.
- **Channel Service** — Node.js simulator for asynchronous carrier delivery callbacks.

## Architecture

The system is designed as a decoupled, event-driven loop:

1. A campaign is launched and campaign logs are persisted.
2. After the database transaction commits, the async sender pool dispatches messages to the Channel Service.
3. The Channel Service simulates delivery events and emits webhook callbacks.
4. The CRM receipt endpoint ingests callbacks and updates campaign log state.

```text
[CRM API Client] ──(Launch)──> [Spring Boot Transaction]
                               │
                               ▼ (afterCommit)
              [Async Sender Pool] ──(HTTP 202)──> [Channel Service]
                       │                             │
                       ▼                             ▼
            [PostgreSQL campaign_logs]      [Delivery simulation flows]
                       ▲                             │
                       │                             ▼
    [CRM Receipt Endpoint] <──(Webhook payload)──┘
                       │
                       ▼
        [PostgreSQL campaign_events]
```

## Why this works

### 1. Safe async dispatch after commit

Campaign logs are persisted inside a transaction and only dispatched after the transaction successfully commits.

This prevents race conditions where a callback arrives before the launch transaction has finished.

### 2. Fast analytics with grouped SQL

Campaign metrics avoid repeated status counts by using a single grouped query.

Example:

```java
@Query("SELECT cl.status, COUNT(cl) FROM CampaignLog cl WHERE cl.campaign.id = :campaignId GROUP BY cl.status")
List<Object[]> getCampaignStatusCounts(@Param("campaignId") UUID campaignId);
```

### 3. Optimal SQL generation for AI-driven rules

The segment rules engine splits filters into:

- `WHERE` for scalar fields like `city` and `gender`
- `HAVING` for aggregated metrics like `total_spent` or `order_count`

This improves PostgreSQL execution planning and keeps query performance predictable.

### 4. Secure parameter binding

Dynamic query generation uses safe binding instead of string interpolation.

Example:

```java
params.forEach(query::setParameter);
```

### 5. Channel Service throttling

The simulator uses keep-alive connections and a concurrency cap to avoid overloading the CRM.

## API Endpoints

### Segments

- `POST /api/segments/parse` — Parse natural language into structured segment rules.
- `POST /api/segments` — Save a segment definition.
- `GET /api/segments` — List saved segments.

### Campaigns

- `POST /api/campaigns` — Create a campaign in `DRAFT` status.
- `POST /api/campaigns/{id}/launch` — Launch a campaign and dispatch messages.
- `GET /api/campaigns/{id}` — Fetch campaign details and metrics.
- `POST /api/campaigns/{id}/ai-summary` — Generate an ROI-focused campaign analysis.

### Webhook receiver

- `POST /api/receipt` — Receive delivery callback events and update campaign status.

## Scale assumptions and trade-offs

- The backend is optimized for fast campaign analytics and safe async dispatch.
- Large audiences are currently processed in batch chunks of around 200 IDs.
- For enterprise-scale traffic, streaming batch processing would replace large single `IN (...)` queries.
- Copy personalization is done with local token substitution, not per-message LLM calls.

## Setup

### Prerequisites

- Java 17+
- Maven
- Node.js v18+
- PostgreSQL

### 1. Create the database

```sql
CREATE DATABASE crm_db;
```

### 2. Configure the CRM backend

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/crm_db
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD

groq.api.key=YOUR_GROQ_API_KEY
groq.api.url=https://api.groq.com/openai/v1/chat/completions
channel.service.url=http://localhost:9090
server.port=8087
```

Run the backend:

```bash
mvn clean spring-boot:run
```

### 3. Run the Channel Service

From the channel-service folder:

```bash
npm install express axios
node channel-service.js
```

The service listens on port `9090` by default.

## Notes

- The design prioritizes safe transaction boundaries, predictable analytics, and an independent simulator for callback testing.
- Replace the demo Channel Service with a real carrier integration for production.
- Use cursor-based streaming for very large customer audiences.
