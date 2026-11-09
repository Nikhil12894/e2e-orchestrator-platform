---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
# Technology Stack

**Analysis Date:** 2026-11-06

## Languages

**Primary:**
- Java 25 LTS - Backend services, Karate execution runner, AST compiler, JPA persistence (`backend/pom.xml`, `backend/src/main/java`)
- TypeScript 5.6.3 - Frontend visual canvas, drawer configuration, API client, auto-layout engine (`frontend/tsconfig.json`, `frontend/src`)

**Secondary:**
- JavaScript / Karate DSL - Dynamic test execution features and JS transform node scripting (`backend/src/main/resources/karate/`)
- SQL / PostgreSQL DDL - Database schema definition and versioned migrations (`backend/src/main/resources/db/migration/`)
- HTML5 / CSS3 / Tailwind - Frontend markup, layout styling, and CSS variables for theme switching (`frontend/src/index.css`, `frontend/tailwind.config.js`)
- Bash / Shell / PowerShell - Container entrypoints, multi-arch Docker build and push scripts (`docker-build-push.sh`, `docker-build-push.ps1`, `backend/docker-entrypoint.sh`)

## Runtime

**Environment:**
- Backend: Java 25 LTS (OpenJDK / Eclipse Temurin), Generational ZGC (`-XX:+UseZGC`), Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`)
- Frontend: Node.js 20+ (development and Vite build), Nginx 1.27 Alpine (production container serving static build)
- Container: Alpine Linux 3.21 with custom modular `jlink` JRE runtime (55 MB footprint)

**Package Manager:**
- Backend: Apache Maven 3.9+ (`backend/pom.xml`)
- Frontend: npm (`frontend/package.json`)
- Lockfile: `frontend/package-lock.json` present

## Frameworks

**Core:**
- Spring Boot 4.1.2 - Backend REST APIs, transaction management, dependency injection, WebSocket STOMP messaging (`backend/pom.xml`)
- React 18.3.1 - Frontend reactive UI layer and component tree (`frontend/package.json`)
- @xyflow/react 12.3.6 (React Flow) - Visual interactive drag-and-drop workflow canvas (`frontend/src/components/canvas/FlowCanvas.tsx`)

**Testing:**
- Karate Core 2.1.4 (`io.karatelabs:karate-core`) - Dynamic embedded API, DB, and Kafka test scenario engine (`backend/pom.xml`)
- Karate JUnit 6 (`io.karatelabs:karate-junit6`) - Integration test bridge for Karate features
- Spring Boot Test Starter (JUnit Jupiter 5, Mockito, Spring Test) - Unit and integration testing (`backend/src/test/java`)

**Build/Dev:**
- Vite 5.4.9 - Frontend fast bundler with React plugin and Rollup manual chunking (`frontend/vite.config.ts`)
- Tailwind CSS 3.4.14 & PostCSS 8.4.47 - Utility-first styling framework (`frontend/tailwind.config.js`, `frontend/postcss.config.js`)
- Spring Boot Maven Plugin 4.1.2 - Backend packaging and execution (`backend/pom.xml`)
- Maven Compiler Plugin 3.13+ - Configured for Java 25 with `-parameters` argument (`backend/pom.xml`)

## Key Dependencies

**Critical:**
- `io.karatelabs:karate-core` (v2.1.4) - Embedded test execution engine driving dynamic scenarios (`backend/pom.xml`)
- `@xyflow/react` (v12.3.6) - Interactive node graph canvas with custom nodes, handles, and viewport (`frontend/package.json`)
- `io.swagger.parser.v3:swagger-parser` (v2.1.22) - OpenAPI 3.x and Swagger 2.0 spec parsing and payload generation (`backend/src/main/java/com/orchestrator/platform/service/OpenApiCatalogService.java`)
- `com.jayway.jsonpath:json-path` (v2.9.0) - Jayway JSONPath engine for variable extraction and response assertions (`backend/src/main/java/com/orchestrator/platform/service/WorkflowContextEngine.java`)
- `org.apache.kafka:kafka-clients` (v3.8.0) - Kafka consumer assertions for event-driven testing (`backend/src/main/java/com/orchestrator/platform/service/execution/KafkaAssertExecutor.java`)
- `@stomp/stompjs` (v7.0.0) & `sockjs-client` (v1.6.1) - WebSocket STOMP protocol client for real-time log broadcasting (`frontend/src/components/console/LiveExecutionConsole.tsx`)
- `axios` (v1.7.7) - HTTP client for backend REST API calls (`frontend/src/api/client.ts`)

**Infrastructure:**
- `org.postgresql:postgresql` (v42.7+) - PostgreSQL JDBC driver for application persistence (`backend/pom.xml`)
- `org.flywaydb:flyway-database-postgresql` - Database migration automation (`backend/src/main/resources/db/migration`)
- `com.zaxxer:HikariCP` - Production connection pool manager for application and dynamic test steps (`backend/src/main/java/com/orchestrator/platform/config/HikariConnectionPoolManager.java`)
- `com.oracle.database.jdbc:ojdbc17`, `com.mysql:mysql-connector-j`, `org.mariadb.jdbc:mariadb-java-client`, `com.microsoft.sqlserver:mssql-jdbc`, `org.xerial:sqlite-jdbc`, `com.h2database:h2` - Bundled universal JDBC drivers for database test steps (`backend/pom.xml`)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` (v2.8.6) - Self-hosted Swagger UI and OpenAPI documentation at `/swagger-ui.html` (`backend/pom.xml`)
- `lucide-react` (v0.453.0) - Icon set for canvas toolbar, sidebar, and modals (`frontend/package.json`)

## Configuration

**Environment:**
- Backend: Configured via `backend/src/main/resources/application.yml` with support for environment variable overrides (`PORT`, `SPRING_DATASOURCE_URL`, `ORCHESTRATOR_REPORTS_PATH`)
- Test Profile: Dedicated test configuration at `backend/src/main/resources/application-test.yml` using H2 in-memory database
- Container Environment: Orchestrated via `docker-compose.yml` and `docker-compose.hub.yml`
- In-Workflow Secrets: Interpolated at runtime via `${$env:VAR_NAME}` from host/container environment

**Build:**
- Backend: `backend/pom.xml` targeting Java 25 with Surefire `--enable-native-access=ALL-UNNAMED`
- Frontend: `frontend/vite.config.ts` targeting ES2022 with manual chunk splitting for vendor libraries
- TypeScript: `frontend/tsconfig.json` with strict mode enabled

## Platform Requirements

**Development:**
- Java 25 LTS (`sdk install java 25.0.3-tem` or Temurin 25)
- Node.js 20+ and npm 10+
- Maven 3.9+
- PostgreSQL 16 (local installation or via `docker compose up -d postgres`)

**Production:**
- Containerized Linux environment (Docker / Kubernetes / Docker Compose)
- Multi-architecture container images (`linux/amd64` and `linux/arm64`)
- Minimum 1024MB RAM allocated for Spring Boot backend container

---

*Stack analysis: 2026-11-06*
