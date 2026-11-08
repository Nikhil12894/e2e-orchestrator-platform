# E2E Test Workflow Orchestrator - Execution Status & Report URLs for cURL / CI/CD

## What This Is

The End-to-End Test Workflow Orchestrator is a full-stack developer platform for visually authoring, executing, and monitoring automated API, database, and event-driven test workflows. This milestone introduces comprehensive execution tracking, status monitoring, and Karate HTML report discovery for workflows triggered via cURL and external CI/CD pipelines.

## Core Value

Ensure complete functional parity and execution fidelity between visual workflows, dynamic in-memory Karate execution, and standalone exported Karate `.feature` files, while providing frictionless execution status tracking and direct HTML report access for automated pipelines and terminal users.

## Current Milestone: v1.1 Execution Status & Report URLs for cURL / CI/CD

**Goal:** Provide direct execution tracking, status monitoring, and Karate HTML report URLs when triggering workflows via cURL and CI/CD pipelines, supporting both asynchronous tracking and synchronous wait execution modes.

**Target features:**
- Execution Response Navigation URLs: Include absolute `statusUrl`, `reportUrl`, and `streamUrl` properties in `POST /api/v1/workflows/{id}/execute` and `GET /api/v1/executions/{id}`, plus an HTTP `Location` header.
- Synchronous Execution Parameter: Support `?wait=true` (with optional `&timeoutSec=N`) on the execution trigger endpoint so CI/CD callers can block until completion and receive final status and report link directly in the response.
- CI/CD Webhook Modal Enhancements: Update the visual modal to display enriched response structures, status polling cURL snippets, and synchronous pipeline commands.

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
- ✓ **Karate Feature Export for DB Poll Steps**: Export executable database polling steps in standalone `.feature` files utilizing self-contained inline JDBC functions (`java.sql.DriverManager`), supporting retry loops, expected row counts, custom SQL conditions, and downstream variable extraction without requiring proprietary platform classes on external classpaths — v1.0 Phase 1
- ✓ **HTTP Form URL-Encoded Configuration Drawer**: Add a Request Body Type toggle (`JSON` vs `x-www-form-urlencoded`) in the visual node drawer when method is `POST`, `PUT`, or `PATCH`, with an interactive key-value table editor, variable injection, and automatic `Content-Type: application/x-www-form-urlencoded` header synchronization — v1.0 Phase 2
- ✓ **HTTP Form URL-Encoded AST & Execution**: Enhance `HttpRequestStepConfig` with body type support and update `execute-http.feature` to natively invoke Karate's `form fields` keyword instead of `request` when sending form-encoded requests — v1.0 Phase 2
- ✓ **HTTP Form URL-Encoded Standalone Export**: Update `TestWorkflowService.generateStandaloneKarateFeature()` to emit native Karate `form fields` syntax when exporting form-encoded HTTP steps — v1.0 Phase 2
- ✓ **Execution Navigation URLs (EXEC-01)**: Execution trigger (`POST /api/v1/workflows/{id}/execute`) and details (`GET /api/v1/executions/{id}`) responses provide absolute `statusUrl`, `reportUrl`, and `streamUrl` navigation URLs resolved against request origin / reverse-proxy headers — Phase 3
- ✓ **Execution Location Header (EXEC-02)**: Execution trigger endpoint sets standard RFC 7231 `Location` response header matching the absolute `statusUrl` — Phase 3
- ✓ **Synchronous Wait Execution Mode (EXEC-03)**: Workflow execution trigger endpoint supports `?wait=true` (with defensive `&timeoutSec=N` clamping), blocking on Java 25 Virtual Threads until execution completes and returning HTTP 200 with completed entity, final status, and populated `reportUrl`, or graceful HTTP 504/202 timeout continuation — Phase 4

### Active

- [ ] **EXEC-04**: `CiCdWebhookModal` provides clear cURL commands for async execution with status check, synchronous wait mode, and direct Karate HTML report link template.

### Out of Scope

- Multipart / form-data file uploads — separate milestone; this milestone focuses on execution URLs and status tracking.
- External standalone JAR dependency requirements for DB tests — standalone exported Karate features remain runnable using only standard JDBC drivers and standard Karate CLI/Maven.

## Context

Shipped v1.0 milestone delivering standalone Karate database polling export and full-stack `application/x-www-form-urlencoded` HTTP request support.
- Codebase state: 7 key files modified across backend and frontend (+1,123 / -40 lines).
- Backend: Java 25 LTS, Spring Boot 4.1.2, Karate Core 2.1.4, HikariCP, PostgreSQL. `WorkflowFeaturesAndPortabilityTest` has 16/16 tests passing.
- Frontend: React 18, TypeScript 5.6.3, @xyflow/react, Tailwind CSS, Vite (`tsc && vite build` passing with 0 errors).
- Zero technical debt or verification gaps remaining. Future enhancements (multipart/form-data, raw query string editor, multi-table DB assertions) tracked for subsequent milestones.

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
*Last updated: 2026-11-08 after Phase 04*
