---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
# External Integrations

**Analysis Date:** 2026-11-06

## APIs & External Services

**OpenAPI / API Catalog Ingestion:**
- OpenAPI 3.x / Swagger Specification Ingestion - Imports service endpoints and auto-generates sample payloads (`backend/src/main/java/com/orchestrator/platform/service/OpenApiCatalogService.java`)
  - SDK/Client: `io.swagger.parser.v3:swagger-parser`
  - Auth: Optional HTTP Basic / Bearer token via ingestion request headers or URL query

**Target HTTP Endpoints (Under Test):**
- Arbitrary External REST / HTTP APIs - Executed dynamically as workflow test steps (`backend/src/main/resources/karate/execute-http.feature`)
  - SDK/Client: Embedded Karate HTTP Client (Apache HttpClient / Karate core)
  - Auth: Configurable per-step or flow-level headers (e.g., `Authorization: Bearer ${authToken}`), environment secrets (`${$env:SECRET_KEY}`)

**Built-in Mock Target Services:**
- Mock Microservice Suite - Self-contained endpoints for auth, orders, payments, and inventory (`backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java`)
  - Endpoints: `POST /api/v1/mock/auth/login`, `POST /api/v1/mock/orders`, `GET /api/v1/mock/orders/{orderId}`, `POST /api/v1/mock/payments/charge`, `GET /api/v1/mock/inventory/{sku}`
  - Auth: Custom in-memory mock JWT validation (`MockJwtService` / in-memory tokens)

**Kafka Message Brokers:**
- Apache Kafka - Consumes messages from topics and validates JSON payloads against assertions (`backend/src/main/java/com/orchestrator/platform/service/execution/KafkaAssertExecutor.java`)
  - SDK/Client: `org.apache.kafka:kafka-clients`
  - Auth: Standard Kafka SASL/SSL or PLAINTEXT via step bootstrap configuration

## Data Storage

**Databases:**
- PostgreSQL 16 (Primary Application Storage):
  - Connection: `SPRING_DATASOURCE_URL` (default: `jdbc:postgresql://localhost:5432/e2e_orchestrator`)
  - Client: Spring Data JPA / Hibernate 6 with Flyway migrations (`backend/src/main/resources/db/migration/V1__init_orchestrator_schema.sql`)
  - Tables: `api_endpoint_catalog`, `test_workflows`, `workflow_executions`
- External Target Databases (Under Test):
  - Supported: PostgreSQL, Oracle Database (`ojdbc17`), H2 (`com.h2database:h2`)
  - Connection: Dynamic JDBC connection strings provided per-step (`DbPollStepConfig.jdbcUrl`)
  - Client: `HikariCP` connection pool manager (`backend/src/main/java/com/orchestrator/platform/config/HikariConnectionPoolManager.java`)

**File Storage:**
- Local Filesystem / Mounted Volume:
  - Base Path: `ORCHESTRATOR_REPORTS_PATH` (default: `./target/karate-reports`, in Docker: `/app/reports`)
  - Purpose: Isolated Karate HTML test reports (`karate-summary.html`, `workflow-report.html`), execution logs, and captured request/response bodies

**Caching:**
- In-Memory Concurrency Caching:
  - Client: Java `ConcurrentHashMap` in `DynamicKarateBridge.java` for active execution contexts, payloads, and step results
  - Client: Cache in `HikariConnectionPoolManager.java` for JDBC connection pools indexed by sanitized URL and credentials

## Authentication & Identity

**Auth Provider:**
- Custom JWT / Bearer Token & Environment-Scoped Secrets:
  - Implementation: Built-in mock auth generator in `MockTargetApiController.java`; workflows chain tokens between steps using variable extraction (`source: RESPONSE_BODY`, `jsonPath: $.token`)
  - Dynamic Secrets: Scoped per environment (`default`, `staging`, `production`) in `WorkflowAst` and resolved via `WorkflowContextEngine.java`
  - Secret Masking: `ExecutionLogBroadcaster.java` automatically redacts registered secret values with `[REDACTED]` prior to streaming over SSE and WebSockets

## Monitoring & Observability

**Error Tracking:**
- Application Error Tracking: Spring Boot standard exception handlers and SLF4J logger (`backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java`)
- Test Execution Failures: Captured per-step in `ExecutionStepResult` entity with exact status code, response body, latency, and assertion failure details

**Logs:**
- Server-Sent Events (SSE): `/api/v1/executions/{id}/stream` streams real-time step start, logs, and completion events (`backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionLogBroadcaster.java`)
- WebSocket STOMP: `/ws` broker endpoint streaming execution progress to `LiveExecutionConsole.tsx`
- Container Logs: Structured standard output with SLF4J / Logback

## CI/CD & Deployment

**Hosting:**
- Self-Hosted Docker Compose or Kubernetes:
  - Base image: `alpine:3.21` with multi-stage build and `jlink` custom JRE
  - Compose configuration: `docker-compose.yml` (local build) and `docker-compose.hub.yml` (Docker Hub prebuilt)

**CI Pipeline:**
- GitHub Actions: `.github/workflows/docker-publish.yml`
  - Triggers on push to `main` branch, SemVer git tags (`v*.*.*`), or manual workflow dispatch
  - Builds multi-platform container images (`linux/amd64`, `linux/arm64`) using Docker Buildx and pushes to Docker Hub

## Environment Configuration

**Required env vars:**
- `SPRING_DATASOURCE_URL`: PostgreSQL JDBC connection URL
- `SPRING_DATASOURCE_USERNAME`: PostgreSQL database username
- `SPRING_DATASOURCE_PASSWORD`: PostgreSQL database password
- `ORCHESTRATOR_REPORTS_PATH`: Filesystem directory for HTML test reports (default: `/app/reports`)
- `PORT`: HTTP server port (default: `8080`)

**Secrets location:**
- Container / Host Environment: Injected into workflow execution contexts via `${$env:VAR_NAME}` syntax
- Database JSONB: Stored in `test_workflows.workflow_ast` under `envSecrets` and `secrets`

## Webhooks & Callbacks

**Incoming:**
- `POST /api/v1/workflows/{id}/execute` - Triggers asynchronous workflow execution; returns HTTP 202 Accepted with execution ID and streams progress
- Copy-pasteable cURL commands generated via `frontend/src/components/navbar/CiCdWebhookModal.tsx` for GitHub Actions, GitLab CI, and Jenkins integration

**Outgoing:**
- None configured (execution notifications are delivered over SSE and WebSockets)

---

*Integration audit: 2026-11-06*
