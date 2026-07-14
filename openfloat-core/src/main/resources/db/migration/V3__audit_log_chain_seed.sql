-- =====================================================================
-- OpenFloat M-Pesa Middleware Platform
-- Seed Genesis Audit Log Record
-- =====================================================================

INSERT INTO audit_logs (id, username, action, resource, details, ip_address, timestamp, hash)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'system',
    'GENESIS',
    'SYSTEM',
    'Genesis record to bootstrap the cryptographic audit log chain',
    '127.0.0.1',
    '2026-01-01T00:00:00Z',
    '0000000000000000000000000000000000000000000000000000000000000000'
);
