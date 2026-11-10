# Milestones

## v1.1 Execution Status & Report URLs for cURL / CI/CD (Shipped: 2026-11-10)

**Phases completed:** 3 phases (Phases 3-5), 4 plans, 11 tasks

**Key accomplishments:**
- **Execution Tracking Navigation URLs (Phase 3):** Execution trigger (`POST /api/v1/workflows/{id}/execute`) and details endpoints return RFC 7231 `Location` header and absolute `statusUrl`, `reportUrl`, and `streamUrl` navigation URLs dynamically adapting to reverse proxy origin headers (`X-Forwarded-Host`, `X-Forwarded-Proto`).
- **Synchronous Wait Execution Mode (Phase 4):** Execution trigger supports `?wait=true` with configurable, defensively clamped `timeoutSec` (1s-300s). Execution blocks via non-blocking virtual thread carrier-unmounting with `CompletableFuture.get()`, returning HTTP 200 upon completion/failure with final execution status and accessible HTML report URLs, and HTTP 504/202 upon timeout with uninterrupted background continuation.
- **Dynamic Global Header Variable Resolution (Phase 5):** Global flow headers dynamically evaluate `${variableName}` expressions from `globalContext` on every HTTP request. Variables extracted by preceding steps or sub-workflows (such as `authToken`) automatically populate global headers (`Authorization: Bearer ${authToken}`).
- **Pre-Requisite Setup Workflow (Phase 5):** Pre-requisite setup workflow schema and runtime execution synthesis prepending a synthesized setup step at index 0, with dedicated "Setup Workflow" tab in `WorkflowSettingsModal` featuring workflow selection, variable promotion table, and Bearer auto-bind quick action.
- **Canvas Badges & Drawer Autocomplete (Phase 5):** Visual variable export badges on `SubWorkflowNode` and upstream variable suggestion chips (`+ ${varName}`) across URL, header, and body inputs in `NodeConfigDrawer`.
- **Standalone Feature Export Parity (Phase 5):** Native Karate dynamic JavaScript header callback (`* configure headers = function() { ... }`) and setup workflow step inlining for 100% offline terminal execution parity.

---

## v1.0 Standalone DB Export & Form URL-Encoded Requests (Shipped: 2026-11-07)

**Phases completed:** 2 phases, 4 plans, 4 tasks

**Key accomplishments:**
- Standalone Karate feature exporter for database polling workflow steps, emitting self-contained inline JDBC functions (`dbQuery` and `dbPoll`) in the feature `Background:`, retry loops with configurable timing, compound condition evaluation safeguards, step-level column match assertions, and downstream variable extraction.
- Standalone Karate feature execution verification running generated database polling features directly through `io.karatelabs.core.Runner.path` against in-memory H2 database, proving downstream variable chaining into subsequent HTTP requests, muted step handling, bounded timeout termination, and zero platform classpath dependencies.
- Implemented backend AST model extension (bodyType, formData), dynamic Karate execution with native form fields keyword, mock /api/v1/mock/auth/token endpoint, and standalone Karate .feature export formatting dynamic variable expressions.
- Implemented frontend TypeScript data models (bodyType, formData), segmented Request Body Format selector, interactive key-value table editor with ${var} selector insertion, duplicate key inline warnings, and header auto-synchronization in NodeConfigDrawer.tsx.

---
