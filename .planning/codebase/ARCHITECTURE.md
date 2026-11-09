---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
<!-- refreshed: 2026-11-06 -->
# Architecture

**Analysis Date:** 2026-11-06

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                   Frontend (React 18 / Vite / Tailwind)                     │
├──────────────────────┬──────────────────────┬───────────────────────────────┤
│    ApiCatalogSidebar │      FlowCanvas      │       NodeConfigDrawer        │
│ `components/catalog` │ `components/canvas`  │ `components/drawer`           │
└──────────┬───────────┴──────────┬───────────┴───────────────┬───────────────┘
           │                      │                           │
           ▼                      ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Frontend API & State Layer                            │
│  `frontend/src/api/client.ts` · Axios `/api/v1` · SSE `/stream` · WS `/ws`  │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ HTTP / WebSocket
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 4.1.2 Controller Layer                      │
│ `web/WorkflowController.java` · `web/WorkflowExecutionController.java`      │
│ `web/ApiCatalogController.java` · `web/MockTargetApiController.java`        │
└──────────┬──────────────────────────┬───────────────────────────────┬───────┘
           │                          │                               │
           ▼                          ▼                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Service & Engine Layer                             │
│ ┌─────────────────────────┐  ┌────────────────────────────────────────────┐ │
│ │ OpenApiCatalogService   │  │ TestWorkflowExecutionService               │ │
│ │ (Swagger parser v3)     │  │ (Java 25 Virtual Thread Task Pool)         │ │
│ └─────────────────────────┘  └─────────────────────┬──────────────────────┘ │
│                                                    │                        │
│ ┌──────────────────────────────────────────────────┴──────────────────────┐ │
│ │ DynamicKarateRunner & DynamicKarateBridge (Embedded Karate 2.1.4 DSL)   │ │
│ │  - execute-http.feature       - execute-db.feature                      │ │
│ │  - execute-kafka.feature      - execute-delay.feature                   │ │
│ │  - execute-transform.feature  - execute-subworkflow.feature             │ │
│ └──────────────────────────┬──────────────────────────────────────────────┘ │
│                            │                                                │
│ ┌──────────────────────────┴───────────────┐ ┌────────────────────────────┐ │
│ │ WorkflowContextEngine                    │ │ ExecutionLogBroadcaster    │ │
│ │ (Jayway JsonPath & Secret Interpolator)  │ │ (SSE & STOMP Broadcaster)  │ │
│ └──────────────────────────────────────────┘ └────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Storage Layer                                   │
│  PostgreSQL 16 (JSONB AST & Executions)  │  Karate HTML Reports (Disk)      │
│  `repository/TestWorkflowRepository`     │  `/app/reports/{executionId}/`   │
└─────────────────────────────────────────────────────────────────────────────┘
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

**Overall:** Event-Driven Asynchronous Test Orchestration with In-Memory DSL Execution Bridge and Visual Graph AST Compilation.

**Key Characteristics:**
- **Asynchronous Non-Blocking Execution:** Runs on Java 25 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), returning immediate HTTP 202 Accepted responses.
- **Dynamic In-Memory Karate DSL Interop:** Test steps execute through reusable Karate feature templates (`execute-http.feature`, `execute-db.feature`, etc.) that pull runtime configuration directly from the Java runtime bridge (`DynamicKarateBridge`) without generating temporary test files for each step.
- **Dual Representation (Canvas & AST):** The frontend stores visual UI node positions (`CanvasState`) and translates connections into an execution graph (`WorkflowAst`), persisted together in PostgreSQL JSONB columns.
- **Standalone Self-Contained Feature Export & Universal DB Runner:** Workflows export to 100% self-contained Karate Gherkin feature files with recursive sub-workflow inlining, variable scope binding, and inline JDBC functions. For offline / terminal / CI execution, the `standalone-karate` profile packages `meterflow-karate-standalone.jar` bundling Karate Core 2.1.4, multi-database JDBC drivers (Postgres, MySQL, SQL Server, Oracle, MariaDB, SQLite, H2), and dynamic `--driver-dir` JAR loading via `DbHelper`.

## Layers

**Presentation Layer (Frontend UI):**
- Purpose: Interactive canvas, API endpoint catalog, configuration drawer, execution streaming console
- Location: `frontend/src/`
- Contains: React components, `@xyflow/react` nodes, Tailwind styles, Axios client
- Depends on: Backend REST API (`/api/v1`) and WebSocket broker (`/ws`)
- Used by: End users, test engineers, CI/CD webhook callers

**Web / Controller Layer:**
- Purpose: Exposes RESTful endpoints, handles HTTP requests, SSE streaming, and OpenAPI specs
- Location: `backend/src/main/java/com/orchestrator/platform/web/`
- Contains: Spring `@RestController` classes, request DTOs, Swagger annotations
- Depends on: Service layer
- Used by: Frontend client, external webhook triggers

**Service & Orchestration Layer:**
- Purpose: Business workflows, catalog parsing, virtual thread lifecycle, variable resolution, and retention cleanup
- Location: `backend/src/main/java/com/orchestrator/platform/service/`
- Contains: Spring `@Service` classes, context engines, thread executors
- Depends on: Execution engine layer, repository layer
- Used by: Web controllers

**Execution Engine Layer:**
- Purpose: Dynamic test execution with Karate 2.1.4, database polling, Kafka consumer assertions, and log broadcasting
- Location: `backend/src/main/java/com/orchestrator/platform/service/execution/` and `backend/src/main/resources/karate/`
- Contains: `DynamicKarateRunner`, `DynamicKarateBridge`, Karate `.feature` files, Karate JavaScript runtime
- Depends on: `HikariConnectionPoolManager`, `WorkflowExecutionRepository`
- Used by: `TestWorkflowExecutionService`

**Persistence Layer:**
- Purpose: Relational database access, Flyway migrations, and JSONB document storage
- Location: `backend/src/main/java/com/orchestrator/platform/repository/` and `backend/src/main/resources/db/migration/`
- Contains: Spring Data JPA repositories, entity classes, Flyway SQL scripts
- Depends on: PostgreSQL / HikariCP
- Used by: Service layer and execution runner

## Data Flow

### Primary Request Path (Workflow Execution)

1. User clicks **"Execute"** in canvas toolbar (`frontend/src/components/navbar/Navbar.tsx`) or triggers via webhook.
2. Frontend issues `POST /api/v1/workflows/{id}/execute` (`frontend/src/api/client.ts`).
3. `WorkflowExecutionController.executeWorkflow` delegates to `TestWorkflowExecutionService.triggerExecution` (`backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:54`).
4. `TestWorkflowExecutionService` persists execution entity in `PENDING` state and spawns a virtual thread task (`backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java:56`).
5. Returns HTTP 202 Accepted with execution ID immediately to client.
6. `DynamicKarateRunner.executeWorkflow` resolves environment variables and secrets via `WorkflowContextEngine` and stages dynamic feature file (`backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java:70`).
7. `Runner.path(...).parallel(1)` invokes Karate Suite runner; Karate scenarios invoke Java bridge static methods (`DynamicKarateBridge.getStepConfig(...)`).
8. `ExecutionLogBroadcaster` pushes live step events over SSE and STOMP WebSockets (`backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionLogBroadcaster.java:65`).
9. On completion, `WorkflowReportGenerator` creates Karate HTML reports on disk and updates execution record to `COMPLETED` or `FAILED`.
10. `TestWorkflowExecutionService` auto-prunes historical executions exceeding `maxRunsPerWorkflow`.

### Secondary Flow (OpenAPI Spec Ingestion to Canvas)

1. User imports Swagger JSON/YAML URL or raw text in `ImportSpecModal.tsx`.
2. `ApiCatalogController.ingestSpec` calls `OpenApiCatalogService.ingestOpenApiSpec` (`backend/src/main/java/com/orchestrator/platform/service/OpenApiCatalogService.java:38`).
3. Swagger parser validates spec, extracts endpoints, parameters, request body schemas, and generates smart sample JSON payloads.
4. Endpoints are saved into `api_endpoint_catalog` table.
5. User drags endpoint from `ApiCatalogSidebar.tsx` onto `FlowCanvas.tsx`, creating an `HTTP_REQUEST` canvas node with pre-filled method, URL, and sample payload.

**State Management:**
- Frontend State: React component state, local storage for theme (`theme-mode`), and `@xyflow/react` node/edge hooks (`useNodesState`, `useEdgesState`).
- Execution State: Transient in-memory `ConcurrentHashMap` in `DynamicKarateBridge` during run; durable state in PostgreSQL `workflow_executions` table (`context_snapshot`, `step_results`).

## Key Abstractions

**`WorkflowAst`:**
- Purpose: Machine-readable compiled test workflow containing ordered/branching steps, variable definitions, environment scopes, and default headers
- Examples: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java`, `frontend/src/types/workflow.ts`
- Pattern: Abstract Syntax Tree (AST)

**`WorkflowStep`:**
- Purpose: Polymorphic step model holding step type (`HTTP_REQUEST`, `DB_POLL`, `KAFKA_ASSERT`, `DELAY`, `TRANSFORM`, `SUB_WORKFLOW`), retry policies, variable extractions, and assertions
- Examples: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowStep.java`
- Pattern: Strategy / Discriminated Union

**`DynamicKarateBridge`:**
- Purpose: High-speed bidirectional communication gateway bridging Karate Gherkin step execution with Spring Boot managed services and memory state
- Examples: `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`
- Pattern: Facade / Singleton Bridge

**`DbHelper`:**
- Purpose: Zero-dependency database polling, query execution, update execution, and dynamic external driver registration for Karate features
- Examples: `backend/src/main/java/com/orchestrator/platform/karate/DbHelper.java`
- Pattern: Utility / Helper

## Entry Points

**Backend Application:**
- Location: `backend/src/main/java/com/orchestrator/platform/E2EOrchestratorApplication.java`
- Triggers: `mvn spring-boot:run` or container boot (`java org.springframework.boot.loader.launch.JarLauncher`)
- Responsibilities: Boots Spring application context, runs Flyway migrations, configures virtual thread executor, initializes WebSocket broker

**Frontend Application:**
- Location: `frontend/src/main.tsx`
- Triggers: Web browser visiting `http://localhost:5173` or container port `3000`
- Responsibilities: Renders React DOM, initializes `ThemeProvider`, mounts main `App.tsx` layout

**Karate Dynamic Runner:**
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
**What happens:** Generating new ad-hoc Gherkin feature files on disk for every step configuration.
**Why it's wrong:** High disk I/O, cleanup race conditions, container storage bloat, and slower execution.
**Do this instead:** Route through `generic-flow-executor.feature` and `DynamicKarateBridge` so Karate fetches configuration directly from in-memory objects.

### Storing Unmasked Secrets in Execution Logs
**What happens:** Broadcasting raw values of `${$env:SECRET}` or workflow secrets over WebSocket/SSE streams.
**Why it's wrong:** Leaks sensitive credentials to frontend browser consoles and observer tools.
**Do this instead:** Register all environment and workflow secrets in `ExecutionLogBroadcaster.registerSecrets` before execution starts; the broadcaster automatically scrubs them.

## Error Handling

**Strategy:** Comprehensive per-step error containment with step retry policies, execution cancellation signals, and graceful failure propagation.

**Patterns:**
- Step-level retries: `HttpRequestStepConfig` supports `retryCount` (1-5) and `retryIntervalMs`, configured as native Karate retries (`configure retry = ...`).
- Step-level muting: Disabled steps are flagged with `enabled: false` and safely bypassed by the Karate runner without failing downstream steps.
- Cancellation handling: `DynamicKarateBridge.isExecutionCancelled` checks cancellation flags at the start of each step and delay interval, aborting remaining steps safely.

## Cross-Cutting Concerns

**Logging:** SLF4J with Logback in backend; `ExecutionLogBroadcaster` intercepts and dispatches execution logs to WebSocket `/topic/execution/{id}/events` and SSE `/api/v1/executions/{id}/stream`.
**Validation:** Jakarta Bean Validation (`@Valid`, `@NotNull`) on REST endpoints; JSON Schema validation on OpenAPI imports.
**Authentication:** Environment variable and header secret interpolation with runtime log masking; built-in mock JWT token generation.

---

*Architecture analysis: 2026-11-06*
