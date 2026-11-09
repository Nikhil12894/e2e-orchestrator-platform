---
phase: 05-sub-workflow-variable-promotion-global-flow-headers
plan: 01
subsystem: execution-engine
tags: [sub-workflow, dynamic-headers, variable-promotion, ast, karate-export]
status: complete
requires:
  - 04-synchronous-wait-execution-mode
provides:
  - dynamic-flow-header-interpolation
  - setup-workflow-ast-and-canvas
  - runtime-setup-workflow-synthesis
  - standalone-karate-dynamic-headers-export
affects:
  - backend-ast
  - karate-execution-bridge
  - karate-feature-export
  - workflow-settings
tech-stack:
  added: []
  patterns:
    - Per-request dynamic header evaluation via DynamicKarateBridge
    - Pre-execution setup step prepending at index 0 in stepList
    - Circular self-reference guard in setup workflows
    - Dynamic Karate configure headers JS function for standalone feature export
key-files:
  created:
    - backend/src/main/java/com/orchestrator/platform/domain/model/ast/SetupWorkflowConfig.java
  modified:
    - backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java
    - backend/src/main/java/com/orchestrator/platform/domain/model/canvas/CanvasState.java
    - backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java
    - backend/src/main/resources/karate/execute-step.feature
    - backend/src/main/resources/karate/execute-http.feature
    - backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java
    - backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java
    - backend/src/test/java/com/orchestrator/platform/SubWorkflowCompositionTest.java
decisions:
  - "Added overloaded DynamicKarateBridge.computeEffectiveHeaders accepting executionId and globalContext to dynamically resolve ${...} expressions before every HTTP request"
  - "Preserved original 2-argument computeEffectiveHeaders method for backward compatibility"
  - "Stored SetupWorkflowConfig in jsonb columns of TestWorkflowEntity (WorkflowAst and CanvasState) without requiring database migrations"
  - "Synthesized setup workflow step in DynamicKarateRunner prepending it at index 0 with defensive circular call prevention"
  - "Emitted dynamic JavaScript configure headers = function() { ... } callback in standalone exported features for 100% execution parity"
metrics:
  tasks: 3
  completed_date: "2026-11-09"
---

# Phase 05 Plan 01: Dynamic Headers, Setup Workflow AST, and Standalone Export Parity Summary

Backend execution engine implementation of dynamic global flow header resolution, pre-requisite setup workflow execution with variable promotion, and standalone Karate export parity.

## Accomplishments

1. **Dynamic Global Header Variable Resolution (SUBWF-01):**
   - Added overloaded `DynamicKarateBridge.computeEffectiveHeaders(String executionId, Map<String, Object> flowHeaders, Map<String, Object> stepConfig, Map<String, Object> globalContext)` that dynamically resolves `${...}` template tokens in global flow headers against live execution context before each HTTP request.
   - Updated `execute-step.feature` and `execute-http.feature` to pass `executionId` and `globalContext` into the bridge header resolution.
   - Verified that extracted variables like `authToken` or `tenantId` from preceding steps immediately and reactively populate global headers (`Authorization: Bearer ${authToken}`) on downstream HTTP requests.

2. **Setup Workflow AST and Execution Synthesis (SUBWF-02):**
   - Created `SetupWorkflowConfig.java` model holding `workflowId`, `workflowName`, `enabled`, `inheritEnvAndSecrets`, and `extractions`.
   - Added `setupWorkflow` fields to `WorkflowAst.java` and `CanvasState.java`.
   - Updated `TestWorkflowService.deriveAstFromCanvas` to transfer `setupWorkflow` during canvas-to-AST synchronization.
   - Updated `DynamicKarateRunner.java` to validate circular self-references and automatically synthesize a prepended `SUB_WORKFLOW` step at index 0 when `setupWorkflow` is enabled.

3. **Standalone Karate Feature Export Parity (SUBWF-04):**
   - Updated `TestWorkflowService.exportWorkflowToKarate` to detect dynamic `${...}` tokens in `defaultHeaders` and emit a Karate dynamic JavaScript header callback: `* configure headers = function() { return { ... }; }`.
   - Added inlining of `setupWorkflow` steps as initial background / sequential steps in standalone feature exports.

4. **Integration Verification:**
   - Added 3 comprehensive test methods to `SubWorkflowCompositionTest.java`:
     - `testDynamicGlobalHeaderVariableResolution`
     - `testSetupWorkflowExecutionAndPromotion`
     - `testStandaloneKarateDynamicHeaderExport`
   - All 7 tests in `SubWorkflowCompositionTest` and all 75 tests across the entire backend suite pass with 0 failures and 0 errors.

## Verification Results

- `mvn test -Dtest=SubWorkflowCompositionTest -f backend/pom.xml`: 7 tests passed, 0 failures, 0 errors.
- `mvn test -f backend/pom.xml`: 75 tests passed, 0 failures, 0 errors.
