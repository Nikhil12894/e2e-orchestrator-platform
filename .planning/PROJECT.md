# E2E Test Workflow Orchestrator

## What This Is

The End-to-End Test Workflow Orchestrator is a full-stack developer platform for visually authoring, executing, and monitoring automated API, database, and event-driven test workflows. Workflows can be constructed visually via an interactive canvas, executed asynchronously or synchronously on Java 25 Virtual Threads with real-time log streaming, and exported into 100% self-contained Karate Gherkin feature files runnable offline.

## Core Value

Ensure complete functional parity and execution fidelity between visual workflows, dynamic in-memory Karate execution, and standalone exported Karate `.feature` files, while providing frictionless execution status tracking and direct HTML report access for automated pipelines and terminal users.

## Current State

- **Shipped v1.0** (2026-11-07): Standalone database polling export via self-contained inline JDBC functions (`java.sql.DriverManager`), `application/x-www-form-urlencoded` HTTP request drawer and engine execution, universal database support, and standalone Karate CLI runner (`meterflow-karate-standalone.jar`).
- **Shipped v1.1** (2026-11-10): Hypermedia execution tracking (RFC 7231 `Location` header, `statusUrl`, `reportUrl`, `streamUrl`), synchronous blocking wait execution mode (`?wait=true&timeoutSec=N`), pre-requisite setup workflow execution, dynamic global flow headers with reactive variable promotion, canvas export badges, and upstream variable suggestion chips.

## Next Milestone Goals

- **CI/CD Webhook & cURL Modal**: Visual modal providing ready-to-use cURL commands for asynchronous polling, synchronous wait execution, and direct Karate HTML report link opening.
- **CI/CD Pipeline Snippets**: Copyable bash and GitHub Actions workflow templates (`.github/workflows/e2e-run.yml`) for automated pipeline integration.
- **Reporting Enhancements**: Dedicated JUnit XML / Karate JSON report download endpoint (`/api/v1/executions/{id}/report/karate-summary.json`) for CI test result parsers.

## Requirements

### Validated

- ✓ Interactive visual canvas powered by `@xyflow/react` with custom step nodes and connections — existing
- ✓ Java 25 Virtual Thread-based asynchronous workflow execution engine — existing
- ✓ Dynamic Karate 2.1.4 in-memory execution bridge (`DynamicKarateBridge`) — existing
- ✓ HTTP request execution with headers, JSON body, assertions, and variable extractions — existing
- ✓ Database polling execution via HikariCP connection pooling (`DbPollExecutor`) — existing
- ✓ Kafka consumer assertion steps for event-driven testing — existing
- ✓ Real-time execution logs and progress streaming via WebSocket STOMP and SSE — existing
- ✓ Standalone Karate `.feature` file export for HTTP, delay, and transform steps — existing
- ✓ **Karate Feature Export for DB Poll Steps (DBEXP-01..04)**: Export executable database polling steps in standalone `.feature` files utilizing self-contained inline JDBC functions (`java.sql.DriverManager`), supporting retry loops, expected row counts, custom SQL conditions, and downstream variable extraction without requiring proprietary platform classes on external classpaths — v1.0 Phase 1
- ✓ **HTTP Form URL-Encoded Requests (FORM-01..04)**: Full-stack `application/x-www-form-urlencoded` support across visual configuration drawer (table editor, variable injection, auto-sync headers), runtime execution engine (`form fields`), and standalone feature exporter — v1.0 Phase 2
- ✓ **Execution Navigation URLs (EXEC-01, EXEC-02, EXEC-03)**: Execution trigger (`POST /api/v1/workflows/{id}/execute`) and details (`GET /api/v1/executions/{id}`) responses provide absolute `statusUrl`, `reportUrl`, and `streamUrl` navigation URLs resolved against request origin / reverse-proxy headers, plus standard RFC 7231 `Location` header — v1.1 Phase 3
- ✓ **Synchronous Wait Execution Mode (SYNC-01, SYNC-02, SYNC-03)**: Workflow execution trigger endpoint supports `?wait=true` (with defensive `&timeoutSec=N` clamping), blocking on Java 25 Virtual Threads until execution completes and returning HTTP 200 with completed entity, final status, and populated `reportUrl`, or graceful HTTP 504/202 timeout continuation — v1.1 Phase 4
- ✓ **Dynamic Global Header Resolution (SUBWF-01)**: `DynamicKarateBridge.computeEffectiveHeaders` dynamically re-resolves global flow headers on every HTTP step using live execution context, allowing preceding steps or sub-workflows to satisfy global headers (e.g. `Authorization: Bearer ${authToken}`) — v1.1 Phase 5
- ✓ **Pre-Requisite Setup Workflow (SUBWF-02)**: Setup workflow model persisted in AST and `CanvasState`; `DynamicKarateRunner` prepends synthetic setup step at index 0 and promotes variables to parent context, configured via dedicated "Setup Workflow" tab in `WorkflowSettingsModal` — v1.1 Phase 5
- ✓ **Canvas Badges & Suggestion Chips (SUBWF-03)**: `SubWorkflowNode` renders amber Zap badges for exported variables; `NodeConfigDrawer` renders one-click Sparkles suggestion chips (`+ ${varName}`) across URL, header, and body inputs — v1.1 Phase 5
- ✓ **Standalone Feature Export Parity (SUBWF-04)**: Standalone feature export emits native Karate dynamic JavaScript header callback (`* configure headers = function() { ... }`) and inlines setup workflow steps for 100% execution parity — v1.1 Phase 5
- ✓ **Standalone Karate Sub-Workflow Feature Export**: Inlines nested sub-workflows recursively into a single self-contained Karate feature, preserving context binding, variable scope isolation, and parent variable extraction while enforcing a visited-workflow guard to prevent circular references
- ✓ **Universal Database Support & Standalone Karate CLI Runner**: Shaded standalone JAR (`meterflow-karate-standalone.jar`) bundling Karate Core 2.1.4, multi-database JDBC drivers (PostgreSQL, MySQL, SQL Server, Oracle, MariaDB, SQLite, H2), and dynamic `--driver-dir` JAR loading via `DbHelper` (`DbHelper.poll()`, `DbHelper.query()`, `DbHelper.executeUpdate()`)

### Out of Scope

- Multipart / form-data file uploads — separate milestone.
- External standalone JAR dependency requirements for DB tests — standalone exported Karate features remain runnable using only standard JDBC drivers and standard Karate CLI/Maven.

## Context

Shipped v1.1 milestone delivering execution navigation URLs, synchronous wait execution mode, and sub-workflow variable promotion with dynamic global flow headers.
- Codebase state: 75 backend tests passing in `SubWorkflowCompositionTest`, `WorkflowExecutionSyncModeTest`, `WorkflowExecutionNavigationUrlTest`, and `WorkflowFeaturesAndPortabilityTest`.
- Frontend: TypeScript 5.6.3, React 18, Vite (`tsc && vite build` passing with 0 errors).
- Zero technical debt or verification gaps remaining.

## Constraints

- **Compatibility**: Standalone `.feature` files must be valid Gherkin executable by official Karate Core 2.x CLI/Maven without needing orchestrator backend classes on the classpath.
- **Zero Breaking Changes**: Existing workflows with JSON payloads, canvas layouts, and AST structures must continue functioning without regression.
- **Security**: Database credentials and secrets in exported features must support environment variable interpolation (`${$env:DB_PASS}`).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Self-contained inline JDBC helper for exported DB steps | Eliminates custom classpath dependencies so exported `.feature` files run in any standard Karate setup with JDBC driver | ✓ Validated in Phase 1 |
| Key-value table editor with raw mode in UI drawer | Provides clean developer experience for form parameters while retaining variable insertion capabilities | ✓ Validated in Phase 2 |
| Native Karate `form fields` keyword in execution & export | Conforms to Karate's built-in URL-encoding behavior and automatic header handling | ✓ Validated in Phase 2 |
| Dynamic request-origin resolution via `ServletUriComponentsBuilder` | Produces absolute URLs reflecting caller scheme, host, and port without environment-specific hardcoded URLs | ✓ Validated in Phase 3 |
| Framework-managed proxy strategy `server.forward-headers-strategy: framework` | Standardizes reverse proxy URL adaptation via Spring's `ForwardedHeaderFilter` | ✓ Validated in Phase 3 |
| Non-blocking Virtual Thread carrier unmounting with `CompletableFuture` | Allows synchronous callers to await completion without holding or pinning OS carrier threads | ✓ Validated in Phase 4 |
| Defensive parameter clamping (1s-300s) on synchronous wait | Prevents connection starvation attacks while providing reliable timeouts | ✓ Validated in Phase 4 |
| Per-request dynamic header evaluation via `DynamicKarateBridge` | Allows tokens extracted during execution (e.g. from setup workflows) to reactively satisfy global flow headers | ✓ Validated in Phase 5 |
| Zero-migration AST storage in existing PostgreSQL `jsonb` columns for `setupWorkflow` | Preserves database schema stability while adding rich setup workflow capabilities | ✓ Validated in Phase 5 |
| Standalone feature export parity via native Karate JavaScript `configure headers = function() { ... }` callbacks | Preserves dynamic runtime behavior when running exported features in terminal or CI without the server | ✓ Validated in Phase 5 |
| Recursive inlining with cycle guards for sub-workflow export | Emits a single self-contained `.feature` file without external feature path dependencies while preventing infinite recursion | ✓ Validated |
| Zero-dependency `DbHelper` with `DriverManager.registerDriver` | Enables arbitrary external JDBC drivers via `--driver-dir` without requiring re-compilation or shading of uncommon drivers | ✓ Validated |
| Dedicated `standalone-karate` Maven Shade profile with transformer override | Avoids collision with Spring Boot starter parent resource transformer while producing an optimized 129MB all-in-one Karate CLI runnable anywhere | ✓ Validated |

---
*Last updated: 2026-11-10 after Milestone v1.1 closeout*
