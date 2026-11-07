# E2E Test Workflow Orchestrator - Standalone DB Export & Form URL-Encoded Requests

## What This Is

The End-to-End Test Workflow Orchestrator is a full-stack developer platform for visually authoring, executing, and monitoring automated API, database, and event-driven test workflows. This milestone adds comprehensive standalone Karate `.feature` export for database polling steps and introduces first-class support for `application/x-www-form-urlencoded` HTTP requests in the visual editor, execution runtime, and export pipelines.

## Core Value

Ensure complete functional parity and execution fidelity between visual workflows, dynamic in-memory Karate execution, and standalone exported Karate `.feature` files for all step types including database polling and form-parameter HTTP requests.

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
- ✓ **Karate Feature Export for DB Poll Steps**: Export executable database polling steps in standalone `.feature` files utilizing self-contained inline JDBC functions (`java.sql.DriverManager`), supporting retry loops, expected row counts, custom SQL conditions, and downstream variable extraction without requiring proprietary platform classes on external classpaths — Phase 1
- ✓ **HTTP Form URL-Encoded Configuration Drawer**: Add a Request Body Type toggle (`JSON` vs `x-www-form-urlencoded`) in the visual node drawer when method is `POST`, `PUT`, or `PATCH`, with an interactive key-value table editor, variable injection, and automatic `Content-Type: application/x-www-form-urlencoded` header synchronization — Phase 2
- ✓ **HTTP Form URL-Encoded AST & Execution**: Enhance `HttpRequestStepConfig` with body type support and update `execute-http.feature` to natively invoke Karate's `form fields` keyword instead of `request` when sending form-encoded requests — Phase 2
- ✓ **HTTP Form URL-Encoded Standalone Export**: Update `TestWorkflowService.generateStandaloneKarateFeature()` to emit native Karate `form fields` syntax when exporting form-encoded HTTP steps — Phase 2

### Active

*(All milestone requirements complete)*

### Out of Scope

- Multipart / form-data file uploads — separate milestone; this milestone focuses on `application/x-www-form-urlencoded`.
- External standalone JAR dependency requirements for DB tests — standalone exported Karate features must remain runnable using only standard JDBC drivers and standard Karate CLI/Maven.

## Context

- `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java`: Currently emits `# DB Polling Step: <stepName>` as a comment line during standalone Karate export.
- `backend/src/main/resources/karate/execute-http.feature`: Currently issues `* request reqBody` unconditionally, which fails or misformats `application/x-www-form-urlencoded` payloads that require Karate's native `* form fields` or `* form field`.
- `frontend/src/components/drawer/NodeConfigDrawer.tsx`: Only supports a JSON textarea for request bodies.

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

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-11-07 after Phase 2 (Milestone Complete)*
