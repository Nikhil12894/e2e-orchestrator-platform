# Phase 05 Context: Sub-Workflow Variable Promotion & Global Flow Headers

**Phase:** 05  
**Goal:** Enable workflows to include previous/sub-workflows as setup or sequential steps, automatically promoting extracted variables into global workflow settings and headers across both dynamic execution and standalone export.  
**Requirements:** SUBWF-01, SUBWF-02, SUBWF-03, SUBWF-04  

## User Request & Intent

The user specifically requested:
> *"my plane is to include previous workflow if its a first or previous stape any ney stape thne we can able to add the extracted variable from previous workflow/sub workflow can be used in global workflow seting"*

### Key Use Cases
1. **Auth / Token Generation Setup:** Running a login/auth workflow as step 1 (or configured as a pre-requisite setup workflow in settings), extracting `authToken`, and having global default headers like `Authorization: Bearer ${authToken}` automatically populate for all subsequent HTTP steps without per-step header overrides.
2. **Modular Workflow Chaining:** Adding a preceding workflow before any step, extracting variables (`orderId`, `customerId`, `tenantId`), and having them promoted into the active execution context for downstream steps.
3. **Execution Parity:** Ensuring both in-memory virtual thread execution (`DynamicKarateRunner`) and standalone exported `.feature` files (`TestWorkflowService`) evaluate global headers dynamically with runtime variables.

## Decisions

- **D-01:** Dynamic Flow Header Resolution (Backend Engine) — In `DynamicKarateBridge.computeEffectiveHeaders(executionId, flowHeaders, stepConfig, globalContext)`, interpolate `${varName}` placeholders against `globalContext` on every HTTP step execution. Keep `flowHeaders` reactive: as steps complete and extract new variables, subsequent steps immediately inherit the updated resolved header values. In `generic-flow-executor.feature` and `execute-step.feature`, pass `globalContext` into `execute-http.feature` so headers are computed dynamically per step.
- **D-02:** Global Workflow Settings & AST Enhancements — AST model and entity support `setupWorkflow` with `workflowId`, `workflowName`, `enabled`, `inheritEnvAndSecrets`, and `extractions`. In `WorkflowSettingsModal.tsx`, add a "Setup Workflow" tab where users can select an existing workflow, review its outputs, map variables, and quick-bind to Bearer Authorization headers.
- **D-03:** Canvas & Drawer Visual Polish — In `SubWorkflowNode.tsx`, show a visual badge/chip listing exported variables (e.g. `⚡ Exports: authToken, orderId`). In `NodeConfigDrawer.tsx`, compute available upstream variables and render one-click autocomplete suggestion pills above URL, header, and body inputs.
- **D-04:** Standalone Karate Export Parity — In `TestWorkflowService.generateStandaloneKarateFeature`, when default headers contain `${...}`, render Karate dynamic header function: `* configure headers = function() { ... }` that reads from `context` or `karate.get(...)`, and inline setup workflow steps at the beginning of the scenario if configured in workflow AST.
