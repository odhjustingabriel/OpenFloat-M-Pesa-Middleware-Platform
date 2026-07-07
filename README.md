# OpenFloat M-Pesa Middleware Platform

Enterprise-grade middleware for Safaricom M-Pesa Daraja API integration.

## Architecture

```
React Staff Portal
        │
  Spring Cloud Gateway
        │
  Authentication Service
        │
  M-Pesa Middleware Service (openfloat-core)
        │
  ┌─────┼──────────┬──────────────┐
  │     │          │              │
Daraja PostgreSQL RabbitMQ/Kafka Redis
  API                    │
                         │
               ERP Connector Service
                         │
              ┌──────────┼──────────┐
              │          │          │
             SAP      Oracle   Dynamics
```

## Tech Stack

| Layer           | Technology                          |
|-----------------|-------------------------------------|
| Language        | Java 21                             |
| Framework       | Spring Boot 3.3.x                   |
| Database        | PostgreSQL 16                       |
| Cache           | Redis 7                             |
| Messaging       | RabbitMQ 3                          |
| Auth            | Spring Security OAuth2 + JWT        |
| API Docs        | OpenAPI 3 + Swagger UI              |
| Build           | Maven (multi-module)                |
| Containerization| Docker + Docker Compose             |
| Observability   | Micrometer + Prometheus + Grafana   |

## Project Structure

```
openfloat-parent/
├── openfloat-common/       # Shared DTOs, exceptions, utilities, events
├── openfloat-core/         # M-Pesa middleware core service
├── openfloat-auth/         # Authentication & authorization service (Phase 3)
├── openfloat-gateway/      # Spring Cloud Gateway (Phase 6)
├── openfloat-erp-connector/# ERP integration service (Phase 4)
├── openfloat-staff-portal/ # React SPA (Phase 6)
├── docker-compose.yml      # Development infrastructure
└── pom.xml                 # Parent POM
```

## Quick Start

### Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** on port `5432` (db: `openfloat_mpesa`)
- **Redis** on port `6379`
- **RabbitMQ** on port `5672` (management UI: `http://localhost:15672`)

### 2. Build & Run

```bash
# Build all modules
mvn clean package -DskipTests

# Run the core service
cd openfloat-core
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Access

| Service              | URL                                    |
|----------------------|----------------------------------------|
| Core API             | `http://localhost:8080`                |
| Swagger UI           | `http://localhost:8080/swagger`        |
| OpenAPI JSON         | `http://localhost:8080/openapi.json`   |
| RabbitMQ Management  | `http://localhost:15672`               |
| Prometheus Metrics   | `http://localhost:8080/actuator/prometheus` |

### Default Admin Credentials

- **Username:** `admin`
- **Password:** `admin123` *(change in production!)*

## API Endpoints

| Method | Endpoint                            | Description              |
|--------|-------------------------------------|--------------------------|
| POST   | `/api/v1/payments/stk-push`        | Initiate STK Push        |
| GET    | `/api/v1/transactions/{id}`        | Get transaction details  |
| GET    | `/api/v1/transactions`             | Search transactions      |
| POST   | `/api/v1/callbacks`                | Internal callback forward|
| POST   | `/api/v1/mpesa/callbacks/stk`      | STK Push callback        |
| POST   | `/api/v1/mpesa/callbacks/c2b`      | C2B callback             |
| POST   | `/api/v1/mpesa/callbacks/b2c`      | B2C callback             |
| POST   | `/api/v1/mpesa/callbacks/reversal` | Reversal callback        |

## Security

- **Encryption at rest:** AES-256-GCM for phone numbers and account references
- **Transport:** TLS 1.3
- **Auth:** OAuth2 + JWT
- **Rate limiting:** Per-client, configurable (default: 100 req/min)
- **Audit logging:** Hash-chained tamper-evident logs

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify -Pintegration-test

# Coverage report
mvn jacoco:report
```

## License

MIT License — see [LICENSE](LICENSE) for details.
