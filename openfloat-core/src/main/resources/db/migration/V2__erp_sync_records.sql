CREATE TABLE erp_sync_records (
    id UUID PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    erp_system VARCHAR(50) NOT NULL,
    sync_status VARCHAR(20) NOT NULL,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    sync_payload JSONB,
    synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_erp_sync_status ON erp_sync_records(sync_status);
CREATE INDEX idx_erp_sync_transaction_id ON erp_sync_records(transaction_id);
