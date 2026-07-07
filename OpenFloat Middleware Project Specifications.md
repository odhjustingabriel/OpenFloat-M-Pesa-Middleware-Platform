**OpenFloat M-Pesa Middleware Platform**

**Technical Specification**

**Project Overview**

The OpenFloat M-Pesa Middleware Platform is a secure, scalable, enterprise-grade middleware solution that acts as the single entry point for all Safaricom M-Pesa interactions.

The platform abstracts the complexity of the Safaricom Daraja APIs and provides:

- Unified REST APIs for internal applications
- Secure callback processing
- Real-time ERP integrations
- Transaction reconciliation
- Comprehensive audit logging
- Internal staff portal for payment operations

**Technology Stack**

**Backend**

- Java 21
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- Hibernate

**Database**

- PostgreSQL

**Cache**

- Redis

**Messaging**

- RabbitMQ (Primary)
- Kafka (Optional)

**Authentication & Authorization**

- Spring Security OAuth2
- Spring Authorization Server
- JWT
- LDAP/Active Directory Integration

**Documentation**

- OpenAPI 3
- Swagger UI

**Containerization & Deployment**

- Docker
- Kubernetes

**Observability**

- Micrometer
- Prometheus
- Grafana

**Logging**

- ELK Stack
- Splunk Integration

**Build Tool**

- Maven

**System Objectives**

The platform must:

1.  Abstract Safaricom Daraja APIs.
2.  Expose secure REST APIs.
3.  Process callbacks securely.
4.  Integrate with ERP systems using an event-driven architecture.
5.  Support reconciliation and auditing.
6.  Provide a staff-facing web portal.

**System Architecture**

React Staff Portal

|

|

Spring Cloud Gateway

|

Authentication Service

|

M-Pesa Middleware Service

|

\-------------------------------------------------------

| | | |

Daraja API PostgreSQL RabbitMQ/Kafka Redis

|

|

ERP Connector Service

|

\------------------------------------------------

| | |

SAP Oracle Dynamics

**Core Services**

**API Gateway**

**Responsibilities**

- Request routing
- Authentication
- Rate limiting
- API versioning
- Logging

**Technology**

- Spring Cloud Gateway

**Authentication Service**

**Responsibilities**

- OAuth2 Client Credentials Flow
- JWT Generation
- LDAP Authentication
- RBAC

**Roles**

**VIEWER**

Read-only access to transactions and reports.

**OPERATOR**

Can initiate STK Push and B2C transactions.

**FINANCE**

Can access reconciliation tools and export financial data.

**ADMIN**

Can manage:

- Users
- Credentials
- Paybill configurations
- System settings

**M-Pesa Integration Service**

**Responsibilities**

- STK Push
- C2B
- B2C
- Reversals
- Callback processing

MpesaService

├── StkPushService

├── C2BService

├── B2CService

└── ReversalService

**M-Pesa API Support**

**Customer to Business (C2B)**

Purpose:

- Receive customer payments.
- Handle validation callbacks.
- Handle confirmation callbacks.
- Support transaction simulations.

Features:

- Register callback URLs.
- Validate payloads.
- Persist transactions.

**Business to Customer (B2C)**

Purpose:

- Send payments to customers.

Examples:

- Refunds
- Salaries
- Settlements

Features:

- Initiate disbursements.
- Track statuses.
- Process callbacks.

**STK Push (Lipa na M-Pesa Online)**

Purpose:

- Initiate payment prompts on customer devices.

Features:

- Initiate STK Push.
- Handle callback responses.
- Handle timeouts.
- Track payment status.

**Reversal API**

Purpose:

- Reverse erroneous transactions.

Features:

- Submit reversal requests.
- Track reversal status.
- Process reversal callbacks.

**REST API Specification**

Base URL:

/api/v1

**Initiate STK Push**

**Endpoint**

POST /api/v1/payments/stk-push

**Request**

{

"msisdn": "254712345678",

"amount": 1000,

"paybill": "123456",

"accountRef": "INV001",

"description": "Invoice Payment"

}

**Response**

{

"transactionId": "uuid",

"status": "PENDING"

}

**Get Transaction**

**Endpoint**

GET /api/v1/transactions/{id}

Returns:

- Transaction details
- Metadata
- Callback payload
- Reconciliation information

**Search Transactions**

**Endpoint**

GET /api/v1/transactions

**Filters**

- startDate
- endDate
- paybill
- accountReference
- phoneNumber
- status
- reconciliationStatus

Features:

- Pagination
- Sorting

**Internal Callback Endpoint**

**Endpoint**

POST /api/v1/callbacks

Purpose:

- Forward final transaction results to internal applications.

**Callback Endpoints**

POST /api/v1/mpesa/callbacks/stk

POST /api/v1/mpesa/callbacks/c2b

POST /api/v1/mpesa/callbacks/b2c

POST /api/v1/mpesa/callbacks/reversal

Responsibilities:

1.  Validate callback.
2.  Store raw payload.
3.  Deduplicate requests.
4.  Publish events.

**Idempotency**

Prevent duplicate processing using:

- ConversationID
- OriginatorConversationID
- CheckoutRequestID
- MerchantRequestID

Generate:

idempotency_key

Duplicate requests should return:

{

"status": "ALREADY_PROCESSED"

}

**Database Design**

**transactions**

id UUID

transaction_id

conversation_id

originator_conversation_id

checkout_request_id

merchant_request_id

transaction_type

phone_number

amount

paybill

account_reference

status

result_code

result_description

reconciliation_id

created_at

updated_at

**callbacks**

id UUID

transaction_id

raw_payload JSONB

processed_payload JSONB

received_at

**api_clients**

id UUID

client_id

client_secret

status

rate_limit

created_at

**users**

id UUID

username

email

role

status

last_login

**audit_logs**

id UUID

username

action

resource

ip_address

timestamp

hash

**Event-Driven Architecture**

On every final callback:

1.  Persist transaction.
2.  Publish event.

Example:

{

"transactionId": "",

"amount": "",

"phoneNumber": "",

"accountReference": "",

"status": ""

}

Publish to:

exchange.transaction.completed

**ERP Connector Service**

Responsibilities:

- Consume events.
- Transform payloads.
- Send data to ERP systems.
- Retry failures.
- Persist responses.

**Supported ERP Systems**

- SAP
- Oracle
- Microsoft Dynamics
- Custom ERP Platforms

ERPAdapter

├── SAPAdapter

├── OracleAdapter

├── DynamicsAdapter

└── CustomAdapter

**Automated Reconciliation**

Generate:

reconciliation_id

Store:

- Raw callback
- Transformed payload
- ERP response
- Timestamps

**Retry Strategy**

Retry 1 → 1 minute

Retry 2 → 5 minutes

Retry 3 → 15 minutes

Retry 4 → 30 minutes

Retry 5 → Dead Letter Queue

**Staff Portal**

**Technology**

- React SPA

**Features**

**Initiate Payments**

Fields:

- Phone Number
- Amount
- Account Reference
- Paybill

**Real-Time Status**

Display:

- Pending
- Successful
- Failed
- Error codes
- Timestamps

**Transaction Logs**

Search by:

- Date
- Phone Number
- Reference

**Authentication & Security**

**Authentication**

- OAuth2 Client Credentials
- JWT
- LDAP/Active Directory
- Optional Mutual TLS (mTLS)

**Transport Security**

- TLS 1.3

**Data Encryption**

Encrypt sensitive fields:

- Phone Number
- Account Reference

Algorithm:

AES-256

Implement using JPA AttributeConverter.

**Rate Limiting**

Per-client request throttling.

Example:

100 requests/minute/client

Return:

429 Too Many Requests

**Secrets Management**

Never store:

- Consumer Keys
- Secrets
- Certificates

Use:

- Kubernetes Secrets
- HashiCorp Vault
- AWS Secrets Manager

Support automatic secret rotation.

**Audit Logging**

Log:

- Login attempts
- Logout events
- API calls
- Configuration changes
- Payment initiation
- Failed authentication

Forward logs to:

- ELK Stack
- Splunk

Logs should support tamper detection through hash chaining.

**Package Structure**

com.openfloat.mpesa

├── config

├── security

├── controller

├── service

├── repository

├── entity

├── dto

├── mapper

├── exception

├── event

├── listener

├── integration

│ ├── mpesa

│ └── erp

├── audit

├── reconciliation

├── scheduler

└── util

**API Documentation**

Expose:

/swagger

/openapi.json

**Non-Functional Requirements**

- 99.9% uptime target
- Horizontal scalability
- High availability
- Full auditability
- Secure by default
- Response time under 500ms for non-M-Pesa operations
- Comprehensive monitoring and observability
- Dockerized deployment
- Kubernetes-ready infrastructure
- Unit testing with JUnit 5
- Integration testing with Testcontainers
- Minimum 80% code coverage

**Future Enhancements**

- Multi-tenant support
- Multi-country mobile money integrations
- Payment analytics dashboard
- Fraud detection engine
- Real-time anomaly detection
- Event sourcing
- Distributed tracing with OpenTelemetry
- Multi-region disaster recovery support