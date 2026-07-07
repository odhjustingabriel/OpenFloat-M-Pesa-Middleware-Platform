-- =====================================================================
-- OpenFloat M-Pesa Middleware Platform
-- Initial Schema Migration
-- =====================================================================

-- ── Transactions ─────────────────────────────────────────────────────
CREATE TABLE transactions (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id              VARCHAR(100)    UNIQUE,
    conversation_id             VARCHAR(100),
    originator_conversation_id  VARCHAR(100),
    checkout_request_id         VARCHAR(100),
    merchant_request_id         VARCHAR(100),
    transaction_type            VARCHAR(20)     NOT NULL,
    phone_number                TEXT            NOT NULL,
    amount                      NUMERIC(15,2)   NOT NULL,
    paybill                     VARCHAR(20)     NOT NULL,
    account_reference           TEXT,
    description                 TEXT,
    status                      VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    result_code                 INTEGER,
    result_description          TEXT,
    reconciliation_id           VARCHAR(100),
    reconciliation_status       VARCHAR(20)     DEFAULT 'PENDING',
    idempotency_key             VARCHAR(255)    UNIQUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ
);

CREATE INDEX idx_txn_conversation_id     ON transactions(conversation_id);
CREATE INDEX idx_txn_checkout_request_id ON transactions(checkout_request_id);
CREATE INDEX idx_txn_merchant_request_id ON transactions(merchant_request_id);
CREATE INDEX idx_txn_status              ON transactions(status);
CREATE INDEX idx_txn_paybill             ON transactions(paybill);
CREATE INDEX idx_txn_created_at          ON transactions(created_at);

-- ── Callbacks ────────────────────────────────────────────────────────
CREATE TABLE callbacks (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID            NOT NULL REFERENCES transactions(id),
    raw_payload         JSONB           NOT NULL,
    processed_payload   JSONB,
    callback_type       VARCHAR(20),
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cb_transaction_id ON callbacks(transaction_id);
CREATE INDEX idx_cb_received_at    ON callbacks(received_at);

-- ── API Clients ──────────────────────────────────────────────────────
CREATE TABLE api_clients (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       VARCHAR(100)    NOT NULL UNIQUE,
    client_secret   TEXT            NOT NULL,
    client_name     VARCHAR(200),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    rate_limit      INTEGER         DEFAULT 100,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_api_client_client_id ON api_clients(client_id);

-- ── Users ────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100)    NOT NULL UNIQUE,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   TEXT            NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    last_login      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_user_username ON users(username);
CREATE UNIQUE INDEX idx_user_email    ON users(email);

-- ── Audit Logs ───────────────────────────────────────────────────────
CREATE TABLE audit_logs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100)    NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    resource        VARCHAR(255),
    resource_id     VARCHAR(255),
    details         TEXT,
    ip_address      VARCHAR(45),
    timestamp       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    hash            VARCHAR(64)     NOT NULL
);

CREATE INDEX idx_audit_username  ON audit_logs(username);
CREATE INDEX idx_audit_action    ON audit_logs(action);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_resource  ON audit_logs(resource);

-- ── Seed default admin user (password: admin123 — CHANGE IN PRODUCTION) ──
-- BCrypt hash of "admin123"
INSERT INTO users (username, email, password_hash, role, status)
VALUES (
    'admin',
    'admin@openfloat.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ADMIN',
    'ACTIVE'
);
