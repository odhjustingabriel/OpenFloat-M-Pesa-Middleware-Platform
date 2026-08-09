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
6. [RUNBOOK-06: Multi-Tenant Webhook Dispatch Failure & Redrive](#runbook-06-multi-tenant-webhook-dispatch-failure--redrive)
7. [RUNBOOK-07: B2C Initiator SecurityCredential RSA Encryption Failure](#runbook-07-b2c-initiator-securitycredential-rsa-encryption-failure)
8. [RUNBOOK-08: Bulk Payout Beneficiary Disbursement Batch Failure](#runbook-08-bulk-payout-beneficiary-disbursement-batch-failure)
9. [RUNBOOK-09: Financial PDF / Excel Report Generation Memory Outage](#runbook-09-financial-pdf--excel-report-generation-memory-outage)
10. [RUNBOOK-10: Multi-Tenant Client Suspension & Secret Key Rotation](#runbook-10-multi-tenant-client-suspension--secret-key-rotation)

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

---

## RUNBOOK-06: Multi-Tenant Webhook Dispatch Failure & Redrive

### Trigger
* **Alert Name:** `WebhookDeliveryFailureSpike`
* **Condition:** `increase(openfloat_webhook_delivery_failures_total[15m]) > 10`
* **Impact:** Payment webhooks to registered client applications (e.g. School Portal, E-Commerce App) are timing out or returning HTTP 5xx errors. Payments succeed on M-Pesa, but tenant systems are not updated.

### Immediate Action & Troubleshooting

1. **Check Webhook Delivery Logs:**
   Navigate to the Staff Portal **Webhook Logs** screen (`/webhooks`) or run API query:
   ```bash
   curl -X GET https://api.openfloat.co.ke/api/v1/webhooks/failed \
     -H "Authorization: Bearer ${MANAGER_TOKEN}"
   ```

2. **Inspect Target Server Health & HTTP Response Code:**
   * Review `httpStatus` and `errorMessage` fields in failed webhook records.
   * If target server returned `504 Gateway Timeout` or `500 Internal Error`, contact client app administrator.

3. **Manual Webhook Redrive:**
   Once client application target server connectivity is restored, trigger redrives via Staff Portal or CLI:
   ```bash
   curl -X POST https://api.openfloat.co.ke/api/v1/webhooks/${LOG_ID}/redrive \
     -H "Authorization: Bearer ${MANAGER_TOKEN}"
   ```

---

## RUNBOOK-07: B2C Initiator SecurityCredential RSA Encryption Failure

### Trigger
* **Alert Name:** `B2CSecurityCredentialEncryptionError`
* **Condition:** `increase(openfloat_b2c_encryption_errors_total[5m]) > 0`
* **Impact:** B2C mobile wallet disbursements fail immediately with `500 Internal Server Error` or Safaricom rejections (`Invalid Initiator`).

### Immediate Action & Troubleshooting

1. **Inspect Encryption Logs:**
   ```bash
   kubectl logs -n openfloat -l app=openfloat-core --tail=200 | grep "B2CSecurityUtility"
   ```

2. **Verify Safaricom Public Certificate Validity:**
   * Check expiration date on active public certificate:
     ```bash
     openssl x509 -in openfloat-core/src/main/resources/certs/SandboxCertificate.cer -noout -dates
     ```
   * If certificate has expired, obtain updated `.cer` file from Safaricom Developer Portal, place in `certs/` path, and redeploy pod.

3. **Verify Initiator Password in Vault:**
   * Ensure `OPENFLOAT_MPESA_B2C_INITIATOR_PASSWORD` environment variable or Vault secret path `secret/data/openfloat/b2c` is populated with valid initiator password string.

---

## RUNBOOK-08: Bulk Payout Beneficiary Disbursement Batch Failure

### Trigger
* **Alert Name:** `BulkPayoutHighFailureRate`
* **Condition:** `failedCount / totalCount > 0.20` on any single bulk payout batch.
* **Impact:** High proportion of B2C beneficiary disbursements in a CSV batch failed.

### Immediate Action & Troubleshooting

1. **Check Batch Execution Results:**
   Review batch response details in Staff Portal **Bulk Payouts** screen (`/bulkpayouts`).

2. **Diagnose Failure Causes:**
   * **Invalid MSISDN Format:** Recipient number not formatted as 12-digit `254...`.
   * **Insufficient Utility Float Balance:** Safaricom B2C utility account balance low. Check balance via `GET /api/v1/payments/b2c/balance`.
   * **Recipient Wallet Full / Inactive:** Safaricom customer account restricted.

3. **Re-Submit Failed Items Only:**
   Download failed items CSV from portal, correct invalid MSISDNs, and re-upload failed subset.

---

## RUNBOOK-09: Financial PDF / Excel Report Generation Memory Outage

### Trigger
* **Alert Name:** `JVMHeapUsageHigh` / `ReportGenerationTimeout`
* **Condition:** JVM Heap usage > 85% during `/api/v1/reports/transactions/pdf` or `/excel` export calls.
* **Impact:** Report export requests hanging or core service pod experiencing OOMKilled crashes.

### Immediate Action & Troubleshooting

1. **Restrict Date Range on Report Request:**
   Instruct operations staff to filter transaction exports by narrower date ranges (e.g. maximum 30 days per report) rather than requesting all-time histories.

2. **Verify SXSSF Streaming Configuration:**
   Ensure `ReportService.java` uses streaming `SXSSFWorkbook` with row windowing ($ROW\_WINDOW \le 100$) rather than memory DOM `XSSFWorkbook`.

3. **Increase Pod Memory Limit:**
   If export concurrency exceeds standard limits, scale `limits.memory` in `k8s/pod-resource-hardening.yaml` from 1Gi to 2Gi.

---

## RUNBOOK-10: Multi-Tenant Client Suspension & Secret Key Rotation

### Trigger
* Compromised `webhookSecret` or `apiKey` reported by a registered client application administrator.

### Immediate Action & Troubleshooting

1. **Suspend Client Application:**
   Immediately freeze client traffic via Staff Portal **Client Applications** view (`/clients`) or API:
   ```bash
   curl -X PUT https://api.openfloat.co.ke/api/v1/clients/${CLIENT_ID}/status \
     -H "Authorization: Bearer ${ADMIN_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{"status": "SUSPENDED"}'
   ```

2. **Rotate Webhook Secret & API Keys:**
   Re-generate credentials for target system, communicate new secret securely to client admin, and re-activate status (`ACTIVE`).
