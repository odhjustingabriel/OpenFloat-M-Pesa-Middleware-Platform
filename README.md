# OpenFloat M-Pesa Middleware Platform

[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/downloads/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-orange.svg)](https://www.rabbitmq.com/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

An enterprise-grade, high-performance, and secure middleware platform that abstracts Safaricom's M-Pesa Daraja APIs. It provides unified REST APIs, robust callback ingestion, real-time event-driven ERP synchronization, comprehensive security, and transaction reconciliation.

---

## System Architecture

```
                       ┌──────────────────────────────┐
                       │      React Staff Portal      │
                       └──────────────┬───────────────┘
                                      │ (OAuth2 / JWT)
                                      ▼
                       ┌──────────────────────────────┐
                       │     Spring Cloud Gateway     │
                       └──────────────┬───────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼ (JWT)                                         ▼ (JWT / OAuth2 Token)
┌───────────────────────────┐                   ┌───────────────────────────┐
│     Authentication        │                   │    M-Pesa Core Service    │
│  Service (openfloat-auth) │                   │     (openfloat-core)      │
└─────────────┬─────────────┘                   └─────────────┬─────────────┘
              │ (JPA)                                         │
              │                                               ├─► Daraja APIs (STK, B2C, Reversal)
              │                                               ├─► Redis 7 (Tokens, Rate Limits)
              │                                               ├─► PostgreSQL (Transactions, Audits)
              │                                               │
              │                                               ▼ (AMQP: transaction.completed)
              │                                 ┌───────────────────────────┐
              │                                 │   exchange.transaction    │
              │                                 └─────────────┬─────────────┘
              │                                               │
              │                                               ▼ (queue.erp.sync)
              │                                 ┌───────────────────────────┐
              │                                 │       ERP Connector       │
              │                                 │ (openfloat-erp-connector) │
              │                                 └─────────────┬─────────────┘
              │                                               │
              └───────────────────────┬───────────────────────┘
                                      ▼ (Shared PostgreSQL Database)
                              ┌───────────────┐
                              │  PostgreSQL   │
                              └───────────────┘
                                      │
                                      ▼ (Pluggable Dispatch)
                           ┌──────────┼──────────┐
                           │          │          │
                         SAP ERP   Oracle D365  Custom REST
```

---

## Platform Core Features

### 🔒 Enterprise-Grade Security
* **Transparent Data Encryption:** Sensitive database columns (phone numbers, account references) are automatically encrypted at the JPA layer using **AES-256-GCM** via custom attribute converters.
* **OAuth2 Authentication & authorization:** Centralized token issuance based on Spring Security Authorization Server supporting client-credentials flow, custom roles integration, and database-backed users.
* **LDAP/Active Directory Integration:** Support for enterprise LDAP environments to manage internal payment operations roles.
* **API Throttling:** Multi-stage Redis-based token rate-limiting (`RateLimitFilter`) protecting payment and query resources (default: 100 requests/minute/client).

### 🛡️ Tamper-Evident Auditing
* **Cryptographic Hash-Chaining:** Each audit log entry contains a SHA-256 hash computed recursively as `hash = SHA-256(previous_hash | current_record_data)`.
* **Automated Auditing Aspect:** Simple method intercept via `@Audit(action = PAYMENT_INITIATED)` annotation.
* **Integrity Validation:** Direct check endpoints verify chain continuity to detect internal or database-level record tempering.

### ⚡ Event-Driven ERP Synchronization
* **Reliable Messaging:** Transaction outcomes (Success/Failures) publish to a RabbitMQ Topic Exchange (`exchange.transaction.completed`).
* **Resilient Retry & DLQ Policies:** Automatic exponential backoff retries (1m, 5m, 25m capped at 30m) with Dead Letter Queue fallback routing after 5 failed attempts.
* **Pluggable ERP Adapters:** Modular design supporting SAP ERP, Oracle Financials, Microsoft Dynamics 365, and Generic REST targets out of the box.

---

## Project Structure

```
openfloat-parent/
├── openfloat-common/         # Shared schemas, DTOs, custom exceptions, crypto/phone utilities, and Vault provider
├── openfloat-core/           # Core M-Pesa middleware (Daraja clients, callback endpoints, and token manager)
├── openfloat-auth/           # OAuth2 Spring Authorization Server & LDAP authentication module
├── openfloat-erp-connector/  # ERP event listener, retry coordinators, and integration adapters
├── openfloat-gateway/        # Spring Cloud API Gateway (Routing, rate-limiting, SSL, Safaricom IP whitelist)
├── openfloat-staff-portal/   # React Single Page Application (Vite, TS, TanStack Query, Recharts, Tailwind)
├── k8s/                      # Production K8s manifests, logstash pipeline configs, and Prometheus alert rules
├── scripts/                  # Vault seeding, credential/password rotation, and pgBackRest backup scripts
├── docker-compose.yml        # Development database, brokers, Prometheus/Grafana stack
└── pom.xml                   # Master Maven parent POM
```

---

## Tech Stack

| Layer | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Java (JDK) | 21 |
| **Backend Framework** | Spring Boot | 3.3.x |
| **Security** | Spring Security, Spring Authorization Server | 6.3.x |
| **Database** | PostgreSQL | 16 |
| **Cache & Throttling** | Redis | 7 |
| **Message Broker** | RabbitMQ | 3.12+ |
| **Flyway Migrations** | Flyway | 10.x |
| **Portal Frontend** | React, TypeScript, TanStack Query, Vite, Recharts, Tailwind CSS | Latest |
| **Secret Management** | HashiCorp Vault, Spring Cloud Vault | Latest |
| **Observability** | Prometheus, Grafana, Logstash, ElasticSearch | Latest |

---

## Quick Start

### Prerequisites
* **Java 21** or higher
* **Maven 3.9+**
* **Node.js 18+** & **npm 9+**
* **Docker & Docker Compose**

### 1. Launch Infrastructure Components
Start the PostgreSQL, Redis, RabbitMQ, and monitoring containers locally:
```bash
docker-compose up -d
```
Verify containers are running:
* **PostgreSQL:** Port `5432` (Database: `openfloat_mpesa`, User/Pass: `openfloat` / `openfloat_dev_2024`)
* **Redis:** Port `6379`
* **RabbitMQ:** Port `5672` (Management Dashboard: `http://localhost:15672` with `guest`/`guest`)
* **Prometheus:** Port `9090`
* **Grafana:** Port `3001`

### 2. Build the Workspace
Compile and bundle all backend services from the parent directory:
```bash
mvn clean package -DskipTests
```

### 3. Run the Backend Services
Launch the complete middleware platform locally (open separate terminals):

#### Start the Authorization Server (Port 8081)
```bash
cd openfloat-auth
mvn spring-boot:run
```

#### Start the Core Middleware Service (Port 8080)
```bash
cd openfloat-core
mvn spring-boot:run
```

#### Start the ERP Connector Service (Port 8082)
```bash
cd openfloat-erp-connector
mvn spring-boot:run
```

#### Start the API Gateway (Port 8443)
```bash
cd openfloat-gateway
mvn spring-boot:run
```

### 4. Run the Staff Portal Frontend
Install dependencies and run the Vite development server (Port 3000):
```bash
cd openfloat-staff-portal
npm install
npm run dev
```

---

## Production Hardening & Deployment

For production deployments, the following utilities are provided:
* **Secrets Seeding:** Run `./scripts/vault-seed-secrets.sh` to initialize HashiCorp Vault KV v2 secret engine paths.
* **Credential Rotation:** Run `./scripts/rotate-seed-passwords.sh` to rotate default database passwords and generate `.env.production`.
* **Automated Backups:** Nightly database backup CronJob configuration is located in `k8s/postgres-backup-cronjob.yaml`.
* **K8s Deployments:** Production configurations for services, routing, and HPA autoscaling are located in the `k8s/` directory.

---

## API Endpoints Reference

### 🔐 Authentication & User Administration (`openfloat-auth`)

| Method | Endpoint | Access Role | Description |
| :--- | :--- | :--- | :--- |
| POST | `/oauth2/token` | Public (Client Auth) | Requests an access token via client-credentials flow |
| POST | `/api/v1/users` | `ADMIN` | onboard new staff/operators with explicit roles |
| GET | `/api/v1/users` | `ADMIN` | List all system users |
| PUT | `/api/v1/users/{id}/status` | `ADMIN` | Enable, block or suspend user accounts |

### 💳 Payment Operations (`openfloat-core`)

| Method | Endpoint | Access Role | Description |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/payments/stk-push` | `OPERATOR`, `ADMIN` | Triggers Lipa na M-Pesa Online STK Push prompt |
| POST | `/api/v1/payments/b2c` | `OPERATOR`, `ADMIN` | Disburses payouts to mobile wallets |
| POST | `/api/v1/payments/reversal` | `FINANCE`, `ADMIN` | Requests reversal of an erroneous transaction |
| POST | `/api/v1/payments/c2b/register-urls`| `ADMIN` | Configures Confirmation & Validation endpoints with Safaricom |
| POST | `/api/v1/payments/c2b/simulate` | `ADMIN` | Simulates a customer payment (Sandbox only) |

### 🔍 Query & Ingest (`openfloat-core`)

| Method | Endpoint | Access Role | Description |
| :--- | :--- | :--- | :--- |
| GET | `/api/v1/transactions` | `VIEWER`, `FINANCE`, `ADMIN` | Paginated search with complex metadata filters |
| GET | `/api/v1/transactions/{id}` | `VIEWER`, `FINANCE`, `ADMIN` | Fetch details including callback payload |
| POST | `/api/v1/mpesa/callbacks/stk` | Public | Webhook callback from Safaricom for STK pushes |
| POST | `/api/v1/mpesa/callbacks/c2b` | Public | Webhook callback from Safaricom for C2B payments |
| POST | `/api/v1/mpesa/callbacks/b2c` | Public | Webhook callback from Safaricom for B2C transfers |
| POST | `/api/v1/mpesa/callbacks/reversal` | Public | Webhook callback from Safaricom for reversals |

---

## Access & Diagnostics

* **Swagger UI Documentation:** `http://localhost:8080/swagger`
* **Core OpenAPI JSON Descriptor:** `http://localhost:8080/openapi.json`
* **Prometheus Observability Metrics:** `http://localhost:8080/actuator/prometheus`
* **Health and Readiness Probes:** `http://localhost:8080/actuator/health`

---

## Verification & Test Suites

Verify code coverage and component interactions:
```bash
# Execute unit tests
mvn test

# Run integration tests using Testcontainers (requires Docker)
mvn verify -Pintegration-test

# Generate test coverage reports
mvn jacoco:report
```

## License
Copyright (c) 2026 OpenFloat M-Pesa Middleware Platform. All rights reserved. 

This software and its documentation are confidential and proprietary. See [LICENSE](LICENSE) for full licensing terms and restrictions.
