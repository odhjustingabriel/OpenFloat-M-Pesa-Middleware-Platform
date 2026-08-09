# What I Have Learned — Engineering the OpenFloat M-Pesa Middleware Platform

> **Technical Knowledge Distillation** | **Focus Area:** Distributed Systems, Financial Engineering, Cryptography & Cloud Security

---

## Executive Summary

Building the **OpenFloat M-Pesa Middleware Platform** provided deep, hands-on experience in designing a mission-critical, enterprise-grade financial integration platform. This document synthesizes the key technical challenges encountered, architectural paradigms mastered, and advanced engineering concepts implemented throughout the project.

---

## 1. M-Pesa Daraja 2.0 API Nuances & Financial Integration

### A. Dynamic LNMO (Lipa Na M-Pesa Online) Password Generation
One of the core requirements of Safaricom's Daraja STK Push API is computing a dynamic `Password` field for every single transaction query and STK push request.

* **Formula:**
  $$\text{Password} = \text{Base64}\Big(\text{Shortcode} \,||\, \text{Passkey} \,||\, \text{Timestamp}\Big)$$
* **Format Requirements:** The `Timestamp` must strictly follow `yyyyMMddHHmmss` in East Africa Time (EAT, UTC+3).
* **Key Takeaway:** Handled timestamp formatting using `DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"))` to prevent authentication rejections due to server timezone mismatch.

### B. Asynchronous Webhook Decoupling & ID Correlation
Daraja STK Push and B2C APIs do not return immediate payment success/failure outcomes in the synchronous HTTP POST response. Instead, they return a tracking pair:
* `MerchantRequestID` & `CheckoutRequestID` (for STK Push)
* `ConversationID` & `OriginatorConversationID` (for B2C / Reversal)

* **Key Takeaway:** The system initial status must immediately be saved as `PENDING`. When Safaricom posts the asynchronous callback to `/api/v1/mpesa/callbacks/stk`, the `CheckoutRequestID` is used to look up the pending transaction, record the `MpesaReceiptNumber` (e.g., `REC-RBS789XYZ`), update status to `SUCCESS` or `FAILED`, and publish an event to RabbitMQ.

### C. Automated Nightly Reconciliation Logic
When network timeouts occur, M-Pesa callbacks might be delayed or lost. 
* **Key Takeaway:** Implemented `ReconciliationScheduler.java` running at 02:00 UTC. It queries all transactions stuck in `PENDING` for > 24 hours, issues a Daraja Query request (`/mpesa/stkpushquery/v1/query`), updates local database state to `MATCHED` or `MISMATCHED`, and alerts operations if discrepancies exist.

---

## 2. Cryptography & Tamper-Evident Audit Logging

### A. SHA-256 Hash Chaining with Pessimistic Database Locking
To guarantee that audit log entries cannot be modified or deleted directly in the database without detection, I built a blockchain-inspired SHA-256 recursive hash chain.

```
[ Genesis Block: 000...000 ] ──► [ Audit Entry 1: SHA256(Genesis + Data1) ] ──► [ Audit Entry 2: SHA256(Hash1 + Data2) ]
```

* **The Challenge:** Under multi-threaded concurrent requests, two threads reading the "latest" hash simultaneously will compute duplicate parent links, corrupting the chain linearity.
* **The Solution:** Used JPA pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on `AuditLogRepository.findLatestForUpdate()`. This serializes database audit inserts at the SQL row-lock level (`SELECT ... FOR UPDATE`), guaranteeing strict, race-condition-free chain integrity.

### B. Field-Level AES-256-GCM JPA Encryption
Storing raw mobile phone numbers (`MSISDN`) violates PII data protection regulations (such as Kenya's Data Protection Act 2019 and GDPR).
* **The Solution:** Implemented `EncryptedStringConverter.java` using `AES/GCM/NoPadding`.
* **Key Learning:** GCM mode requires a unique 12-byte Initialization Vector (IV) for every encryption pass. The IV is prepended to the ciphertext byte array so that decryption can extract it dynamically.

---

## 3. Event-Driven Architecture & RabbitMQ Reliability

### A. Dead Letter Exchange (DLX) & Dead Letter Queue (DLQ) Topology
When dispatching payment events to enterprise ERP targets (SAP, Oracle, Dynamics 365), temporary ERP downtime must not drop financial events.

```
[ TransactionCompletedEvent ]
              │
              ▼
    ┌──────────────────┐  5 Failed Attempts   ┌──────────────────┐
    │  queue.erp.sync  │ ─────────►────────── │ exchange.dlx     │
    └──────────────────┘  (Nack without req)  └────────┬─────────┘
                                                       │
                                                       ▼
                                              ┌──────────────────┐
                                              │ queue.erp.sync.  │
                                              │ dlq              │
                                              └──────────────────┘
```

* **Key Takeaway:** Configured Spring AMQP stateful retry with exponential backoff (1m, 5m, 25m). Upon retry exhaustion, the consumer issues an explicit `nack(requeue=false)`. RabbitMQ automatically routes dead-lettered messages to `queue.erp.sync.dlq`, where a dedicated listener logs SIEM alerts and enables one-click administrative re-driving.

---

## 4. High-Performance API Gateway & Security

### A. Reactive WebFlux Gateway Engine
Spring Cloud Gateway operates on the reactive Netty engine rather than standard Spring MVC servlet threads.
* **Key Learning:** Implemented custom reactive filters using `GatewayFilter` and `WebFilter` types returning `Mono<Void>`.
* **IP Whitelisting (`IpWhitelistFilter.java`):** Evaluates incoming client remote IP addresses against Safaricom CIDR subnet lists using bitwise netmask checks before allowing requests to touch downstream payment controllers.

### B. Redis Sliding Window Rate Limiting
Implemented multi-stage rate limiting via `RateLimitFilter.java` using Redis atomic commands (`ZADD`, `ZREMRANGEBYSCORE`, `ZCARD`).
* **Key Learning:** Sliding window rate-limiting avoids the "burst boundary" weakness of fixed-window algorithms, ensuring fair client bandwidth allocation.

---

## 5. Modern Frontend Engineering & SPA Architecture

### A. Server-State Caching with TanStack Query (v5)
In the `openfloat-staff-portal` React SPA, managing server state separately from local UI state was critical.
* **Key Learning:** Leveraged TanStack Query `useQuery` and `useMutation` hooks for background refetching, automatic 3-second live status polling during STK push prompts, and instant cache invalidation upon user/setting updates.

### B. OAuth2 PKCE Authentication Flow
For Single Page Applications, storing client secrets in JavaScript code is an extreme security risk.
* **The Solution:** Implemented OAuth2 PKCE (Proof Key for Code Exchange) in `client.ts` and `LoginPage.tsx`. The frontend generates a random high-entropy `code_verifier`, computes `code_challenge = Base64URL(SHA256(verifier))`, and exchanges the authorization code securely without exposing static secrets.

---

## 6. Cloud Native Infrastructure & Hardening

### A. HashiCorp Vault Sidecar Secret Injection
Hardcoding production database passwords or M-Pesa Daraja consumer keys in YAML files is unsafe.
* **Key Learning:** Configured `vault-agent-config.yaml` to authenticate with Kubernetes via ServiceAccounts and render dynamic secret files into `/vault/secrets/` tmpfs in-memory mounts.

### B. Kubernetes Resource Limits & HPA Scaling
* **Key Learning:** Benchmark-tested service workloads with k6 load testing scripts up to 100 Virtual Users (500 RPS). Configured `pod-resource-hardening.yaml` with explicit CPU/Memory `requests` and `limits` to ensure predictable Horizontal Pod Autoscaling (HPA) when CPU usage exceeds 70%.

---

---

## 7. Multi-Tenant Account Reference Routing & Signed Webhooks (Phase 8)

### A. Dynamic Account Reference Resolution Engine
In a multi-tenant payment middleware, multiple client applications (e.g. School Portals, E-Commerce Stores) share a single registered M-Pesa Paybill shortcode.

```
Customer Payment (Paybill: 174379, AccountRef: "SCH-4Y71P9")
                   │
                   ▼
    ┌─────────────────────────────┐
    │  OpenFloat Callback Engine  │
    └──────────────┬──────────────┘
                   │ Extract Prefix "SCH"
                   ▼
    ┌─────────────────────────────┐
    │  Client App DB Registry     │ ──► Maps "SCH" -> Client App ID "1" (School Portal)
    └──────────────┬──────────────┘
                   │
                   ▼
    ┌─────────────────────────────┐
    │  HMAC-SHA256 Signed Webhook │ ──► POST https://school.example.com/api/payment-webhook
    └─────────────────────────────┘
```

* **The Challenge:** Routing incoming C2B payments to the correct destination system without requiring separate Paybills for every client app.
* **The Solution:** Implemented prefix-based account reference resolution in `AccountReferenceService.java`. External systems call `POST /api/v1/references/generate` to reserve an account reference (e.g., `SCH-4Y71P9`). When Safaricom posts a C2B callback, `WebhookDispatcherService.java` extracts the prefix (`SCH`), resolves the target `ClientApp`, updates the reference status to `PAID`, and dispatches a signed webhook.

### B. HMAC-SHA256 Webhook Security & Replay Prevention
Sending unauthenticated webhooks over the public internet exposes client systems to spoofed payment notifications.
* **Key Learning:** Built an HMAC-SHA256 signature scheme in `WebhookDispatcherService.java`:
  $$\text{Signature} = \text{HMAC-SHA256}\Big(\text{WebhookSecret}, \, \text{Timestamp} \,||\, "." \,||\, \text{RawJsonPayload}\Big)$$
* **Headers Included:** `X-OpenFloat-Signature: sha256=<hex_hash>` and `X-OpenFloat-Timestamp: <unix_epoch_ms>`.
* **Replay Protection:** Target client applications verify that `Timestamp` is within a 5-minute threshold to reject replayed webhook captures.

---

## 8. Financial Document Generation & Memory Optimization (Phase 9)

### A. Non-Blocking PDF Statement Generation with OpenPDF
Generating audit-compliant financial PDF statements for transaction histories requires strict layout precision without draining JVM heap space under high concurrency.
* **Key Learning:** Implemented `ReportService.java` using OpenPDF (`com.github.librepdf:openpdf`). Used ByteArrayOutputStream streaming, explicit cell padding, alternating row colors, and header repetition (`table.setHeaderRows(1)`) to cleanly format statement tables up to 10,000 transactions without memory exhaustion.

### B. Memory-Efficient Excel Export via Apache POI SXSSF
Standard DOM-based Excel libraries (such as Apache POI `XSSFWorkbook`) load the entire document tree in memory, causing `OutOfMemoryError` crashes during large exports.
* **Key Learning:** Implemented streaming Excel generation via Apache POI `SXSSFWorkbook` (Streaming Extension for XSSF). `SXSSFWorkbook` maintains a configurable row window (100 rows) in memory and flushes older rows to temporary disk files, allowing multi-gigabyte transaction history exports with a stable 64MB memory footprint.

---

## 9. Safaricom B2C Initiator Password RSA Public Cert Encryption (Phase 9)

### A. PKCS#1 v1.5 RSA Encryption Engine
Safaricom's B2C (Business to Customer) disbursement API requires the `SecurityCredential` parameter to be an RSA-encrypted Base64 string containing the B2C Initiator Password.

```
Initiator Password ──► [ RSA 2048-bit Public Cert (PKCS1v1.5) ] ──► [ Base64 Encode ] ──► SecurityCredential
```

* **Implementation:** Built `B2CSecurityUtility.java` loading Safaricom's X.509 public certificate (`SandboxCertificate.cer` / `ProductionCertificate.cer`).
* **Cipher Transformation:** Uses `Cipher.getInstance("RSA/ECB/PKCS1Padding")` to encrypt the plain initiator password bytes before encoding to Base64.
* **Key Takeaway:** Integrated certificate caching using `CertificateFactory.getInstance("X.509")` to avoid re-parsing the certificate on every B2C disbursement request.

---

## 10. Customer Invoicing Engine & Automated Fulfillment (Phase 9)

### A. Invoice State Machine & Automatic Payment Matching
Implemented customer billing management in `InvoiceService.java` with lifecycle states: `DRAFT`, `ISSUED`, `PAID`, `CANCELLED`.
* **Automated Fulfillment:** When a C2B or STK Push payment callback arrives, the middleware checks if the payment's `accountReference` or `msisdn` matches an outstanding `ISSUED` invoice. Upon a match, the invoice automatically transitions to `PAID`, records `paidAt` and `transactionId`, and records an audit log entry.

---

## 11. Bulk B2C Beneficiary Disbursements & CSV Stream Parsing (Phase 9/10)

### A. Bulk Beneficiary CSV Parser & Partial Failure Resilience
Distributing payouts to hundreds of mobile wallet recipients in a single batch (e.g. employee payroll or vendor disbursements) requires CSV stream parsing and resilient error isolation.
* **Key Learning:** Built `BulkPayoutService.java` and `BulkPayoutsPage.tsx`. Supports uploading CSV files (`msisdn, amount, accountReference, remarks`).
* **Partial Failure Handling:** If 3 out of 100 payments in a batch fail due to invalid MSISDNs or insufficient balance, the system does NOT roll back the entire batch. Instead, it completes the valid 97 payments, records individual `BulkPayoutItemResult` records, and returns a aggregate batch metrics object (`totalCount`, `successfulCount`, `failedCount`, `totalAmount`).

---

## Conclusion

Building OpenFloat synthesized advanced concepts across **backend microservices, security engineering, cryptography, message queues, reactive gateways, frontend SPA development, financial document generation, multi-tenant routing, and cloud-native Kubernetes orchestration**. 

The result is a production-ready, resilient, and enterprise-hardened middleware platform capable of securely scaling M-Pesa payment operations.

