---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
# Codebase Concerns

**Analysis Date:** 2026-11-06

## Tech Debt

**Monolithic Frontend Configuration Drawer:**
- Issue: All node types (HTTP, DB, Kafka, Delay, Transform, SubWorkflow) configuration logic, assertion editors, variable extraction tables, and parameter state are managed in a single 1,813-line component file.
- Files: `frontend/src/components/drawer/NodeConfigDrawer.tsx`
- Impact: High cognitive load when maintaining or adding new step types, risk of unintended state bleed across step types, and slower React re-renders when typing in form inputs.
- Fix approach: Refactor `NodeConfigDrawer.tsx` into modular sub-components per node type (`HttpStepConfigPanel.tsx`, `DbPollConfigPanel.tsx`, etc.) with a shared assertion/variable extraction sub-form.

**Static In-Memory Bridge State:**
- Issue: `DynamicKarateBridge` stores active execution state, step configurations, and payloads in static `ConcurrentHashMap` instances.
- Files: `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`
- Impact: If an unhandled thread exception terminates execution before the finally block cleans up, stale payloads and step result maps may linger in memory.
- Fix approach: Implement an explicit time-based expiration cache (e.g., Caffeine cache or TTL eviction) or an execution lifecycle listener that guarantees cleanup on failure or termination.

**Complete Absence of Frontend Automated Testing:**
- Issue: The frontend repository has no test runner (no Vitest or Jest), no component testing libraries, and zero unit/E2E test files.
- Files: `frontend/package.json`
- Impact: Regressions in visual canvas interactions, topological auto-alignment, edge deletions, and form bindings can only be caught through manual verification.
- Fix approach: Install Vitest, React Testing Library, and jsdom in `frontend/package.json`; create unit test suites for `layoutEngine.ts` and node rendering components.

## Known Bugs

**Cancellation Latency During Long Synchronous Step Calls:**
- Symptoms: When a user calls `POST /api/v1/executions/{id}/cancel`, status updates to `CANCELLED`, but an in-flight HTTP request or external DB connection might not terminate immediately until its configured connection/read timeout expires.
- Files: `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`, `backend/src/main/java/com/orchestrator/platform/service/execution/DbPollExecutor.java`
- Trigger: Cancelling an execution while a step is executing an external HTTP request with a high timeout (e.g. 30 seconds) or waiting on an unresponsive database socket.
- Workaround: Configure realistic step-level timeouts (`timeoutMs`) on HTTP and DB poll steps.

## Security Considerations

**Unencrypted In-Database Secrets Storage:**
- Risk: Environment secrets (`envSecrets`) and workflow-level secrets are stored in plaintext JSONB columns within PostgreSQL `test_workflows.workflow_ast`.
- Files: `backend/src/main/resources/db/migration/V1__init_orchestrator_schema.sql`, `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java`
- Current mitigation: Secrets are redacted in real-time execution logs (`ExecutionLogBroadcaster.java`), and database access is restricted by environment credentials.
- Recommendations: Implement application-level envelope encryption (AES-GCM) or integrate with HashiCorp Vault / AWS Secrets Manager before writing secrets to the database.

**JavaScript Transform Node Execution Sandbox:**
- Risk: `execute-transform.feature` allows execution of arbitrary user-defined JavaScript scripts for context transformations.
- Files: `backend/src/main/resources/karate/execute-transform.feature`, `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`
- Current mitigation: Karate's JavaScript engine runs within Karate's scenario scope with limited Java interop privileges.
- Recommendations: Constrain script execution timeouts and verify script inputs to prevent infinite loops or excessive memory consumption in multi-tenant environments.

## Performance Bottlenecks

**Karate HTML Report Disk Accumulation:**
- Problem: Every test execution generates a standalone directory containing HTML, CSS, JS, step screenshots, and captured payloads in `target/karate-reports/` or `/app/reports/`.
- Files: `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java`, `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java`
- Cause: Karate generates self-contained static report assets for each run to allow offline browsing.
- Improvement path: Ensure `ORCHESTRATOR_RETENTION_AUTO_PRUNE_ENABLED` remains active (keeps latest $N$ runs per workflow); use the `/api/v1/executions/purge` endpoint periodically in high-throughput environments.

## Fragile Areas

**Karate Java Interop Signature Coupling:**
- Files: `backend/src/main/resources/karate/generic-flow-executor.feature`, `backend/src/main/resources/karate/execute-step.feature`, `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`
- Why fragile: Karate feature files call static Java methods dynamically by name (e.g., `DynamicKarateBridge.getStepConfig(executionId, stepIndex)`). Renaming Java methods or changing parameter types will silently fail Karate scenario execution at runtime.
- Safe modification: Treat public static methods in `DynamicKarateBridge` as strict binary interfaces; accompany any modification with tests in `DynamicKarateRunnerTest.java`.
- Test coverage: Covered by `DynamicKarateRunnerTest.java` and `SubWorkflowCompositionTest.java`.

## Scaling Limits

**Virtual Thread Concurrency vs. Database Connection Pools:**
- Current capacity: Java 25 Virtual Threads can spawn thousands of concurrent execution tasks.
- Limit: External databases and PostgreSQL connection pools (`maximum-pool-size: 20` in `application.yml`) will hit connection exhaustion if hundreds of workflows execute simultaneously with database polling steps.
- Scaling path: Introduce an execution queue limiter or semaphore in `TestWorkflowExecutionService` to bound concurrent database-polling workflow executions to match connection pool headroom.

## Dependencies at Risk

**Karate Core 2.1.4 Upgrade Stability:**
- Risk: Karate 2.x introduces architectural changes from Karate 1.x (such as runner APIs and internal report paths).
- Impact: Upgrading minor versions of Karate could alter internal feature evaluation behavior or report generation directories.
- Migration plan: Pin `karate.version` to `2.1.4` in `backend/pom.xml` and validate all 10 integration suites before any version bumps.

## Missing Critical Features

**Canvas Undo / Redo Mechanism:**
- Problem: Canvas node movements, deletions, or edge disconnections cannot be undone via `Ctrl+Z` / `Cmd+Z`.
- Blocks: Accidental node or edge deletions require manually re-adding the node or re-importing the workflow JSON.

**Role-Based Access Control (RBAC):**
- Problem: No built-in authentication or user management on the backend REST API; all endpoints are publicly accessible within the local network or container perimeter.
- Blocks: Multi-team tenancy or isolated workspace environments.

## Test Coverage Gaps

**Frontend Canvas & Interaction Testing:**
- What's not tested: React Flow node dragging, Kahn layout engine execution, configuration drawer inputs, modal opening/closing.
- Files: `frontend/src/components/canvas/FlowCanvas.tsx`, `frontend/src/utils/layoutEngine.ts`, `frontend/src/components/drawer/NodeConfigDrawer.tsx`
- Risk: UI regressions during React or dependency updates.
- Priority: High

**Kafka Step Failure / Reconnection Scenarios:**
- What's not tested: Kafka broker unreachable, timeout expiration during consumer polling, and invalid SSL authentication.
- Files: `backend/src/main/java/com/orchestrator/platform/service/execution/KafkaAssertExecutor.java`
- Risk: Silent hangs or unhandled broker disconnection errors during live Kafka assertions.
- Priority: Medium

---

*Concerns audit: 2026-11-06*
