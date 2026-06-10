CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    phone VARCHAR(15),
    city VARCHAR(50),
    total_spent DECIMAL(10,2) DEFAULT 0,
    order_count INT DEFAULT 0,
    last_order_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID REFERENCES customers(id),
    amount DECIMAL(10,2) NOT NULL,
    items JSONB NOT NULL,
    channel VARCHAR(30) DEFAULT 'website',
    ordered_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rules JSONB NOT NULL,
    customer_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    segment_id UUID REFERENCES segments(id),
    message_template TEXT NOT NULL,
    channel VARCHAR(30) DEFAULT 'whatsapp',
    status VARCHAR(20) DEFAULT 'draft',
    launched_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS campaign_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID REFERENCES campaigns(id),
    customer_id UUID REFERENCES customers(id),
    personalized_message TEXT,
    status VARCHAR(20) DEFAULT 'queued',
    sent_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customers_last_order ON customers(last_order_at);
CREATE INDEX IF NOT EXISTS idx_customers_total_spent ON customers(total_spent);
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_ordered_at ON orders(ordered_at);
CREATE INDEX IF NOT EXISTS idx_campaign_logs_campaign_id ON campaign_logs(campaign_id);
CREATE INDEX IF NOT EXISTS idx_campaign_logs_status ON campaign_logs(status);