# MeterFlow Standalone: Master AI Implementation Prompt & Blueprint

> **Use this prompt to scaffold or generate a completely separate, standalone, zero-Docker, zero-PostgreSQL E2E test workflow orchestrator with bundled React Web UI, file-based H2 persistence, and Karate engine.**

---

```markdown
# MISSION: Scaffold "MeterFlow Standalone" - Zero-Docker E2E Test Platform

You are an expert full-stack distributed systems engineer and Java/React architect.
Your mission is to generate a complete, production-ready, self-contained project called **MeterFlow Standalone**.

## Core Value & Philosophy
- **Zero Docker, Zero External PostgreSQL**: Runs natively on any low-resource environment (512MB–2GB RAM) requiring only a JRE (Java 17/21/25).
- **Single All-in-One Binary (`meterflow-standalone.jar`)**: Bundles the compiled React 18 visual canvas UI directly into Spring Boot (`/static`), serving the frontend, REST APIs (`/api/v1`), WebSocket STOMP broker (`/ws`), and Karate test execution engine on a single port (`http://localhost:8080`).
- **File-Based H2 Persistence (`./data/meterflow.mv.db`)**: Workflows, mock endpoints, and execution histories persist across restarts in a local `./data/` directory with zero database server setup.
- **Embedded Custom Java Tools for Karate**:
  - `DbHelper`: Universal multi-database SQL query, polling with retry, and DDL/DML update utility (with bundled Postgres, MySQL, Oracle, MariaDB, SQL Server, SQLite, and H2 drivers).
  - `KafkaHelper`: Standalone Kafka message producer (`KafkaHelper.send(...)`) and consumer/poller (`KafkaHelper.poll(...)` with JSONPath assertions, key matching, and fallback mock mode).
  - Dynamic token resolution: Interpolates `${$env:VAR}`, `${$timestamp}`, and `${$randomUuid}` macros in Karate scenarios.

---

## 1. Project Directory Structure

```text
meterflow-standalone/
├── pom.xml                                   # Standalone Maven POM with H2, Karate, Kafka, and static resource bundling
├── build.sh                                  # 1-step build script (compiles React UI + packages fat-JAR)
├── build.bat                                 # Windows build script
├── run.sh                                    # 1-step launch script (java -Xmx256m -jar target/meterflow-standalone.jar)
├── run.bat                                   # Windows launch script
├── frontend/                                 # Visual Workflow Canvas React SPA
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx                           # Visual canvas, drawer, console, report modal
│   │   ├── components/
│   │   └── api/client.ts                     # Relative baseURL: '/api/v1'
├── backend/                                  # (Or unified src/)
│   └── src/
│       ├── main/
│       │   ├── java/com/orchestrator/platform/
│       │   │   ├── domain/                   # Entities, AST models, Canvas models, DTOs
│       │   │   ├── repository/               # Spring Data JPA repositories
│       │   │   ├── service/                  # Context engine, runner, catalog service
│       │   │   ├── web/                      # REST controllers, Mock API controller, WebSocket
│       │   │   ├── karate/
│       │   │   │   ├── DbHelper.java         # Universal JDBC query & poll helper
│       │   │   │   └── KafkaHelper.java      # Kafka message producer & consumer
│       │   │   └── standalone/
│       │   │       ├── StandaloneApplication.java  # Main entry point & banner
│       │   │       ├── StandaloneDataSeeder.java   # Auto-seeds reference workflows on fresh boot
│       │   │       └── WebSpaController.java       # SPA route forwarder
│       │   └── resources/
│       │       ├── application.yml           # H2 file DB configuration
│       │       ├── karate-config.js          # Pre-binds Db and Kafka helpers globally
│       │       ├── karate/                   # Reusable feature templates (http, db, kafka, delay)
│       │       └── static/                   # Bundled pre-compiled React build (index.html, assets/)
```

---

## 2. Maven Configuration (`pom.xml`)

Configure Maven to:
1. Target Java 25 (or 21 LTS) with `spring-boot-starter-parent:4.1.2` (or 3.4+).
2. Include Spring Boot Web, Data JPA, WebSocket, Validation, JSON, and Actuator.
3. Include `com.h2database:h2` in compile scope.
4. Include runtime JDBC drivers: `org.postgresql:postgresql`, `com.mysql:mysql-connector-j`, `org.mariadb.jdbc:mariadb-java-client`, `com.oracle.database.jdbc:ojdbc17`, `com.microsoft.sqlserver:mssql-jdbc`, `org.xerial:sqlite-jdbc`.
5. Include `io.karatelabs:karate-core:2.1.4` and `org.apache.kafka:kafka-clients:3.8.0`.
6. Use `maven-resources-plugin` to copy `frontend/dist` into `target/classes/static`.
7. Use `spring-boot-maven-plugin` to repackage as executable `meterflow-standalone.jar`.

---

## 3. Application Configuration (`application.yml`)

```yaml
server:
  port: ${PORT:8080}
  compression:
    enabled: true
    mime-types: text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json

spring:
  application:
    name: meterflow-standalone

  datasource:
    url: jdbc:h2:file:./data/meterflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
    open-in-view: false

  flyway:
    enabled: false

  threads:
    virtual:
      enabled: true

  web:
    resources:
      static-locations: classpath:/static/

orchestrator:
  reports:
    base-path: ${ORCHESTRATOR_REPORTS_PATH:./data/reports}
  cleanup:
    max-completed-executions: 100
    retention-days: 14
```

---

## 4. Custom Java Tools for Karate Scenarios

### A. Universal Database Poller (`DbHelper.java`)
- `DbHelper.query(Map<String, Object> config)`: Executes SQL query and returns `List<Map<String, Object>>` with case-insensitive column keys.
- `DbHelper.poll(Map<String, Object> config)`: Polls with configurable `maxAttempts`, `intervalMs`, `timeoutMs`, and JavaScript `condition` (e.g. `row.status == 'COMPLETED'`) or `expectedRowCount`.
- `DbHelper.executeUpdate(Map<String, Object> config)`: Executes DDL/DML update and returns rows affected count.

### B. Universal Kafka Helper (`KafkaHelper.java`)
- `KafkaHelper.send(Map<String, Object> config)`: Produces Kafka messages with `topic`, `key`, `payload` (JSON or String), and custom `headers`.
- `KafkaHelper.poll(Map<String, Object> config)`: Consumes messages with `topic`, `groupId`, `timeoutMs`, `keyMatch`, `jsonPath`, and `expectedValue` assertions.
- Includes `mock: true` and `fallbackToMock: true` modes for offline developer testing without active Kafka clusters.

### C. Global Pre-Binding (`karate-config.js`)
```javascript
function fn() {
  karate.configure('connectTimeout', 15000);
  karate.configure('readTimeout', 30000);
  karate.configure('ssl', true);

  return {
    env: karate.env || 'default',
    Db: Java.type('com.orchestrator.platform.karate.DbHelper'),
    Kafka: Java.type('com.orchestrator.platform.karate.KafkaHelper')
  };
}
```

---

## 5. First-Boot Reference Workflow Seeding (`StandaloneDataSeeder.java`)

On initial launch when `./data/meterflow.mv.db` is empty:
1. Detects that the database is fresh.
2. Ingests demo microservices (Auth, Order, Payment).
3. Auto-seeds 8 reference workflows:
   - `[Reference] REST API Chaining & Auth Lifecycle`
   - `[Reference] Database SQL Polling & Variable Chaining`
   - `[Reference] Kafka Streaming Assertion & Event Verification`
   - `[Reference] JavaScript Transform Scripting & Payload Mutation`
   - `[Reference] Multi-Environment Scoped Properties & Secrets`
   - `[Reference] Reusable Child Sub-Workflow (Auth Extraction)`
   - `[Reference] Master Sub-Workflow Composition & Context Inheritance`
   - `[Reference] Async Polling & Resilience (Delay & Failure Handling)`
4. On subsequent boots, recognizes existing records and preserves all user edits without overwriting.

---

## 6. Execution Commands

### Build
```bash
# Compile React UI + Package Executable JAR
./build.sh
```

### Run
```bash
# Run with 256MB memory cap (0 Docker, 0 Postgres)
./run.sh
```
Then open `http://localhost:8080` in your browser!
```
