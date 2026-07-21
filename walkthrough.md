# OpenFloat M-Pesa Middleware — Walkthrough & Implementation Checklist

> **Status as of 2026-07-20:** Phases 1–5 are fully complete. Phase 6 (API Gateway & Staff Portal) is currently in progress (25% complete).

---

## Overall Progress

```
Phase 1 — Shared Foundation & Infrastructure     ████████████ 100%  ✅
Phase 2 — Core M-Pesa Integration Service        ████████████ 100%  ✅
Phase 3 — Authentication & Security Hardening    ████████████ 100%  ✅
Phase 4 — ERP Connector & Reconciliation         ████████████ 100%  ✅
Phase 5 — Testing & Observability                ████████████ 100%  ✅
Phase 6 — API Gateway & Staff Portal             ███░░░░░░░░░  25%  🟨
Phase 7 — Production Hardening & Go-Live         ░░░░░░░░░░░░   0%  ⬜
```

---

## Phase 1 — Shared Foundation & Infrastructure ✅

### Checklist

- [x] **`IdempotencyKeyGenerator.java`** — Deterministic SHA-256 idempotency keys
  - File: [IdempotencyKeyGenerator.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-common/src/main/java/com/openfloat/mpesa/common/util/IdempotencyKeyGenerator.java)

- [x] **`HashUtils.java`** — Null-safe SHA-256 chain hashing + `GENESIS_HASH` constant
  - File: [HashUtils.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-common/src/main/java/com/openfloat/mpesa/common/util/HashUtils.java)

- [x] **`V3__audit_log_chain_seed.sql`** — Genesis audit log entry (zeroed hash)
  - File: [V3__audit_log_chain_seed.sql](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/resources/db/migration/V3__audit_log_chain_seed.sql)

- [x] **`docker-compose.yml`** — Mailpit SMTP stub added
  - File: [docker-compose.yml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/docker-compose.yml)

---

## Phase 2 — Core M-Pesa Integration Service ✅

### Checklist

- [x] **`DarajaTokenManager.java`** + **`DarajaClient.java`** — Redis token caching + 401 auto-refresh
  - Files: [DarajaTokenManager.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/integration/mpesa/DarajaTokenManager.java) · [DarajaClient.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/integration/mpesa/DarajaClient.java)

- [x] **`StkPushService.java`** — Persist PENDING → call Daraja → idempotency → error mapping
  - File: [StkPushService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/service/StkPushService.java)

- [x] **`B2CService.java`** — Full B2C disbursement flow
  - File: [B2CService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/service/B2CService.java)

- [x] **`ReversalService.java`** — Validates SUCCESS state before reversing
  - File: [ReversalService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/service/ReversalService.java)

- [x] **`C2BService.java`** — `registerUrls()` + `simulate()`
  - File: [C2BService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/service/C2BService.java)

- [x] **`MpesaCallbackController.java`** — IP whitelist + raw persist + dedup (all 4 callbacks)
  - File: [MpesaCallbackController.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/controller/MpesaCallbackController.java)

- [x] **`CallbackService.java`** — Status update + `REC-{receipt}` reconciliation ID + event publish
  - File: [CallbackService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/service/CallbackService.java)

- [x] **`IdempotencyService.java`** + **`GlobalExceptionHandler.java`** — Redis+DB dedup → 409
  - Files: [IdempotencyService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/service/IdempotencyService.java) · [GlobalExceptionHandler.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-common/src/main/java/com/openfloat/mpesa/common/exception/GlobalExceptionHandler.java)

---

## Phase 3 — Authentication & Security Hardening ✅

### Checklist

#### OAuth2 Authorization Server (`openfloat-auth`)

- [x] **`AuthorizationServerConfig.java`** — Role claim + introspection + M2M system roles
  - File: [AuthorizationServerConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-auth/src/main/java/com/openfloat/mpesa/auth/config/AuthorizationServerConfig.java)

- [x] **`SecurityConfig.java`** (auth) — LDAP conditional + admin protection
  - File: [SecurityConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-auth/src/main/java/com/openfloat/mpesa/auth/config/SecurityConfig.java)

- [x] **`LdapConfig.java`** — CREATED: `LdapContextSource` + `LdapTemplate` beans
  - File: [LdapConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-auth/src/main/java/com/openfloat/mpesa/auth/config/LdapConfig.java)

- [x] **`UserController.java`** — `PUT /users/{id}/status` + `GET /users/{id}`
  - File: [UserController.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-auth/src/main/java/com/openfloat/mpesa/auth/controller/UserController.java)

#### Resource Server Security (`openfloat-core`)

- [x] **`SecurityConfig.java`** (core) — JWT decoder + RBAC `@PreAuthorize` on all endpoints
  - File: [SecurityConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/security/SecurityConfig.java)

- [x] **`RateLimitFilter.java`** — Per-client Redis sliding window, 429 + Retry-After, callback exclusions
  - File: [RateLimitFilter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/security/RateLimitFilter.java)

- [x] **`EncryptedStringConverter.java`** — AES-256-GCM JPA converter on phoneNumber + accountReference
  - File: [EncryptedStringConverter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/security/EncryptedStringConverter.java)

#### Tamper-Evident Audit Logging

- [x] **`AuditAspect.java`** — `@Around` captures principal + method args → async persist
  - File: [AuditAspect.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/audit/AuditAspect.java)

- [x] **`AuditService.java`** — `@Async` + `SELECT FOR UPDATE` pessimistic lock
  - File: [AuditService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/audit/AuditService.java)

- [x] **`AuditLogRepository.java`** — `findLatestForUpdate()` with `@Lock(PESSIMISTIC_WRITE)`
  - File: [AuditLogRepository.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/repository/AuditLogRepository.java)

- [x] **`OpenFloatCoreApplication.java`** — `@EnableAsync` + `@EnableScheduling`
  - File: [OpenFloatCoreApplication.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/OpenFloatCoreApplication.java)

- [x] **`AuditIntegrityController.java`** — CREATED: `GET /api/v1/audit/verify` (ADMIN only)
  - File: [AuditIntegrityController.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/controller/AuditIntegrityController.java)

---

## Phase 4 — ERP Connector & Reconciliation ✅

### Key Implementation Highlights

#### RabbitMQ DLX/DLQ Topology
The `AmqpConfig` declares a complete dead-letter topology:
- **Main queue** (`queue.erp.sync`) is bound to `exchange.transaction.completed` and declares `x-dead-letter-exchange` pointing at `exchange.transaction.dlx`.
- Spring AMQP's stateful retry (5 attempts, 1m→5m→25m backoff in `application.yml`) **nacks without requeue** after exhaustion, triggering RabbitMQ's dead-lettering.
- **DLQ** (`queue.erp.sync.dlq`) is bound to `exchange.transaction.dlx` and monitored by a dedicated `@RabbitListener` that emits a structured `ALERT` log for SIEM.

#### Dynamics 365 OAuth2 Client Credentials Flow
`DynamicsAdapter` implements a full in-process OAuth2 CC token acquisition:
- Calls `https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token` with form-encoded `client_credentials` grant.
- Caches the access token with a 60-second safety margin before expiry.
- Auto-refreshes on expiry or `401 Unauthorized` from Business Central.

#### Nightly Reconciliation Scheduler
`ReconciliationScheduler` runs at `0 0 2 * * ?` (02:00 UTC) and:
1. Queries all STK Push transactions with `reconciliationStatus=PENDING` older than 24 hours.
2. For each, calls the Daraja STK Push Query API with a freshly computed LNMO password.
3. Sets outcome: `MATCHED`, `MISMATCHED` (transaction also marked `FAILED`), or `IN_PROGRESS` (retried next run).
4. Logs a structured summary: `total / matched / mismatched / inProgress / errors`.

### Checklist

#### AMQP Infrastructure

- [x] **`AmqpConfig.java`** — Full DLX/DLQ topology: exchanges, queues, bindings declared
  - `exchange.transaction.completed` → `queue.erp.sync` (with DLX args)
  - `exchange.transaction.dlx` → `queue.erp.sync.dlq`
  - File: [AmqpConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/config/AmqpConfig.java)

- [x] **`TransactionEventConsumer.java`** — Primary listener + DLQ monitor listener
  - Primary: `@RabbitListener(queues = "queue.erp.sync")` — re-throws for retry
  - DLQ monitor: `@RabbitListener(queues = "queue.erp.sync.dlq")` — structured ALERT log
  - File: [TransactionEventConsumer.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/listener/TransactionEventConsumer.java)

- [x] **`application.yml`** — Spring AMQP retry: 5 attempts, 1m/5m/25m backoff, nack-on-exhaust
  - File: [application.yml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/resources/application.yml)

#### ERP Dispatch Service

- [x] **`ERPDispatchService.java`** — Adapter strategy routing + `ERPSyncRecord` lifecycle management
  - Resolves active adapter by `openfloat.erp.active-adapter` property name
  - Tracks retry count, sets `SUCCESS`/`FAILED` status, stores error message
  - File: [ERPDispatchService.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/service/ERPDispatchService.java)

#### ERP Adapters

- [x] **`SAPAdapter.java`** — RFC 7617 HTTP Basic Auth + BAPI-aligned financial document payload
  - `Authorization: Basic Base64(clientId:clientSecret)`
  - Fields: `documentDate`, `amount`, `currency`, `accountReference`, `mpesaReceipt`, `msisdn`, `transactionType`, `shortcode`
  - File: [SAPAdapter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/adapter/SAPAdapter.java)

- [x] **`OracleAdapter.java`** — Basic Auth + Oracle GL journal import payload
  - `Authorization: Basic Base64(username:api-key)`
  - Fields: `ledgerName`, `source`, `category`, `currency`, `enteredDr`, `referenceText`, `receiptNumber`
  - File: [OracleAdapter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/adapter/OracleAdapter.java)

- [x] **`DynamicsAdapter.java`** — Full OAuth2 CC flow + in-process token caching + 401 retry
  - Acquires token from Microsoft identity platform; cached with 60s safety margin
  - Maps to Business Central General Journal entry format
  - File: [DynamicsAdapter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/adapter/DynamicsAdapter.java)

- [x] **`CustomAdapter.java`** — Generic REST POST with configurable `X-API-Key` header
  - Full `TransactionCompletedEvent` payload forwarded as-is
  - File: [CustomAdapter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-erp-connector/src/main/java/com/openfloat/mpesa/erp/adapter/CustomAdapter.java)

#### STK Push Query Integration (openfloat-core)

- [x] **`StkQueryRequest.java`** — CREATED: Daraja STK Query request DTO with `@JsonProperty` Daraja names
  - File: [StkQueryRequest.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/integration/mpesa/dto/StkQueryRequest.java)

- [x] **`StkQueryResponse.java`** — CREATED: STK Query response DTO with `isTransactionSuccessful()` / `isTransactionFailed()` helpers
  - File: [StkQueryResponse.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/integration/mpesa/dto/StkQueryResponse.java)

- [x] **`DarajaClient.java`** — Added `queryStkPush()` method
  - File: [DarajaClient.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/integration/mpesa/DarajaClient.java)

- [x] **`DarajaConfig.java`** — Added `getStkQueryUrl()` method
  - File: [DarajaConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/integration/mpesa/DarajaConfig.java)

#### Reconciliation Scheduler (openfloat-core)

- [x] **`TransactionRepository.java`** — Added `findPendingReconciliationTransactions()` JPQL query
  - Filters: `reconciliationStatus=PENDING`, `createdAt < cutoff`, `checkoutRequestId IS NOT NULL`
  - File: [TransactionRepository.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/repository/TransactionRepository.java)

- [x] **`ReconciliationScheduler.java`** — CREATED: Nightly 02:00 UTC cron reconciliation job
  - Computes LNMO password (Base64 of ShortCode+Passkey+Timestamp) for each query
  - Outcomes: `MATCHED`, `MISMATCHED` (tx also set `FAILED`), `IN_PROGRESS` (retried next run)
  - Emits structured summary log at end of each run
  - File: [ReconciliationScheduler.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/reconciliation/ReconciliationScheduler.java)

#### Database

- [x] **`V4__erp_sync_records_indexes.sql`** — CREATED: Performance indexes
  - `idx_erp_sync_transaction_status` (composite: `transaction_id, sync_status`)
  - `idx_erp_sync_erp_system` (by ERP system name)
  - `idx_erp_sync_synced_at` (for time-range dashboard queries)
  - `idx_txn_recon_pending_stk` (partial index for reconciliation scheduler WHERE clause)
  - File: [V4__erp_sync_records_indexes.sql](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/resources/db/migration/V4__erp_sync_records_indexes.sql)

---

## Phase 5 — Testing & Observability ✅

### Checklist

#### Unit Tests
- [x] `StkPushServiceTest.java` — happy path, Daraja error, duplicate idempotency key
- [x] `CallbackServiceTest.java` — STK/B2C/Reversal callbacks; verify RabbitMQ publish
- [x] `AuditServiceTest.java` — hash-chain integrity with 3 sequential entries
- [x] `JpaRegisteredClientRepositoryTest.java` — JWT contains correct `role` claim
- [x] `ERPDispatchServiceTest.java` — adapter routing + retry counter increments
- [x] `ReconciliationSchedulerTest.java` — MATCHED, MISMATCHED, IN_PROGRESS, Daraja error paths

#### Integration Tests (Testcontainers)
- [x] `PaymentFlowIT.java` — full STK Push → callback → DB state → event published
- [x] `RateLimitIT.java` — 110 requests → first 100 OK, next 10 → HTTP 429
- [x] `ERPConnectorIT.java` — publish event → sync record created → DLQ after 5 failures

#### Observability
- [x] Add Prometheus/Actuator config to `application.yml` files (erp, auth modules)
- [x] Add Prometheus + Grafana services to `docker-compose.yml`
- [x] Add custom Micrometer counters/timers:
  - [x] `payment.stk.initiated.count`
  - [x] `payment.callback.processing.time`
  - [x] `erp.sync.success.count` / `erp.sync.failure.count`
  - [x] `erp.dlq.messages.count`
  - [x] `rate.limit.rejected.count`
  - [x] `reconciliation.matched.count` / `reconciliation.mismatched.count`

---

## Phase 6 — API Gateway & Staff Portal 🟨

### Checklist

#### `openfloat-gateway` Module
- [x] **`openfloat-gateway/pom.xml`** — Module POM + added to parent `<modules>`
  - File: [pom.xml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-gateway/pom.xml)
  - Dependencies: Spring Cloud Gateway, Reactive Redis, OAuth2 Resource Server, Actuator, Micrometer Prometheus

- [x] **`application.yml`** — Route definitions, Redis rate limiter, Safaricom IP config
  - File: [application.yml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-gateway/src/main/resources/application.yml)
  - Routes: `/api/v1/payments/**`, `/api/v1/transactions/**`, `/api/v1/mpesa/**` → core; `/oauth2/**`, `/api/v1/users/**` → auth
  - Filters: `TokenRelay` + `RequestRateLimiter` (100 req/s burst 150)

- [x] **`GatewayApplication.java`** — Main class + `userKeyResolver` bean (IP-based rate limit key)
  - File: [GatewayApplication.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-gateway/src/main/java/com/openfloat/mpesa/gateway/GatewayApplication.java)

- [x] **`GatewaySecurityConfig.java`** — Reactive WebFlux security: CSRF disabled, OAuth2 JWT resource server, public actuator/callback paths
  - File: [GatewaySecurityConfig.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-gateway/src/main/java/com/openfloat/mpesa/gateway/config/GatewaySecurityConfig.java)

- [x] **`IpWhitelistFilter.java`** — Block non-Safaricom IPs on callback routes (CIDR subnet matching)
  - File: [IpWhitelistFilter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-gateway/src/main/java/com/openfloat/mpesa/gateway/filter/IpWhitelistFilter.java)

- [x] **`RequestLoggingFilter.java`** — Structured request logging: client_id, method, path, status, duration
  - File: [RequestLoggingFilter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-gateway/src/main/java/com/openfloat/mpesa/gateway/filter/RequestLoggingFilter.java)

#### `openfloat-staff-portal` React SPA
- [ ] Scaffold project with Vite + TypeScript + TanStack Query + Tailwind
- [ ] `LoginPage` — OAuth2 PKCE flow
- [ ] `DashboardPage` — summary cards + charts
- [ ] `PaymentInitiatePage` — STK Push form with live status polling
- [ ] `TransactionsPage` — paginated/filterable table + CSV export
- [ ] `TransactionDetailPage` — full callback + reconciliation + ERP sync view
- [ ] `AuditLogPage` — admin-only searchable audit log
- [ ] `UserManagementPage` — admin-only user CRUD
- [ ] `SettingsPage` — paybill config + API client management

#### Infrastructure
- [ ] Update `docker-compose.yml` — add gateway (8443), portal (3000), prometheus (9090), grafana (3001)
- [ ] Create `k8s/` directory with all manifests (deployments, services, ingress, HPA)

---

## Phase 7 — Production Hardening & Go-Live ⬜

### Checklist
- [ ] HashiCorp Vault integration via `spring-cloud-vault`
- [ ] Daraja credential rotation job (`@Scheduled`)
- [ ] Remove dev credentials from all configs for production profile
- [ ] TLS 1.3 enforcement at gateway ingress (cert-manager + Let's Encrypt)
- [ ] mTLS between internal services (optional — see open question Q5)
- [ ] Logstash pipeline for audit logs + app logs → Elastic/Splunk
- [ ] SIEM alerts: failed logins, DLQ messages, rate limit spikes
- [ ] pgBackRest backup configuration + test restore
- [ ] Redis AOF persistence + replication config
- [ ] K8s resource limits/requests on all pods
- [ ] HPA load test (k6 / Gatling) — min 2, max 10 replicas at 70% CPU
- [ ] Incident runbooks: DLQ spike, token refresh failure, Daraja outage
- [ ] Production callback IP whitelist updated with Safaricom production ranges
- [ ] Default seed admin password rotated

---

## Key Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Idempotency storage | Redis (primary) + PostgreSQL (fallback) | Fast dedup with persistence guarantee |
| Audit chain integrity | SHA-256 hash chain with pessimistic DB lock | Tamper-evident without external dependency |
| Field-level encryption | AES-256-GCM via JPA `AttributeConverter` | Transparent to service layer; key in Vault |
| Rate limiting | Redis sliding window per `client_id` | Stateless services; Redis already in stack |
| Auth mechanism | Spring OAuth2 Authorization Server + JWT | Avoids external auth server dependency in dev |
| ERP retry strategy | AMQP DLX/DLQ + Spring AMQP stateful retry | Native RabbitMQ capability; configurable TTL |
| Async audit writes | Spring `@Async` + `@Lock(PESSIMISTIC_WRITE)` | Non-blocking payment thread; race-safe chain |
| Dynamics auth | OAuth2 CC in-process with token caching | Avoids storing pre-fetched tokens in Redis |
| Reconciliation trigger | Nightly `@Scheduled` cron at 02:00 UTC | Low traffic window; 24h cutoff gives callbacks time to arrive |
| SAP/Oracle auth | HTTP Basic Auth (Base64) | Matches standard SAP BAPI / Oracle REST conventions |
| API Gateway | Spring Cloud Gateway (WebFlux) + TokenRelay | Reactive non-blocking ingress; JWT passthrough to downstream |
| Callback IP security | CIDR subnet whitelist via `WebFilter` | Network-level isolation for Safaricom callback endpoints |
