<!-- GSD:project-start source:PROJECT.md -->

## Project

**E2E Orchestrator Platform - Frontend Node 26 LTS Upgrade**

The End-to-End Test Workflow Orchestrator Platform is a full-stack developer tool enabling visual construction, execution, and real-time monitoring of automated API, database, and event-driven test workflows. This milestone modernizes the frontend development toolchain and container build pipeline by upgrading to Node.js 26 LTS.

**Core Value:** Ensure frontend build reliability, security, and developer environment consistency on the latest Node.js 26 LTS runtime without breaking existing visual canvas workflows or packaging pipelines.

### Constraints

- **Compatibility**: All existing frontend packages (`@xyflow/react`, `@stomp/stompjs`, `axios`, `clsx`, `lucide-react`, `sockjs-client`, `tailwind-merge`) must build without errors under Node 26.
- **Docker**: The frontend Dockerfile must build reliably with `node:26-alpine`.
- **Zero Breaking Changes**: The visual canvas UI and execution streaming behavior must not change.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Java 25 LTS - Backend services, Karate execution runner, AST compiler, JPA persistence (`backend/pom.xml`, `backend/src/main/java`)
- TypeScript 5.6.3 - Frontend visual canvas, drawer configuration, API client, auto-layout engine (`frontend/tsconfig.json`, `frontend/src`)
- JavaScript / Karate DSL - Dynamic test execution features and JS transform node scripting (`backend/src/main/resources/karate/`)
- SQL / PostgreSQL DDL - Database schema definition and versioned migrations (`backend/src/main/resources/db/migration/`)
- HTML5 / CSS3 / Tailwind - Frontend markup, layout styling, and CSS variables for theme switching (`frontend/src/index.css`, `frontend/tailwind.config.js`)
- Bash / Shell / PowerShell - Container entrypoints, multi-arch Docker build and push scripts (`docker-build-push.sh`, `docker-build-push.ps1`, `backend/docker-entrypoint.sh`)

## Runtime

- Backend: Java 25 LTS (OpenJDK / Eclipse Temurin), Generational ZGC (`-XX:+UseZGC`), Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`)
- Frontend: Node.js 20+ (development and Vite build), Nginx 1.27 Alpine (production container serving static build)
- Container: Alpine Linux 3.21 with custom modular `jlink` JRE runtime (55 MB footprint)
- Backend: Apache Maven 3.9+ (`backend/pom.xml`)
- Frontend: npm (`frontend/package.json`)
- Lockfile: `frontend/package-lock.json` present

## Frameworks

- Spring Boot 4.1.2 - Backend REST APIs, transaction management, dependency injection, WebSocket STOMP messaging (`backend/pom.xml`)
- React 18.3.1 - Frontend reactive UI layer and component tree (`frontend/package.json`)
- @xyflow/react 12.3.6 (React Flow) - Visual interactive drag-and-drop workflow canvas (`frontend/src/components/canvas/FlowCanvas.tsx`)
- Karate Core 2.1.4 (`io.karatelabs:karate-core`) - Dynamic embedded API, DB, and Kafka test scenario engine (`backend/pom.xml`)
- Karate JUnit 6 (`io.karatelabs:karate-junit6`) - Integration test bridge for Karate features
- Spring Boot Test Starter (JUnit Jupiter 5, Mockito, Spring Test) - Unit and integration testing (`backend/src/test/java`)
- Vite 5.4.9 - Frontend fast bundler with React plugin and Rollup manual chunking (`frontend/vite.config.ts`)
- Tailwind CSS 3.4.14 & PostCSS 8.4.47 - Utility-first styling framework (`frontend/tailwind.config.js`, `frontend/postcss.config.js`)
- Spring Boot Maven Plugin 4.1.2 - Backend packaging and execution (`backend/pom.xml`)
- Maven Compiler Plugin 3.13+ - Configured for Java 25 with `-parameters` argument (`backend/pom.xml`)

## Key Dependencies

- `io.karatelabs:karate-core` (v2.1.4) - Embedded test execution engine driving dynamic scenarios (`backend/pom.xml`)
- `@xyflow/react` (v12.3.6) - Interactive node graph canvas with custom nodes, handles, and viewport (`frontend/package.json`)
- `io.swagger.parser.v3:swagger-parser` (v2.1.22) - OpenAPI 3.x and Swagger 2.0 spec parsing and payload generation (`backend/src/main/java/com/orchestrator/platform/service/OpenApiCatalogService.java`)
- `com.jayway.jsonpath:json-path` (v2.9.0) - Jayway JSONPath engine for variable extraction and response assertions (`backend/src/main/java/com/orchestrator/platform/service/WorkflowContextEngine.java`)
- `org.apache.kafka:kafka-clients` (v3.8.0) - Kafka consumer assertions for event-driven testing (`backend/src/main/java/com/orchestrator/platform/service/execution/KafkaAssertExecutor.java`)
- `@stomp/stompjs` (v7.0.0) & `sockjs-client` (v1.6.1) - WebSocket STOMP protocol client for real-time log broadcasting (`frontend/src/components/console/LiveExecutionConsole.tsx`)
- `axios` (v1.7.7) - HTTP client for backend REST API calls (`frontend/src/api/client.ts`)
- `org.postgresql:postgresql` (v42.7+) - PostgreSQL JDBC driver for application persistence (`backend/pom.xml`)
- `org.flywaydb:flyway-database-postgresql` - Database migration automation (`backend/src/main/resources/db/migration`)
- `com.zaxxer:HikariCP` - Production connection pool manager for application and dynamic test steps (`backend/src/main/java/com/orchestrator/platform/config/HikariConnectionPoolManager.java`)
- `com.oracle.database.jdbc:ojdbc17`, `com.mysql:mysql-connector-j`, `org.mariadb.jdbc:mariadb-java-client`, `com.microsoft.sqlserver:mssql-jdbc`, `org.xerial:sqlite-jdbc`, `com.h2database:h2` - Bundled universal JDBC drivers for database test steps (`backend/pom.xml`)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` (v2.8.6) - Self-hosted Swagger UI and OpenAPI documentation at `/swagger-ui.html` (`backend/pom.xml`)
- `lucide-react` (v0.453.0) - Icon set for canvas toolbar, sidebar, and modals (`frontend/package.json`)

## Configuration

- Backend: Configured via `backend/src/main/resources/application.yml` with support for environment variable overrides (`PORT`, `SPRING_DATASOURCE_URL`, `ORCHESTRATOR_REPORTS_PATH`)
- Test Profile: Dedicated test configuration at `backend/src/main/resources/application-test.yml` using H2 in-memory database
- Container Environment: Orchestrated via `docker-compose.yml` and `docker-compose.hub.yml`
- In-Workflow Secrets: Interpolated at runtime via `${$env:VAR_NAME}` from host/container environment
- Backend: `backend/pom.xml` targeting Java 25 with Surefire `--enable-native-access=ALL-UNNAMED`
- Frontend: `frontend/vite.config.ts` targeting ES2022 with manual chunk splitting for vendor libraries
- TypeScript: `frontend/tsconfig.json` with strict mode enabled

## Platform Requirements

- Java 25 LTS (`sdk install java 25.0.3-tem` or Temurin 25)
- Node.js 20+ and npm 10+
- Maven 3.9+
- PostgreSQL 16 (local installation or via `docker compose up -d postgres`)
- Containerized Linux environment (Docker / Kubernetes / Docker Compose)
- Multi-architecture container images (`linux/amd64` and `linux/arm64`)
- Minimum 1024MB RAM allocated for Spring Boot backend container

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Naming Patterns

- Java source files use PascalCase matching public class/interface name: `DynamicKarateRunner.java`, `WorkflowExecutionController.java` (`backend/src/main/java`)
- React component files use PascalCase with `.tsx`: `FlowCanvas.tsx`, `NodeConfigDrawer.tsx` (`frontend/src/components`)
- TypeScript utilities and API modules use camelCase: `client.ts`, `layoutEngine.ts` (`frontend/src/utils`)
- Database migrations use Flyway versioned prefix `V{N}__{description}.sql`: `V1__init_orchestrator_schema.sql` (`backend/src/main/resources/db/migration`)
- Karate feature files use kebab-case: `generic-flow-executor.feature`, `execute-http.feature` (`backend/src/main/resources/karate`)
- Java methods use camelCase starting with action verbs: `executeWorkflow()`, `triggerExecution()`, `resolveReportDir()`, `sanitizeContextSnapshot()`
- TypeScript functions and React hooks use camelCase: `calculateTopologicalLayout()`, `subscribeToExecutionEvents()`, `useCallback()`
- React functional components use PascalCase: `HttpRequestNode`, `ReportViewerModal`
- Local variables and parameters use camelCase: `executionId`, `reportDir`, `activeContexts`, `keepLatest`
- Constant fields in Java use SCREAMING_SNAKE_CASE: `DEFAULT_PORTS`, `MAX_ATTEMPTS`
- Java Enums use SCREAMING_SNAKE_CASE: `ExecutionStatus.RUNNING`, `StepType.HTTP_REQUEST` (`backend/src/main/java/com/orchestrator/platform/domain/enums`)
- Java Classes, Interfaces, Enums, and Records use PascalCase: `WorkflowExecutionEntity`, `StepAssertion`, `HttpRequestStepConfig`
- TypeScript interfaces and types use PascalCase: `TestWorkflow`, `WorkflowStep`, `ApiEndpoint`, `ExecutionEventDto` (`frontend/src/types/workflow.ts`)

## Code Style

- Java: Standard 4-space indentation; brackets on same line (K&R style); parameters annotated with `@JsonProperty` or Jakarta validation
- TypeScript / React: 2-space indentation; ES module imports; Tailwind CSS utility classes organized logically (layout, sizing, typography, colors, dark mode variants)
- Backend: Maven Compiler Plugin with `-parameters` flag and strict Java 25 compiler checking
- Frontend: TypeScript compiler (`tsc --noEmit` in `npm run build`) with strict type checking enabled in `frontend/tsconfig.json` (`strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`)

## Import Organization

- Standard relative imports (`./`, `../`) used across TypeScript files (`frontend/src/App.tsx:7`)

## Error Handling

- Backend Controller Layer: Returns standard Spring `ResponseEntity` with descriptive HTTP status codes (`HttpStatus.NOT_FOUND`, `HttpStatus.ACCEPTED`, `HttpStatus.CREATED`)
- Backend Execution Engine: Encapsulates exceptions within `WorkflowExecutionEntity.errorMessage` and marks execution status as `ExecutionStatus.FAILED` without crashing virtual threads (`DynamicKarateRunner.java:188`)
- Cancellation Checking: Cooperative cancellation via `DynamicKarateBridge.isExecutionCancelled(executionId)` checked before delay pauses and step executions
- Frontend API: Uses Axios with try/catch blocks; error callbacks for SSE streams (`eventSource.onerror`) to clean up listeners and notify users

## Logging

- Backend: SLF4J with Logback (`LoggerFactory.getLogger(Class.class)`)
- Frontend: Browser console (`console.error`, `console.warn`) for unexpected parsing or network errors
- Logging levels: INFO for execution lifecycle events (`Starting Dynamic Karate Execution [id={}, workflow={}]`), DEBUG for step inputs/outputs, ERROR for trapped exceptions
- Secret Redaction: All logs emitted through `ExecutionLogBroadcaster` pass through secret sanitization (`redactSecrets(...)`) replacing token values with `[REDACTED]`

## Comments

- Explaining non-obvious architecture decisions (e.g., in-memory bridge synchronization, virtual thread isolation, fallback paths for Docker directory permissions)
- Swagger / OpenAPI documentation annotations on all REST controller endpoints (`@Operation(summary = ..., description = ...)`)
- Section markers in complex components (e.g., `// 1. Environment Properties`, `// 2. Global Workflow Variables`)
- Used on utility algorithms such as `layoutEngine.ts` to document inputs, outputs, and Kahn's algorithm complexity

## Function Design

- Targeted single-responsibility methods in backend services (typically 15-40 lines)
- Large configuration drawer (`NodeConfigDrawer.tsx`) contains distinct sub-render functions for each step type
- Complex step configurations use dedicated typed DTOs (`HttpRequestStepConfig`, `DbPollStepConfig`, `KafkaAssertStepConfig`) rather than loose parameter lists
- Java services return `Optional<T>` for queries that may not find an entity (`getWorkflowById(UUID id)`)
- API endpoints return strongly typed `ResponseEntity<T>`

## Module Design

- Named exports preferred in TypeScript utility and API modules: `export const workflowApi = ...` (`frontend/src/api/client.ts`)
- Default exports used for primary React components: `export default FlowCanvas` (`frontend/src/components/canvas/FlowCanvas.tsx`)
- Centralized types defined in `frontend/src/types/workflow.ts` and re-exported across components without multiple scattered type files

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## System Overview

```text

```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `WorkflowController` | REST endpoints for visual workflow CRUD, canvas state saving, and standalone Karate `.feature`/JSON export and import | `backend/src/main/java/com/orchestrator/platform/web/WorkflowController.java` |
| `WorkflowExecutionController` | Triggers async execution via virtual threads, provides execution cancellation, queries execution history, and serves Karate HTML reports | `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` |
| `ApiCatalogController` | Manages OpenAPI/Swagger ingestion, endpoint catalog filtering, and mock seed generation | `backend/src/main/java/com/orchestrator/platform/web/ApiCatalogController.java` |
| `MockTargetApiController` | Built-in mock endpoints for auth, orders, payments, and inventory to enable zero-dependency out-of-the-box test runs | `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java` |
| `TestWorkflowExecutionService` | Offloads workflow execution to Java 25 Virtual Threads, manages cancellation signals, and executes automated storage retention pruner | `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java` |
| `DynamicKarateRunner` | Generates workflow-attributed Karate feature file in staging, launches Karate Suite runner, monitors step completion, and triggers HTML report generation | `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java` |
| `DynamicKarateBridge` | Static in-memory bridge binding execution state, step configurations, and variable contexts directly to Karate Gherkin feature scenarios | `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java` |
| `WorkflowContextEngine` | Evaluates dynamic macros (`${$timestamp}`, `${$randomUuid}`), resolves `${$env:SECRET}` variables, and extracts JSONPath variables from payloads | `backend/src/main/java/com/orchestrator/platform/service/WorkflowContextEngine.java` |
| `DbPollExecutor` | Polls target databases (Postgres, Oracle, H2) through pooled HikariCP connections until assertions or timeouts occur | `backend/src/main/java/com/orchestrator/platform/service/execution/DbPollExecutor.java` |
| `KafkaAssertExecutor` | Consumes messages from Kafka topics and evaluates JSON assertions against payloads | `backend/src/main/java/com/orchestrator/platform/service/execution/KafkaAssertExecutor.java` |
| `ExecutionLogBroadcaster` | Dispatches real-time step progress and log events across SSE and STOMP WebSockets, redacting registered secrets | `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionLogBroadcaster.java` |
| `FlowCanvas` | Visual canvas powered by `@xyflow/react` rendering custom interactive nodes, connecting edges, and viewport panning | `frontend/src/components/canvas/FlowCanvas.tsx` |
| `NodeConfigDrawer` | Multi-panel configuration drawer allowing users to edit step parameters, headers, assertions, variable extractions, and retry policies | `frontend/src/components/drawer/NodeConfigDrawer.tsx` |
| `LiveExecutionConsole` | Slide-up console displaying live execution progress, step status badges, streamed logs, and report viewer triggers | `frontend/src/components/console/LiveExecutionConsole.tsx` |
| `layoutEngine` | Topological auto-layout engine based on Kahn's topological sort algorithm to hierarchically arrange nodes | `frontend/src/utils/layoutEngine.ts` |
| `DbHelper` | Standalone zero-dependency JDBC polling, querying, and updating utility for Karate features with automatic driver detection and dynamic external driver loading | `backend/src/main/java/com/orchestrator/platform/karate/DbHelper.java` |
| `KarateStandaloneMain` | Standalone executable CLI entry point bundling Karate Core 2.1.4, multi-database JDBC drivers, and dynamic external JAR loading via `--driver-dir` | `backend/src/main/java/com/orchestrator/platform/karate/standalone/KarateStandaloneMain.java` |

## Pattern Overview

- **Asynchronous Non-Blocking Execution:** Runs on Java 25 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), returning immediate HTTP 202 Accepted responses.
- **Dynamic In-Memory Karate DSL Interop:** Test steps execute through reusable Karate feature templates (`execute-http.feature`, `execute-db.feature`, etc.) that pull runtime configuration directly from the Java runtime bridge (`DynamicKarateBridge`) without generating temporary test files for each step.
- **Dual Representation (Canvas & AST):** The frontend stores visual UI node positions (`CanvasState`) and translates connections into an execution graph (`WorkflowAst`), persisted together in PostgreSQL JSONB columns.
- **Standalone Self-Contained Feature Export & Universal DB Runner:** Workflows export to 100% self-contained Karate Gherkin feature files with recursive sub-workflow inlining, variable scope binding, and inline JDBC functions. For offline / terminal / CI execution, the `standalone-karate` profile packages `meterflow-karate-standalone.jar` bundling Karate Core 2.1.4, multi-database JDBC drivers (Postgres, MySQL, SQL Server, Oracle, MariaDB, SQLite, H2), and dynamic `--driver-dir` JAR loading via `DbHelper`.

## Layers

- Purpose: Interactive canvas, API endpoint catalog, configuration drawer, execution streaming console
- Location: `frontend/src/`
- Contains: React components, `@xyflow/react` nodes, Tailwind styles, Axios client
- Depends on: Backend REST API (`/api/v1`) and WebSocket broker (`/ws`)
- Used by: End users, test engineers, CI/CD webhook callers
- Purpose: Exposes RESTful endpoints, handles HTTP requests, SSE streaming, and OpenAPI specs
- Location: `backend/src/main/java/com/orchestrator/platform/web/`
- Contains: Spring `@RestController` classes, request DTOs, Swagger annotations
- Depends on: Service layer
- Used by: Frontend client, external webhook triggers
- Purpose: Business workflows, catalog parsing, virtual thread lifecycle, variable resolution, and retention cleanup
- Location: `backend/src/main/java/com/orchestrator/platform/service/`
- Contains: Spring `@Service` classes, context engines, thread executors
- Depends on: Execution engine layer, repository layer
- Used by: Web controllers
- Purpose: Dynamic test execution with Karate 2.1.4, database polling, Kafka consumer assertions, and log broadcasting
- Location: `backend/src/main/java/com/orchestrator/platform/service/execution/` and `backend/src/main/resources/karate/`
- Contains: `DynamicKarateRunner`, `DynamicKarateBridge`, Karate `.feature` files, Karate JavaScript runtime
- Depends on: `HikariConnectionPoolManager`, `WorkflowExecutionRepository`
- Used by: `TestWorkflowExecutionService`
- Purpose: Relational database access, Flyway migrations, and JSONB document storage
- Location: `backend/src/main/java/com/orchestrator/platform/repository/` and `backend/src/main/resources/db/migration/`
- Contains: Spring Data JPA repositories, entity classes, Flyway SQL scripts
- Depends on: PostgreSQL / HikariCP
- Used by: Service layer and execution runner

## Data Flow

### Primary Request Path (Workflow Execution)

### Secondary Flow (OpenAPI Spec Ingestion to Canvas)

- Frontend State: React component state, local storage for theme (`theme-mode`), and `@xyflow/react` node/edge hooks (`useNodesState`, `useEdgesState`).
- Execution State: Transient in-memory `ConcurrentHashMap` in `DynamicKarateBridge` during run; durable state in PostgreSQL `workflow_executions` table (`context_snapshot`, `step_results`).

## Key Abstractions

- Purpose: Machine-readable compiled test workflow containing ordered/branching steps, variable definitions, environment scopes, and default headers
- Examples: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java`, `frontend/src/types/workflow.ts`
- Pattern: Abstract Syntax Tree (AST)
- Purpose: Polymorphic step model holding step type (`HTTP_REQUEST`, `DB_POLL`, `KAFKA_ASSERT`, `DELAY`, `TRANSFORM`, `SUB_WORKFLOW`), retry policies, variable extractions, and assertions
- Examples: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowStep.java`
- Pattern: Strategy / Discriminated Union
- Purpose: High-speed bidirectional communication gateway bridging Karate Gherkin step execution with Spring Boot managed services and memory state
- Examples: `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`
- Pattern: Facade / Singleton Bridge

## Entry Points

- Location: `backend/src/main/java/com/orchestrator/platform/E2EOrchestratorApplication.java`
- Triggers: `mvn spring-boot:run` or container boot (`java org.springframework.boot.loader.launch.JarLauncher`)
- Responsibilities: Boots Spring application context, runs Flyway migrations, configures virtual thread executor, initializes WebSocket broker
- Location: `frontend/src/main.tsx`
- Triggers: Web browser visiting `http://localhost:5173` or container port `3000`
- Responsibilities: Renders React DOM, initializes `ThemeProvider`, mounts main `App.tsx` layout
- Location: `backend/src/main/resources/karate/generic-flow-executor.feature`
- Triggers: Executed by `DynamicKarateRunner` via `Runner.path(...)`
- Responsibilities: Iterates through AST steps, conditionally executes specific feature files (`execute-http.feature`, `execute-db.feature`), and records metrics

**Standalone Karate Runner (CLI):**
- Location: `backend/src/main/java/com/orchestrator/platform/karate/standalone/KarateStandaloneMain.java`
- Triggers: Running shaded JAR (`java -jar meterflow-karate-standalone.jar -f workflow.feature [--driver-dir /path/to/drivers]`)
- Responsibilities: Scans driver directory, registers external JDBC drivers dynamically via `URLClassLoader`, displays terminal banner, and dispatches execution to Karate Core's CLI engine

## Architectural Constraints

- **Threading:** Spring Boot virtual threads (`spring.threads.virtual.enabled=true`) are used for execution jobs. Blocking I/O (Karate HTTP requests, DB polling loops) is permitted without exhausting thread pools.
- **Global state:** `DynamicKarateBridge` maintains static concurrent maps for active executions. Execution cleanup must be called to prevent memory leaks on failed or aborted tasks.
- **Karate Scenario Synchronization:** Karate scenarios use scenario-level synchronization lock (`@lock=workflow-execution`) to prevent race conditions during execution.
- **File System Permissions:** HTML reports require write access to `ORCHESTRATOR_REPORTS_PATH`. A resilient fallback to `java.io.tmpdir` is implemented if primary directory creation encounters permission errors.

## Anti-Patterns

### Modifying Step Logic by Writing Custom Temporary Feature Files

### Storing Unmasked Secrets in Execution Logs

## Error Handling

- Step-level retries: `HttpRequestStepConfig` supports `retryCount` (1-5) and `retryIntervalMs`, configured as native Karate retries (`configure retry = ...`).
- Step-level muting: Disabled steps are flagged with `enabled: false` and safely bypassed by the Karate runner without failing downstream steps.
- Cancellation handling: `DynamicKarateBridge.isExecutionCancelled` checks cancellation flags at the start of each step and delay interval, aborting remaining steps safely.

## Cross-Cutting Concerns

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.agents/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-fast` for a trivial task inline, with no subagents and no PLAN.md
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
