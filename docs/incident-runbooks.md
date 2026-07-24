# OpenFloat M-Pesa Middleware — Production Incident Runbooks

> **Document Version:** 1.0.0 | **Classification:** Operations & Incident Response Manual

This document outlines standard operating procedures (SOPs) and step-by-step incident runbooks for handling production alerts, outages, and degraded states on the OpenFloat M-Pesa Middleware Platform.

---

## Table of Contents
1. [RUNBOOK-01: ERP Dead Letter Queue (DLQ) Spike](#runbook-01-erp-dead-letter-queue-dlq-spike)
2. [RUNBOOK-02: Safaricom Daraja Token Refresh Failure / Outage](#runbook-02-safaricom-daraja-token-refresh-failure--outage)
3. [RUNBOOK-03: Audit Chain Integrity Mismatch](#runbook-03-audit-chain-integrity-mismatch)
4. [RUNBOOK-04: API Gateway Rate Limit Throttling Spike](#runbook-04-api-gateway-rate-limit-throttling-spike)
5. [RUNBOOK-05: Production Database Point-in-Time Recovery (PITR)](#runbook-05-production-database-point-in-time-recovery-pitr)

---

## RUNBOOK-01: ERP Dead Letter Queue (DLQ) Spike

### Trigger
* **Alert Name:** `DLQMessageSpike`
* **Condition:** `openfloat_erp_dlq_messages_count > 5` for > 5 minutes.
* **Impact:** Payment callbacks received from Safaricom are logged, but ERP synchronization (SAP/Dynamics) is failing repeatedly.

### Immediate Action & Troubleshooting

1. **Check DLQ Queue Status via RabbitMQ Management:**
   ```bash
   kubectl exec -it -n openfloat deployments/rabbitmq -- rabbitmqctl list_queues name messages
   ```

2. **Inspect DLQ Payload & Exception Headers:**
   Query the DLQ consumer log for failure reasons:
   ```bash
   kubectl logs -n openfloat -l app=openfloat-erp-connector --tail=200 | grep "DLQ"
   ```

3. **Common Root Causes & Fixes:**
   * **Cause A: ERP Endpoint Offline/Unreachable:**
     * Verify network connectivity to ERP target host (`ping erp.internal`).
     * Check if target ERP system is undergoing maintenance.
   * **Cause B: Invalid Customer Account Reference format:**
     * Inspect failed message headers for `X-Exception-Message`.
     * Update mapping rule in `openfloat-erp-connector`.

4. **Re-driving DLQ Messages:**
   Once ERP target is restored, trigger manual DLQ message re-drive via Staff Portal or REST API:
   ```bash
   curl -X POST https://api.openfloat.co.ke/api/v1/erp/redrive-dlq \
     -H "Authorization: Bearer ${ADMIN_TOKEN}"
   ```

---

## RUNBOOK-02: Safaricom Daraja Token Refresh Failure / Outage

### Trigger
* **Alert Name:** `DarajaTokenRefreshFailed`
* **Condition:** `increase(daraja_credential_rotation_count_total{status="failure"}[15m]) > 0`
* **Impact:** STK Push, B2C payouts, and Reversal requests fail with `401 Unauthorized` or `502 Bad Gateway`.

### Immediate Action & Troubleshooting

1. **Check Safaricom Daraja API Status:**
   * Test direct connectivity to Daraja OAuth endpoint:
     ```bash
     curl -v https://api.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials \
       -u "${MPESA_CONSUMER_KEY}:${MPESA_CONSUMER_SECRET}"
     ```

2. **Verify Vault Secret Injection:**
   * Check if Vault Agent successfully injected updated credentials into pod secret volume:
     ```bash
     kubectl exec -it -n openfloat deployments/openfloat-core -- cat /vault/secrets/daraja.env
     ```

3. **Force Access Token Cache Eviction:**
   * Evict stale token from Redis to force clean renewal:
     ```bash
     kubectl exec -it -n openfloat deployments/redis -- redis-cli -a "${REDIS_PASSWORD}" DEL "daraja:access_token"
     ```

4. **Failover to Secondary Shortcode / Passkey:**
   * If primaryshortcode is blocked by Safaricom, switch active shortcode in `SettingsPage` or via Vault.

---

## RUNBOOK-03: Audit Chain Integrity Mismatch

### Trigger
* **Alert Name:** `AuditChainMismatch`
* **Impact:** Potential unauthorized database modification or record tampering detected.

### Immediate Action & Troubleshooting

1. **Run Full Audit Chain Integrity Verification:**
   ```bash
   curl -X GET https://api.openfloat.co.ke/api/v1/audit/verify \
     -H "Authorization: Bearer ${ADMIN_TOKEN}"
   ```

2. **Locate Mismatched Record ID:**
   * Identify broken link in hash chain: `hash != SHA256(prev_hash + data)`.

3. **Isolate Compromised Node / Database Account:**
   * Review PostgreSQL query audit logs (`pgaudit`) for direct `UPDATE` or `DELETE` queries on `audit_log` table.
   * Immediately rotate database user password via `./scripts/rotate-seed-passwords.sh`.

---

## RUNBOOK-04: API Gateway Rate Limit Throttling Spike

### Trigger
* **Alert Name:** `RateLimitSpikeWarning`
* **Impact:** Legitimate API clients receiving `429 Too Many Requests`.

### Action
1. **Identify Throttled Client:**
   ```bash
   kubectl logs -n openfloat -l app=openfloat-gateway --tail=500 | grep "HTTP 429"
   ```
2. **Increase Client Quota in Gateway Route Config:**
   Adjust `replenishRate` and `burstCapacity` in `application-prod.yml` or via Staff Portal `SettingsPage`.

---

## RUNBOOK-05: Production Database Point-in-Time Recovery (PITR)

### Trigger
* Catastrophic database failure or corrupt data migration.

### Action
1. **Stop Application Traffic:**
   ```bash
   kubectl scale deployment -n openfloat openfloat-core --replicas=0
   ```
2. **Execute Restore via pgBackRest:**
   ```bash
   pgbackrest --stanza=openfloat_db --delta --type=time "--target=2026-07-24 12:00:00+00" restore
   ```
3. **Verify Database Consistency & Scale Up:**
   ```bash
   kubectl scale deployment -n openfloat openfloat-core --replicas=2
   ```
