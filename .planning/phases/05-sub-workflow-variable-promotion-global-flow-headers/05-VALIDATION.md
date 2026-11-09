---
phase: "05"
slug: "sub-workflow-variable-promotion-global-flow-headers"
status: draft
nyquist_compliant: true
wave_0_complete: true
created: "2026-11-09"
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend Framework** | Spring Boot Test (JUnit Jupiter 5 + Mockito + Karate JUnit 6) |
| **Frontend Framework** | TypeScript Compiler (`tsc --noEmit`) + Vite Build |
| **Config file** | `backend/src/main/resources/application-test.yml` |
| **Quick run command** | `mvn test -Dtest=SubWorkflowCompositionTest -f backend/pom.xml` |
| **Full suite command** | `mvn test -f backend/pom.xml && npm --prefix frontend run build` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After backend engine task commit:** Run `mvn test -Dtest=SubWorkflowCompositionTest -f backend/pom.xml`
- **After frontend UI task commit:** Run `npm --prefix frontend run build`
- **After standalone export task commit:** Run `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest -f backend/pom.xml`
- **Before phase completion:** Full suite must be green: `mvn test -f backend/pom.xml && npm --prefix frontend run build`
- **Max feedback latency:** 25 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | SUBWF-01 | T-05-01 | Preceding step variable extractions dynamically resolve in global flow headers for downstream HTTP steps | integration | `mvn test -Dtest=SubWorkflowCompositionTest#testDynamicGlobalHeaderVariableResolution -f backend/pom.xml` | ✅ | ✅ green |
| 05-01-02 | 01 | 1 | SUBWF-02 | T-05-02 | Pre-requisite / Setup Workflow executes before canvas steps, promoting variables to global scope | integration | `mvn test -Dtest=SubWorkflowCompositionTest#testSetupWorkflowExecutionAndPromotion -f backend/pom.xml` | ✅ | ✅ green |
| 05-01-03 | 01 | 1 | SUBWF-04 | T-05-03 | Standalone Karate feature export renders dynamic header function (`configure headers = function()`) with runtime context evaluation | unit / export | `mvn test -Dtest=SubWorkflowCompositionTest#testStandaloneKarateDynamicHeaderExport -f backend/pom.xml` | ✅ | ✅ green |
| 05-02-01 | 02 | 2 | SUBWF-02 | T-05-02 | WorkflowSettingsModal provides "Setup Workflow" tab with workflow picker and variable mapping | component / build | `npm --prefix frontend run build` | ✅ | ✅ green |
| 05-02-02 | 02 | 2 | SUBWF-03 | T-05-01 | SubWorkflowNode displays exported variable badges and NodeConfigDrawer provides variable suggestion pills | component / build | `npm --prefix frontend run build` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Add test cases to `backend/src/test/java/com/orchestrator/platform/SubWorkflowCompositionTest.java`:
  - `testDynamicGlobalHeaderVariableResolution`: Verifies that a variable extracted from Step 1 (`authToken`) dynamically resolves in global `flowHeaders` (`Authorization: Bearer ${authToken}`) on Step 2.
  - `testSetupWorkflowExecutionAndPromotion`: Verifies that configuring a `setupWorkflow` runs prior to canvas step 1 and exposes variables in `globalContext`.
  - `testStandaloneKarateDynamicHeaderExport`: Verifies that exported `.feature` files configure headers dynamically and include setup workflow steps.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 25s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** verified
