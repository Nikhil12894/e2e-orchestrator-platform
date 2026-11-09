# Phase 05 Technical Research: Sub-Workflow Variable Promotion & Global Flow Headers

**Phase:** 05  
**Domain:** Dynamic Execution Engine, Karate DSL Interoperability, AST Composition, Visual Workflow Canvas  
**Status:** COMPLETE  
**Confidence:** HIGH  

---

## 1. Executive Summary & Problem Analysis

In automated API testing, setup workflows (e.g. authentication, token generation, test data creation) are commonly run before core business assertions. While the platform supports `SUB_WORKFLOW` steps, two primary architectural limitations prevent seamless variable reuse in global settings:

1. **Static Flow-Level Header Evaluation:**
   `DynamicKarateRunner.java` pre-resolves global `flowHeaders` at workflow startup before step 1 executes. When a user defines `Authorization: Bearer ${authToken}` in Global Workflow Settings, the placeholder `${authToken}` cannot be resolved because the token has not been extracted yet.
2. **Missing Dynamic Context in Header Calculation:**
   `DynamicKarateBridge.computeEffectiveHeaders` merges static `flowHeaders` and per-step headers without accessing `globalContext`. Consequently, variables extracted during execution (into `globalContext`) are never re-interpolated into global headers for downstream steps.
3. **No Setup Workflow Concept in Global Settings:**
   Users must manually drop a `SUB_WORKFLOW` node onto the canvas, connect edges, and manually configure variable extractions on every workflow instead of configuring a re-usable "Pre-requisite / Setup Workflow" in workflow settings.
4. **Static Background Emission in Standalone Karate Feature Export:**
   `TestWorkflowService.java` emits `* configure headers = karate.merge(...)` statically inside `Background:`. Because Karate evaluates `Background:` only once at scenario initialization, subsequent steps do not inherit dynamic tokens extracted in scenario steps.

---

## 2. Technical Architecture & Solutions

### A. Dynamic Global Header Resolution (SUBWF-01)
- **Engine Flow:**
  1. `execute-step.feature`: Pass `globalContext` into `execute-http.feature`:
     ```gherkin
     * if (stepType == 'HTTP_REQUEST') karate.set('stepRes', karate.call('classpath:karate/execute-http.feature', { resolvedStep: resolvedStep, executionId: executionId, flowHeaders: flowHeaders, globalContext: globalContext }))
     ```
  2. `execute-http.feature`: Pass `globalContext` into `Bridge.computeEffectiveHeaders`:
     ```gherkin
     * def mergedHeaders = Bridge.computeEffectiveHeaders(executionId, fHeaders, httpCfg, globalContext)
     ```
  3. `DynamicKarateBridge.java`:
     Overload `computeEffectiveHeaders` to accept `executionId` and `globalContext`. Before merging headers, resolve all `${varName}` placeholders in `flowHeaders` dynamically using `contextEngine.resolveString(entry.getValue(), activeContext)` where `activeContext` merges `globalContext` and `activeContexts.get(executionId)`.
  4. **Result:** When Step 1 (or any sub-workflow) extracts `authToken`, all subsequent HTTP requests automatically inherit the evaluated `Authorization: Bearer <token>` from the global workflow headers.

### B. Setup / Pre-requisite Workflow in Global Workflow Settings (SUBWF-02)
- **Schema & Persistence:**
  - `WorkflowAst` and `CanvasState` are stored as JSONB in PostgreSQL. No database migration DDL is needed.
  - Define `SetupWorkflowConfig`:
    ```java
    public class SetupWorkflowConfig {
        private String workflowId;
        private String workflowName;
        private boolean enabled = true;
        private boolean inheritEnvAndSecrets = true;
        private List<VariableExtraction> extractions = new ArrayList<>();
    }
    ```
  - Add `setupWorkflow` to `WorkflowAst.java`, `CanvasState.java`, and `frontend/src/types/workflow.ts`.
- **Runtime Execution:**
  - In `DynamicKarateRunner.java`:
    If `ast.getSetupWorkflow()` is configured and enabled:
    Synthesize and prepend a synthetic `SUB_WORKFLOW` step at index 0 of the step list. It runs via the existing `execute-subworkflow.feature`, extracts variables, and binds them into `globalContext` before Canvas Step 1 starts.
- **Frontend Settings UI:**
  - In `WorkflowSettingsModal.tsx`, add a dedicated "Setup Workflow" tab:
    - Workflow selector dropdown (excluding current workflow to prevent circular references).
    - Table for output variable extraction (Source Variable Name -> Target Global Variable Name).
    - Quick-apply toggle: "Bind to Authorization Header (`Bearer ${token}`)".

### C. Canvas Export Badges & Drawer Autocomplete (SUBWF-03)
- **Canvas Node UX:**
  - In `SubWorkflowNode.tsx`, render a badge pill below the node title:
    `⚡ Exports: authToken, orderId`
- **Configuration Drawer Autocomplete:**
  - In `NodeConfigDrawer.tsx`, compute available upstream variables from preceding nodes and render 1-click suggestion chips above URL, Header, and Body inputs (e.g. clicking `+ ${authToken}` inserts it into the active input).

### D. Standalone Feature Export Parity (SUBWF-04)
- **Karate Dynamic Header Function:**
  Karate supports defining `configure headers` as a dynamic JavaScript function:
  ```gherkin
  * configure headers = function() {
      var h = karate.get('defaultHeaders', {});
      var token = karate.get('authToken') || (typeof context !== 'undefined' ? context.authToken : '');
      if (token) h['Authorization'] = 'Bearer ' + token;
      return h;
    }
  ```
  In `TestWorkflowService.generateStandaloneKarateFeature()`, inspect default headers for `${...}` placeholders. If present, emit Karate's dynamic header function.
- In addition, if `setupWorkflow` is configured, inline its steps at the top of the scenario.

---

## 3. Requirements to Verification Test Map

| Requirement | Implementation Target | Automated Verification Test |
|-------------|-----------------------|-----------------------------|
| **SUBWF-01** | `DynamicKarateBridge.java`, `execute-step.feature`, `execute-http.feature` | `mvn test -Dtest=SubWorkflowCompositionTest#testDynamicGlobalHeaderVariableResolution` |
| **SUBWF-02** | `SetupWorkflowConfig.java`, `WorkflowAst.java`, `DynamicKarateRunner.java`, `WorkflowSettingsModal.tsx` | `mvn test -Dtest=SubWorkflowCompositionTest#testSetupWorkflowExecutionAndPromotion` |
| **SUBWF-03** | `SubWorkflowNode.tsx`, `NodeConfigDrawer.tsx` | `npm --prefix frontend run build` |
| **SUBWF-04** | `TestWorkflowService.java` | `mvn test -Dtest=SubWorkflowCompositionTest#testStandaloneKarateDynamicHeaderExport` |

---

## 4. Risks & Mitigations

1. **Risk:** Backward compatibility with existing workflows that don't have a setup workflow.
   **Mitigation:** `setupWorkflow` defaults to null / optional; null-safe checks ensure zero impact on existing workflows.
2. **Risk:** Infinite recursion if a setup workflow references itself.
   **Mitigation:** Validate and prevent self-referencing workflows in both `WorkflowSettingsModal.tsx` and `TestWorkflowService.java`.
3. **Risk:** Performance overhead of re-evaluating headers on every step.
   **Mitigation:** Only re-interpolate strings containing `${...}`; static strings are resolved once with zero overhead.
