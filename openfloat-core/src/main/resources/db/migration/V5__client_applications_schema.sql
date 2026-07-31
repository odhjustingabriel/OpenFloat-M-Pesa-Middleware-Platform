-- =====================================================================
-- OpenFloat M-Pesa Middleware Platform
-- V5: Multi-Tenant Client Application Schema
-- =====================================================================
-- Adds:
--   client_applications       — registered external systems (websites, apps)
--   account_reference_mappings — dynamic references tying payments to clients
--   webhook_delivery_logs     — outbound webhook dispatch audit trail
-- =====================================================================

-- ── Client Applications ───────────────────────────────────────────────
-- Each row represents an external system (website / mobile app) that
-- registers with the middleware to receive M-Pesa payment notifications.
CREATE TABLE client_applications (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name         VARCHAR(200)    NOT NULL,
    account_prefix      VARCHAR(20)     NOT NULL UNIQUE,   -- e.g. ECOMM, SCH, SHOP
    callback_url        TEXT            NOT NULL,           -- HTTPS endpoint to deliver webhooks
    api_key_hash        TEXT            NOT NULL,           -- SHA-256 hash of issued API key
    webhook_secret      TEXT            NOT NULL,           -- HMAC-SHA256 signing secret (hashed)
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | SUSPENDED
    registered_by       VARCHAR(100)    NOT NULL,           -- username of Admin/Manager who registered
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_client_app_prefix   ON client_applications(account_prefix);
CREATE INDEX        idx_client_app_status   ON client_applications(status);
CREATE INDEX        idx_client_app_created  ON client_applications(created_at);

-- ── Account Reference Mappings ────────────────────────────────────────
-- Each row is a generated, unique Account Reference that maps a pending
-- payment to a specific client application and optional expected amount.
-- The middleware validates incoming C2B payments against this table and
-- dispatches webhooks to the corresponding callback_url.
CREATE TABLE account_reference_mappings (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_reference   VARCHAR(50)     NOT NULL UNIQUE,   -- e.g. ECOMM-8X92K4
    client_app_id       UUID            NOT NULL REFERENCES client_applications(id),
    callback_url        TEXT            NOT NULL,           -- snapshot of URL at generation time
    requested_amount    NUMERIC(15,2),                      -- optional expected amount; NULL = any
    currency            VARCHAR(3)      NOT NULL DEFAULT 'KES',
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | PAID | EXPIRED | CANCELLED
    expires_at          TIMESTAMPTZ     NOT NULL,           -- TTL for this reference
    paid_at             TIMESTAMPTZ,
    transaction_id      UUID            REFERENCES transactions(id),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_acct_ref_reference  ON account_reference_mappings(account_reference);
CREATE INDEX        idx_acct_ref_client     ON account_reference_mappings(client_app_id);
CREATE INDEX        idx_acct_ref_status     ON account_reference_mappings(status);
CREATE INDEX        idx_acct_ref_expires    ON account_reference_mappings(expires_at);
CREATE INDEX        idx_acct_ref_created    ON account_reference_mappings(created_at);

-- ── Webhook Delivery Logs ─────────────────────────────────────────────
-- Full audit trail of every outbound webhook dispatch attempt.
-- Managers can query this table to identify failures and trigger redrives.
CREATE TABLE webhook_delivery_logs (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID            REFERENCES transactions(id),
    client_app_id       UUID            NOT NULL REFERENCES client_applications(id),
    account_reference   VARCHAR(50),
    target_url          TEXT            NOT NULL,
    http_status         INTEGER,                            -- e.g. 200, 404, 500; NULL if no response
    request_payload     JSONB           NOT NULL,
    response_body       TEXT,
    error_message       TEXT,
    attempt_number      INTEGER         NOT NULL DEFAULT 1,
    is_success          BOOLEAN         NOT NULL DEFAULT FALSE,
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_log_txn_id     ON webhook_delivery_logs(transaction_id);
CREATE INDEX idx_webhook_log_client     ON webhook_delivery_logs(client_app_id);
CREATE INDEX idx_webhook_log_success    ON webhook_delivery_logs(is_success);
CREATE INDEX idx_webhook_log_created    ON webhook_delivery_logs(created_at);
