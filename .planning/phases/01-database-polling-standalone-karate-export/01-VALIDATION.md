---
phase: "1"
slug: "database-polling-standalone-karate-export"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-11-06"
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.x + Spring Boot Test Starter + Karate Core 2.1.4 |
| **Config file** | `backend/pom.xml`, `backend/src/main/resources/application-test.yml` |
| **Quick run command** | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExport` |
| **Full suite command** | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest,DbExecutorAndReportExclusionTest,TestWorkflowServiceTest` |
| **Estimated runtime** | ~10-15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExport`
- **After every plan wave:** Run `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest,DbExecutorAndReportExclusionTest,TestWorkflowServiceTest`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | DBEXP-01 | T-01-01 | Validates that exported background contains self-contained JDBC driver manager functions without external platform dependencies | unit | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | DBEXP-02 | T-01-02 | Validates that exported scenario includes polling retry loop respecting pollIntervalMs and maxAttempts | unit | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | DBEXP-03 | T-01-02 | Validates that exported scenario generates row count or column match assertions | unit | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ W0 | ⬜ pending |
| 01-01-04 | 01 | 1 | DBEXP-04 | T-01-01 | Validates variable extraction statements emitted for DB query results and usable in downstream steps | unit | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ W0 | ⬜ pending |
| 01-01-05 | 01 | 2 | DBEXP-01..04 | T-01-01 | End-to-end execution of generated standalone .feature file via Runner.path against H2 DB | integration | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExecutionStandalone` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` — stubs and test cases for DB poll standalone export generation, assertion syntax, and standalone Karate execution.

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
