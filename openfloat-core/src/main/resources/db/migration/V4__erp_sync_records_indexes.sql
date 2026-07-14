-- V4: Add performance indexes for ERP sync records and reconciliation queries
-- ─────────────────────────────────────────────────────────────────────────────

-- Composite index used by ERPDispatchService to look up sync records by transaction
-- and filter by status (e.g., finding FAILED records for DLQ alerting).
CREATE INDEX IF NOT EXISTS idx_erp_sync_transaction_status
    ON erp_sync_records (transaction_id, sync_status);

-- Index to speed up queries by ERP system name (e.g., find all SAP sync records).
CREATE INDEX IF NOT EXISTS idx_erp_sync_erp_system
    ON erp_sync_records (erp_system);

-- Index on synced_at for time-range queries in dashboards and reports.
CREATE INDEX IF NOT EXISTS idx_erp_sync_synced_at
    ON erp_sync_records (synced_at);

-- ── Reconciliation scheduler support ─────────────────────────────────────────

-- Partial index for the nightly reconciliation scheduler:
-- Finds PENDING transactions that have a checkoutRequestId (STK Push) efficiently.
-- The partial predicate matches the WHERE clause in TransactionRepository.
CREATE INDEX IF NOT EXISTS idx_txn_recon_pending_stk
    ON transactions (reconciliation_status, created_at)
    WHERE checkout_request_id IS NOT NULL;
