# Roadmap: E2E Test Workflow Orchestrator

## Milestones

- ✅ **v1.0 Standalone DB Export & Form URL-Encoded Requests** — Phases 1-2 (shipped 2026-11-07)
- 🚧 **v1.1 Execution Status & Report URLs for cURL / CI/CD** — Phases 3-5 (in progress)

## Phases

<details>
<summary>✅ v1.0 Standalone DB Export & Form URL-Encoded Requests (Phases 1-2) — SHIPPED 2026-11-07</summary>

- [x] Phase 1: Database Polling Standalone Karate Export (2/2 plans) — completed 2026-11-07
- [x] Phase 2: Form URL-Encoded Requests End-to-End (2/2 plans) — completed 2026-11-07

</details>

- [x] **Phase 3: Execution Tracking Navigation URLs** - Enrich execution trigger and details responses with absolute navigation URLs (statusUrl, reportUrl, streamUrl) and standard HTTP Location header
- [x] **Phase 4: Synchronous Wait Execution Mode** - Support ?wait=true and timeoutSec parameters on execution trigger for blocking execution in CI/CD pipelines (completed 2026-11-08)
- [x] **Phase 5: Sub-Workflow Variable Promotion & Global Flow Headers** - Enable workflows to include previous/sub-workflows as setup or sequential steps, automatically promoting extracted variables into global workflow settings and headers across both dynamic execution and standalone export (completed 2026-11-09)

## Phase Details

### Phase 3: Execution Tracking Navigation URLs
**Goal**: Callers triggering or inspecting workflow executions receive absolute navigation URLs (`statusUrl`, `reportUrl`, `streamUrl`) and an HTTP `Location` header to track execution progress and access reports
**Depends on**: Phase 2
**Requirements**: EXEC-01, EXEC-02, EXEC-03
**Success Criteria** (what must be TRUE):
  1. Triggering workflow execution via `POST /api/v1/workflows/{id}/execute` returns an HTTP `Location` response header and a response payload containing absolute `statusUrl`, `reportUrl`, and `streamUrl` matching the request scheme and host
  2. Querying workflow execution details via `GET /api/v1/executions/{id}` returns absolute `statusUrl`, `streamUrl`, and `reportUrl` navigation properties
  3. When workflow execution completes or fails, `GET /api/v1/executions/{id}` provides the populated `reportUrl` pointing directly to the generated Karate HTML report endpoint
**Plans**: 1/1 plans complete

Plans:
- [x] 03-01-PLAN.md — Enrich execution trigger and details responses with absolute navigation URLs (statusUrl, reportUrl, streamUrl), HTTP Location header, and reverse proxy support

### Phase 4: Synchronous Wait Execution Mode
**Goal**: CI/CD pipelines and automated scripts can trigger workflow execution synchronously via `?wait=true`, blocking until execution completes to obtain final results and report URLs in a single request
**Depends on**: Phase 3
**Requirements**: SYNC-01, SYNC-02, SYNC-03
**Success Criteria** (what must be TRUE):
  1. Calling `POST /api/v1/workflows/{id}/execute?wait=true` blocks the HTTP connection on the backend virtual thread until workflow execution finishes
  2. When execution completes within timeout, the API returns HTTP 200 with the completed execution entity, final `ExecutionStatus` (`COMPLETED` or `FAILED`), and generated `reportUrl`
  3. When execution exceeds `timeoutSec` (or the default 60s), the API responds gracefully with HTTP 504 Gateway Timeout or 202 status indicating background continuation without failing the running execution
**Plans**: 1/1 plans complete

Plans:
- [x] 04-01-PLAN.md — Support ?wait=true and timeoutSec parameters on execution trigger for blocking execution in CI/CD pipelines

### Phase 5: Sub-Workflow Variable Promotion & Global Flow Headers
**Goal**: Enable workflows to include previous/sub-workflows as setup or sequential steps, automatically promoting extracted variables into global workflow settings and headers across both dynamic execution and standalone export
**Depends on**: Phase 4
**Requirements**: SUBWF-01, SUBWF-02, SUBWF-03, SUBWF-04
**Success Criteria** (what must be TRUE):
  1. Extracted variables from preceding steps or sub-workflows (e.g. `authToken`) dynamically resolve into global default headers (`Authorization: Bearer ${authToken}`) on all downstream HTTP steps
  2. Users can configure an optional Pre-requisite / Setup Workflow in `WorkflowSettingsModal` that runs before canvas steps and populates global context
  3. Canvas `SubWorkflowNode` displays visual badges for exported variables and integrates with configuration drawer autocomplete
  4. Standalone Karate feature export (`/export/feature`) generates dynamic Karate header functions reflecting runtime context variables with 100% execution parity
**Plans**: 2/2 plans complete
**UI hint**: yes

Plans:
**Wave 1**
- [x] 05-01-PLAN.md — Backend Dynamic Header Promotion, Setup Workflow Execution & Standalone Export Parity

**Wave 2**
- [x] 05-02-PLAN.md — Frontend Setup Workflow Settings, Canvas Badges & Upstream Variable Autocomplete

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Database Polling Standalone Karate Export | v1.0 | 2/2 | Complete | 2026-11-07 |
| 2. Form URL-Encoded Requests End-to-End | v1.0 | 2/2 | Complete | 2026-11-07 |
| 3. Execution Tracking Navigation URLs | v1.1 | 1/1 | Complete | 2026-11-08 |
| 4. Synchronous Wait Execution Mode | v1.1 | 1/1 | Complete    | 2026-11-08 |
| 5. Sub-Workflow Variable Promotion & Global Flow Headers | v1.1 | 2/2 | Complete | 2026-11-09 |
