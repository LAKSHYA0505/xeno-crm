CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS customers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) UNIQUE NOT NULL,
    phone       VARCHAR(15),
    city        VARCHAR(50),
    gender      VARCHAR(10),
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID REFERENCES customers(id) ON DELETE CASCADE,
    amount      DECIMAL(10,2) NOT NULL,
    items       JSONB NOT NULL,
    channel     VARCHAR(30) DEFAULT 'website',
    ordered_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS segments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    description    TEXT,
    nl_query       TEXT,
    rules          JSONB NOT NULL,
    customer_count INT DEFAULT 0,
    created_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS segment_customers (
    segment_id  UUID REFERENCES segments(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customers(id) ON DELETE CASCADE,
    PRIMARY KEY (segment_id, customer_id)
);

CREATE TABLE IF NOT EXISTS campaigns (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL,
    segment_id       UUID REFERENCES segments(id),
    message_template TEXT NOT NULL,
    channel          VARCHAR(30) DEFAULT 'whatsapp',
    status           VARCHAR(20) DEFAULT 'draft',
    ai_summary       TEXT,
    launched_at      TIMESTAMP,
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS campaign_logs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id          UUID REFERENCES campaigns(id) ON DELETE CASCADE,
    customer_id          UUID REFERENCES customers(id) ON DELETE CASCADE,
    personalized_message TEXT,
    status               VARCHAR(20) DEFAULT 'queued',
    retry_count          INT DEFAULT 0,
    sent_at              TIMESTAMP,
    order_value           DECIMAL(10,2),
    updated_at           TIMESTAMP DEFAULT NOW(),
    UNIQUE(campaign_id, customer_id)
);

CREATE TABLE IF NOT EXISTS campaign_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    log_id      UUID REFERENCES campaign_logs(id) ON DELETE CASCADE,
    event_type  VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id     ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_ordered_at      ON orders(ordered_at);
CREATE INDEX IF NOT EXISTS idx_segment_customers_seg  ON segment_customers(segment_id);
CREATE INDEX IF NOT EXISTS idx_campaign_logs_campaign ON campaign_logs(campaign_id);
CREATE INDEX IF NOT EXISTS idx_campaign_logs_status   ON campaign_logs(status);
CREATE INDEX IF NOT EXISTS idx_campaign_events_log    ON campaign_events(log_id);
CREATE INDEX IF NOT EXISTS idx_campaign_events_type   ON campaign_events(event_type);