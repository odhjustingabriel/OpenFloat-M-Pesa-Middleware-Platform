# OpenFloat M-Pesa Middleware Platform — Comprehensive Security Architecture & Applied Hardening

> **Security Architecture Manual** | **Standard:** Enterprise Financial Infrastructure  
> **Compliance Alignment:** PCI-DSS Baseline, ISO/IEC 27001, Safaricom Daraja Security Guidelines

---

## 1. Overview & Defense-in-Depth Model

The **OpenFloat M-Pesa Middleware Platform** employs a multi-layered **Defense-in-Depth** security model designed to safeguard sensitive financial transactions, customer PII (Personally Identifiable Information), and enterprise authentication credentials.

```
┌────────────────────────────────────────────────────────────────────────┐
│ 🌐 EDGE LAYER: TLS 1.3 Cert-Manager Ingress + Safaricom IP Whitelist   │
├────────────────────────────────────────────────────────────────────────┤
│ 🛡️ GATEWAY LAYER: Spring Cloud Gateway + Redis Sliding Window Rate Limit│
├────────────────────────────────────────────────────────────────────────┤
│ 🔐 AUTH LAYER: OAuth2 PKCE + Spring Authorization Server + LDAP         │
├────────────────────────────────────────────────────────────────────────┤
│ 🔗 MESH LAYER: Istio Strict Internal mTLS Pod-to-Pod Mutual TLS         │
├────────────────────────────────────────────────────────────────────────┤
│ 🔒 DATA LAYER: JPA AES-256-GCM Field Encryption + SHA-256 Chain Audit  │
├────────────────────────────────────────────────────────────────────────┤
│ 🔑 SECRET LAYER: HashiCorp Vault KV-v2 + Automated Token Rotation Job   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Field-Level Data Encryption at Rest

### AES-256-GCM JPA Attribute Converter
To protect sensitive PII stored in PostgreSQL (such as phone numbers `msisdn` and customer account references `accountReference`), field-level encryption is enforced at the JPA ORM layer using `EncryptedStringConverter.java`.

* **Algorithm:** `AES/GCM/NoPadding` (256-bit key).
* **Galois/Counter Mode (GCM):** Provides both confidentiality and authenticated data integrity, preventing ciphertext tampering attacks.
* **Initialization Vector (IV):** A 12-byte cryptographically random IV generated via `SecureRandom` for every single encryption operation. The IV is prepended to the ciphertext before Base64 encoding.

```
Ciphertext Layout: Base64( [ 12-Byte Random IV ] + [ Encrypted Payload ] + [ 128-Bit GCM Authentication Tag ] )
```

* **Implementation File:** [EncryptedStringConverter.java](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/openfloat-core/src/main/java/com/openfloat/mpesa/security/EncryptedStringConverter.java)
* **Master Key Storage:** Injected via environment variable `OPENFLOAT_ENCRYPTION_KEY` or fetched dynamically from HashiCorp Vault at path `secret/data/openfloat/encryption`.

---

## 3. Data-in-Transit & Network Security

### TLS 1.3 Transport Security
* **Ingress Termination:** `k8s/gateway-ingress-tls.yaml` configures NGINX Ingress & `cert-manager` to enforce **TLS 1.3** and modern cipher suites (`ECDHE-ECDSA-AES128-GCM-SHA256`, `ECDHE-RSA-AES256-GCM-SHA384`).
* **HTTP to HTTPS Redirection:** Automatic `301 Moved Permanently` redirect enforced on all unencrypted port 80 traffic.

### Strict Internal Mutual TLS (mTLS)
* **Istio Service Mesh:** Internal communication between `openfloat-gateway`, `openfloat-core`, `openfloat-auth`, and `openfloat-erp-connector` is governed by Istio `PeerAuthentication` in `STRICT` mode ([internal-mtls-policy.yaml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/k8s/internal-mtls-policy.yaml)).
* **Impact:** Prevents packet sniffing, lateral movement, or man-in-the-middle (MITM) attacks inside the Kubernetes cluster.

### Safaricom Webhook IP CIDR Whitelisting
To prevent spoofed webhook requests on public M-Pesa callback endpoints (`/api/v1/mpesa/callbacks/**`), the platform enforces network-level IP filtering using `IpWhitelistFilter.java` at the Gateway and Core layers:

* **Safaricom Production Ranges:** `196.201.214.0/24`, `196.201.213.0/24`
* **Safaricom Sandbox Range:** `196.201.212.0/24`
* **Matching Logic:** Implements fast bitwise CIDR mask evaluation using `IpAddressMatcher`.

---

## 4. Authentication, Authorization & OAuth2 Security

### OAuth2 Authorization Server & Role Claims
* **Module:** `openfloat-auth`
* **Flows Supported:**
  * **Client Credentials Grant:** For machine-to-machine (M2M) backend integration.
  * **OAuth2 PKCE (Proof Key for Code Exchange):** For the React Staff Portal SPA, preventing authorization code interception attacks without requiring client secret storage on the browser.
* **JWT Token Hardening:**
  * Access tokens signed via 2048-bit RSA keypair.
  * Embedded custom claims: `role` (`ADMIN`, `STAFF`, `FINANCE`, `OPERATOR`), `client_id`, and `issuer`.
  * Short-lived access token validity (15 minutes in production).

### Enterprise LDAP / Active Directory Integration
* **`LdapConfig.java`**: Provides conditional LDAP authentication for enterprise staff deployment. Supports Spring Security `LdapAuthenticationProvider` binding to Active Directory domain controllers.

### Fine-Grained Role-Based Access Control (RBAC)
* All core API endpoints enforce explicit Spring Security annotations:
  ```java
  @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
  @PostMapping("/stk-push")
  public ResponseEntity<ApiResponse<StkPushResult>> initiateStkPush(...)
  ```

---

## 5. API Rate Limiting & Throttling

To prevent Denial of Service (DoS) attacks and brute-force token enumeration, a dual-layer Redis sliding window rate-limiter is implemented:

1. **API Gateway Layer (`RequestRateLimiter`):**
   * Configured in `openfloat-gateway/src/main/resources/application.yml`.
   * Replenish rate: 100 req/s, Burst capacity: 150 req/s per client IP / `client_id`.
2. **Core Application Layer (`RateLimitFilter.java`):**
   * Per-client Redis sliding window algorithm.
   * Exceeding threshold returns `HTTP 429 Too Many Requests` along with a `Retry-After: 60` HTTP header.

---

## 6. Cryptographic Tamper-Evident Audit Logging

### SHA-256 Hash Chaining Algorithm
Every sensitive action (payment initiation, callback execution, user status modification, credential change) triggers an audit event captured by `AuditAspect.java`.

* **Recursive Chain Equation:**
  $$\text{ChainHash}_n = \text{SHA-256}\Big(\text{ChainHash}_{n-1} \,||\, \text{Timestamp} \,||\, \text{UserId} \,||\, \text{Action} \,||\, \text{EntityId}\Big)$$
* **Genesis Seed:** The initial audit record $n=0$ uses `V3__audit_log_chain_seed.sql` with a known 64-zero genesis hash.
* **Concurrency Control:** `AuditService.java` executes `SELECT FOR UPDATE` pessimistic locks (`@Lock(PESSIMISTIC_WRITE)`) on the audit log repository to guarantee strict, sequential, race-condition-free chain links under high concurrency.
* **Tamper Verification Endpoint:** `AuditIntegrityController.java` (`GET /api/v1/audit/verify`) re-computes every hash link from genesis to current. Any direct SQL database tampering breaks the hash chain and is immediately flagged.

---

## 7. Secrets Management & Operational Hardening

### HashiCorp Vault KV-v2 Engine
* Development/production secrets are stored in HashiCorp Vault KV-v2 (`secret/data/openfloat/`).
* **Vault Agent Sidecar ([vault-agent-config.yaml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/k8s/vault-agent-config.yaml)):** Automatically authenticates via Kubernetes ServiceAccount tokens and injects dynamic credentials into `/vault/secrets/` in-memory tmpfs mounts.

### Automated Credential Rotation
* **`DarajaCredentialRotationJob.java`**: Scheduled job running every 6 hours to proactively evict and refresh Daraja OAuth tokens, check API credential validity, and export rotation metrics to Prometheus.
* **`rotate-seed-passwords.sh`**: Automated utility script to generate cryptographically secure 32-character base64 database passwords, Redis keys, and encryption master keys before production deployment.

### Log Redaction & SIEM Hygiene
* **`Logstash Pipeline` ([logstash-config.yaml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/k8s/logstash-config.yaml)):** Auto-detects and redacts sensitive M-Pesa payload fields (`InitiatorPassword`, `SecurityCredential`, `passkey`) before forwarding logs to Elasticsearch/Splunk.
* **Pod Security Context ([pod-resource-hardening.yaml](file:///d:/HOC/OpenFloat-M-Pesa-Middleware-Platform/k8s/pod-resource-hardening.yaml)):** Containers run as non-root (`runAsNonRoot: true`, `runAsUser: 10001`), with read-only root filesystems (`readOnlyRootFilesystem: true`) and privilege escalation disabled (`allowPrivilegeEscalation: false`).
