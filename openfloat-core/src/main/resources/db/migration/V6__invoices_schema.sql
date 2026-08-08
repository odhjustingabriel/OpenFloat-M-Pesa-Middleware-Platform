-- ============================================================================
-- V6: Invoices table — Phase 9, Component 3: Invoicing Engine
-- ============================================================================

CREATE TABLE IF NOT EXISTS invoices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number    VARCHAR(50)    NOT NULL UNIQUE,
    customer_name     VARCHAR(200),
    customer_phone    VARCHAR(20)    NOT NULL,
    customer_email    VARCHAR(200),
    amount            NUMERIC(15,2)  NOT NULL,
    amount_paid       NUMERIC(15,2)  NOT NULL DEFAULT 0.00,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'KES',
    account_reference VARCHAR(50),
    description       TEXT,
    due_date          DATE           NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'UNPAID',
    paid_at           TIMESTAMPTZ,
    transaction_id    UUID REFERENCES transactions(id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_inv_number    ON invoices (invoice_number);
CREATE INDEX IF NOT EXISTS idx_inv_status    ON invoices (status);
CREATE INDEX IF NOT EXISTS idx_inv_due_date  ON invoices (due_date);
CREATE INDEX IF NOT EXISTS idx_inv_customer  ON invoices (customer_phone);
CREATE INDEX IF NOT EXISTS idx_inv_acct_ref  ON invoices (account_reference);
CREATE INDEX IF NOT EXISTS idx_inv_created_at ON invoices (created_at);
