# Requirements: Execution Status & Report URLs for cURL / CI/CD

**Defined:** 2026-11-07  
**Core Value:** Ensure complete functional parity and execution fidelity between visual workflows, dynamic in-memory Karate execution, and standalone exported Karate `.feature` files, while providing frictionless execution status tracking and direct HTML report access for automated pipelines and terminal users.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### API Execution Tracking & URL Navigation

- [x] **EXEC-01**: Execution trigger response (`POST /api/v1/workflows/{id}/execute`) and details response (`GET /api/v1/executions/{id}`) include absolute navigation URLs: `statusUrl`, `reportUrl`, and `streamUrl` computed from request host/origin.
- [x] **EXEC-02**: Workflow execution trigger (`POST /api/v1/workflows/{id}/execute`) sets standard HTTP `Location` response header pointing to the absolute `statusUrl`.
- [x] **EXEC-03**: Workflow execution details endpoint (`GET /api/v1/executions/{id}`) provides the generated `reportUrl` immediately upon completion or failure.

### Synchronous Execution Mode

- [x] **SYNC-01**: Execution trigger endpoint supports a `?wait=true` query parameter with configurable `timeoutSec` (default 60s) to block until execution completes on the virtual thread.
- [x] **SYNC-02**: When `?wait=true` completes within timeout, the API returns HTTP 200 containing the completed execution entity, final `ExecutionStatus` (`COMPLETED` or `FAILED`), and generated `reportUrl`.
- [x] **SYNC-03**: When `?wait=true` exceeds `timeoutSec`, the API responds gracefully with HTTP 504 Gateway Timeout or 202 status indicating background continuation.

### Sub-Workflow Chaining & Global Variable Promotion

- [x] **SUBWF-01**: Dynamic Global Header Variable Resolution — `DynamicKarateBridge.computeEffectiveHeaders` dynamically resolves `${variableName}` expressions from `globalContext` on every HTTP step, so variables extracted by preceding steps (or sub-workflows) automatically populate global default headers (e.g. `Authorization: Bearer ${authToken}`).
- [x] **SUBWF-02**: Pre-requisite / Setup Workflow in Global Workflow Settings — `WorkflowSettingsModal` and AST schema support configuring an optional pre-requisite workflow that executes prior to the canvas steps, binding its output variables directly into `globalContext`.
- [x] **SUBWF-03**: Canvas Sub-Workflow Quick-Add & Variable Badges — Visual canvas displays variable export chips on `SubWorkflowNode` indicating exported variables, with autocomplete support in the configuration drawer.
- [x] **SUBWF-04**: Standalone Feature Export Parity — `TestWorkflowService.generateStandaloneKarateFeature` generates dynamic Karate header evaluation functions (`configure headers = function() { ... }`) that read from `context`, preserving 100% execution parity in offline / CI terminal runs.

## v2 Requirements

Deferred to future releases. Tracked but not in current roadmap.

### CI/CD Webhook & cURL Modal (Deferred)

- **CURL-01**: `CiCdWebhookModal` displays the direct Karate HTML report URL format and live status checking cURL command alongside the trigger snippet.
- **CURL-02**: `CiCdWebhookModal` provides toggleable choices between "Asynchronous Trigger (HTTP 202)" and "Synchronous CI/CD Pipeline (HTTP 200 with report link)".
- **CURL-03**: `CiCdWebhookModal` provides a copyable bash script snippet for CI/CD pipelines that triggers execution, verifies exit status, and outputs the direct HTML report URL.

### Advanced CI/CD Integration

- **CURL-V2-01**: Provide downloadable GitHub Actions workflow action (`.github/workflows/e2e-run.yml`) template.
- **CURL-V2-02**: Support JUnit XML / Karate JSON report download endpoint (`/api/v1/executions/{id}/report/karate-summary.json`) for CI test result parsers.

## Out of Scope

Explicitly excluded to maintain stability and prevent scope creep.

| Feature | Reason |
|---------|--------|
| Custom External Java Class Dependencies | Standalone exported Karate features remain runnable using only standard JDBC drivers and standard Karate CLI/Maven |
| Multipart file uploads | Distinct MIME protocol handling; deferred to future release |
| Native CLI binary | cURL and standard HTTP clients are sufficient without building a bespoke binary CLI |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| EXEC-01 | Phase 3 | Complete |
| EXEC-02 | Phase 3 | Complete |
| EXEC-03 | Phase 3 | Complete |
| SYNC-01 | Phase 4 | Complete |
| SYNC-02 | Phase 4 | Complete |
| SYNC-03 | Phase 4 | Complete |
| SUBWF-01 | Phase 5 | Complete |
| SUBWF-02 | Phase 5 | Complete |
| SUBWF-03 | Phase 5 | Complete |
| SUBWF-04 | Phase 5 | Complete |

**Coverage:**
- v1 requirements: 10 total
- Mapped to phases: 10
- Unmapped: 0 ✓

---
*Requirements defined: 2026-11-07*  
*Last updated: 2026-11-09 for v1.1 milestone (Phase 5 scope updated to Sub-Workflow Chaining & Variable Promotion)*
