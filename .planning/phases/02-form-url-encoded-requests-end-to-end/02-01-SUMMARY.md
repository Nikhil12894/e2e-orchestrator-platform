---
phase: 02-form-url-encoded-requests-end-to-end
plan: 01
subsystem: backend-execution-export
tags: [karate, http, ast, form-urlencoded, exporter, mock-api]
requires: []
provides:
  - "HttpRequestStepConfig AST support for bodyType, formData, and isFormEncoded()"
  - "MockTargetApiController /api/v1/mock/auth/token endpoint consuming application/x-www-form-urlencoded"
  - "execute-http.feature native form fields execution without raw JSON body passing"
  - "TestWorkflowService deriveAstFromCanvas and generateStandaloneKarateFeature form fields emission"
affects:
  - "backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java"
  - "backend/src/main/resources/karate/execute-http.feature"
  - "backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java"
  - "backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java"
  - "backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java"
tech-stack:
  added: []
  patterns:
    - "Native Karate form fields keyword execution in runtime and standalone export"
    - "Dynamic variable expression formatting using formatKarateExpression in exported form parameters"
key-files:
  created: []
  modified:
    - "backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java"
    - "backend/src/main/resources/karate/execute-http.feature"
    - "backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java"
    - "backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java"
    - "backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java"
decisions:
  - "Annotate HttpRequestStepConfig with @JsonIgnoreProperties(ignoreUnknown = true) and @JsonIgnore on isFormEncoded() to avoid persisting pseudo-property to JSONB"
  - "Use Karate native form fields and request keyword semantics in execute-http.feature ensuring formPayload populates MultiPartBuilder without raw JSON body conflict"
metrics:
  duration: 4 min
  completed: "2026-11-07"
status: complete
actuals:
  tokens: 4091
  tasks: 2
  commits: 2
  plan_head_before: "c7685395f1a6eed9256d2a946a896d3dcf00dbee"
  plan_head_after: "40d1086fb67b9ae04e27b9634d17dd27728b88b6"
---

# Phase 02 Plan 01: Wave 1 Backend AST, Karate Runtime & Standalone Exporter Summary

Form URL-encoded HTTP request execution in dynamic Karate runtime and portable standalone feature export using native `form fields`.

## One-Liner
Implemented backend AST model extension (`bodyType`, `formData`), dynamic Karate execution with native `form fields` keyword, mock `/api/v1/mock/auth/token` endpoint, and standalone Karate `.feature` export formatting dynamic variable expressions.

## What Was Done

### Task 1: In-Memory Dynamic Karate Execution of Form URL-Encoded Requests (Tracer)
- Extended `HttpRequestStepConfig.java` with `bodyType` ("JSON" | "FORM_URL_ENCODED"), `formData` (`Map<String, Object>`), and `@JsonIgnore public boolean isFormEncoded()` helper method.
- Added `@PostMapping(value = "/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)` endpoint to `MockTargetApiController.java` parsing `@RequestParam Map<String, String> formParams` and returning an OAuth2-style token payload.
- Enhanced `execute-http.feature` to branch on `isForm`: invoking native Karate `form fields formPayload` when form-encoded and skipping raw JSON `request` body, passing effective request payload to `DynamicKarateBridge.onStepFinished`.
- Created `testDynamicKarateExecutionFormUrlEncoded` in `WorkflowFeaturesAndPortabilityTest.java`, proving in-memory dynamic execution succeeds with HTTP 200.
- Executed Task 1 tracer feedback gate with automated re-verification passing cleanly.
- **Commit:** `ed189e0`

### Task 2: Standalone Karate Feature Exporter & Portable Execution
- Updated `deriveAstFromCanvas` in `TestWorkflowService.java` to extract `bodyType` and `formData` (handling both Maps and key-value entry lists) from canvas node data.
- Updated `generateStandaloneKarateFeature` in `TestWorkflowService.java` to check `cfg.isFormEncoded()`, emitting `And form fields <expression>` when true and omitting `And request`.
- Implemented `formatFormFieldsExpression` in `TestWorkflowService.java` converting `${var}` and `${$env:VAR}` references into JavaScript expression concatenations via `formatKarateExpression` (mitigating T-02-01).
- Added `testStandaloneKarateFeatureExportFormUrlEncoded` asserting exported Gherkin contains `And form fields` and no `And request`.
- Added `testStandaloneKarateFeatureExecutionFormUrlEncoded` writing the exported feature to a temporary file and running it through `Runner.path(...).parallel(1)` with 0 failures against the mock endpoint.
- Executed full test suite with all 16 tests passing in `WorkflowFeaturesAndPortabilityTest`.
- **Commit:** `40d1086`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Jackson serialization of `isFormEncoded()` as `formEncoded` pseudo-property**
- **Found during:** Task 1 regression check on `testStandaloneKarateFeatureExport`
- **Issue:** Jackson treated `boolean isFormEncoded()` as a getter for `formEncoded`, serializing `"formEncoded": false` into `WorkflowAst` JSONB column. During entity retrieval, Jackson threw `UnrecognizedPropertyException: Unrecognized field "formEncoded"`.
- **Fix:** Added `@JsonIgnore` to `isFormEncoded()` and `@JsonIgnoreProperties(ignoreUnknown = true)` to `HttpRequestStepConfig`.
- **Files modified:** `backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java`
- **Commit:** `ed189e0`

**2. [Rule 1 - Bug] Karate 2.x JS parser syntax error on multi-token keyword in `* if` statement**
- **Found during:** Task 1 initial verify
- **Issue:** Karate 2.x parses lines starting with `* if (expr)` using the JavaScript parser rather than Gherkin keyword parser; `* if (isForm) form fields formPayload` failed with GraalJS syntax error `expected: [SEMI]`.
- **Fix:** Structured the Karate steps as `* form fields formPayload` (which evaluates to a no-op when `formPayload` is `null`) and `* request reqBody` (which sets body to null when `reqBody` is `null`, allowing `multiPart` form fields to format the request body).
- **Files modified:** `backend/src/main/resources/karate/execute-http.feature`
- **Commit:** `ed189e0`

## Known Stubs
None. All data structures, execution pipelines, and export formatters are fully implemented and integrated.

## Threat Flags
None. Security mitigations T-02-01 (formatting environment and secret variables as runtime lookups instead of hardcoding) and T-02-02 (using native Karate `form fields` with RFC 3986 percent-encoding) were verified in integration tests.

## Self-Check: PASSED
- FOUND: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java`
- FOUND: `backend/src/main/resources/karate/execute-http.feature`
- FOUND: `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java`
- FOUND: `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java`
- FOUND: `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java`
- FOUND commit ed189e0
- FOUND commit 40d1086
