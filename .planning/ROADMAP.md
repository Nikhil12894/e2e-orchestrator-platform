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

- [ ] **Phase 3: Execution Tracking Navigation URLs** - Enrich execution trigger and details responses with absolute navigation URLs (statusUrl, reportUrl, streamUrl) and standard HTTP Location header
- [ ] **Phase 4: Synchronous Wait Execution Mode** - Support ?wait=true and timeoutSec parameters on execution trigger for blocking execution in CI/CD pipelines
- [ ] **Phase 5: CI/CD Webhook Modal & Pipeline Snippets** - Update visual modal with async/sync mode toggles, status polling cURL snippets, and automated bash script

## Phase Details

### Phase 3: Execution Tracking Navigation URLs
**Goal**: Callers triggering or inspecting workflow executions receive absolute navigation URLs (`statusUrl`, `reportUrl`, `streamUrl`) and an HTTP `Location` header to track execution progress and access reports
**Depends on**: Phase 2
**Requirements**: EXEC-01, EXEC-02, EXEC-03
**Success Criteria** (what must be TRUE):
  1. Triggering workflow execution via `POST /api/v1/workflows/{id}/execute` returns an HTTP `Location` response header and a response payload containing absolute `statusUrl`, `reportUrl`, and `streamUrl` matching the request scheme and host
  2. Querying workflow execution details via `GET /api/v1/executions/{id}` returns absolute `statusUrl`, `streamUrl`, and `reportUrl` navigation properties
  3. When workflow execution completes or fails, `GET /api/v1/executions/{id}` provides the populated `reportUrl` pointing directly to the generated Karate HTML report endpoint
**Plans**: 1 plan

Plans:
- [ ] 03-01-PLAN.md — Enrich execution trigger and details responses with absolute navigation URLs (statusUrl, reportUrl, streamUrl), HTTP Location header, and reverse proxy support

### Phase 4: Synchronous Wait Execution Mode
**Goal**: CI/CD pipelines and automated scripts can trigger workflow execution synchronously via `?wait=true`, blocking until execution completes to obtain final results and report URLs in a single request
**Depends on**: Phase 3
**Requirements**: SYNC-01, SYNC-02, SYNC-03
**Success Criteria** (what must be TRUE):
  1. Calling `POST /api/v1/workflows/{id}/execute?wait=true` blocks the HTTP connection on the backend virtual thread until workflow execution finishes
  2. When execution completes within timeout, the API returns HTTP 200 with the completed execution entity, final `ExecutionStatus` (`COMPLETED` or `FAILED`), and generated `reportUrl`
  3. When execution exceeds `timeoutSec` (or the default 60s), the API responds gracefully with HTTP 504 Gateway Timeout or 202 status indicating background continuation without failing the running execution
**Plans**: TBD

### Phase 5: CI/CD Webhook Modal & Pipeline Snippets
**Goal**: Developers authoring automated pipelines can view, configure, and copy ready-to-run cURL commands and CI/CD bash scripts directly from the web UI
**Depends on**: Phase 4
**Requirements**: CURL-01, CURL-02, CURL-03
**Success Criteria** (what must be TRUE):
  1. User can open `CiCdWebhookModal` and toggle between "Asynchronous Trigger (HTTP 202)" and "Synchronous CI/CD Pipeline (HTTP 200 with report link)" execution modes
  2. User can view and copy updated cURL commands showing status polling and direct Karate HTML report URL formats alongside the trigger command
  3. User can copy an end-to-end CI/CD bash script that triggers execution, verifies exit status, and outputs the direct HTML report URL
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Database Polling Standalone Karate Export | v1.0 | 2/2 | Complete | 2026-11-07 |
| 2. Form URL-Encoded Requests End-to-End | v1.0 | 2/2 | Complete | 2026-11-07 |
| 3. Execution Tracking Navigation URLs | v1.1 | 0/1 | Ready to execute | - |
| 4. Synchronous Wait Execution Mode | v1.1 | 0/0 | Not started | - |
| 5. CI/CD Webhook Modal & Pipeline Snippets | v1.1 | 0/0 | Not started | - |
