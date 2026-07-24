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

## Conclusion

Building OpenFloat synthesized advanced concepts across **backend microservices, security engineering, cryptography, message queues, reactive gateways, frontend SPA development, and cloud-native Kubernetes orchestration**. 

The result is a production-ready, resilient, and enterprise-hardened middleware platform capable of securely scaling M-Pesa payment operations.
