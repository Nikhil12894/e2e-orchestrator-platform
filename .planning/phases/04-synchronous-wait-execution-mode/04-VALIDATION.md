---
phase: "04"
slug: "synchronous-wait-execution-mode"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-11-08"
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Spring Boot Starter Test (JUnit Jupiter 5 + MockMvc) |
| **Config file** | `backend/src/main/resources/application-test.yml` |
| **Quick run command** | `mvn test -Dtest=WorkflowExecutionSyncModeTest -f backend/pom.xml` |
| **Full suite command** | `mvn test -f backend/pom.xml` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=WorkflowExecutionSyncModeTest -f backend/pom.xml`
- **After every plan wave:** Run `mvn test -f backend/pom.xml`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 20 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | SYNC-01 | T-04-01 | Virtual thread parking without thread exhaustion; timeout clamp [1, 300] | integration | `mvn test -Dtest=WorkflowExecutionSyncModeTest#testSynchronousExecutionBlocksUntilComplete -f backend/pom.xml` | ❌ W0 | ⬜ pending |
| 04-01-02 | 01 | 1 | SYNC-02 | T-04-02 | HTTP 200 with completed execution entity and populated reportUrl; background task commits to DB | integration | `mvn test -Dtest=WorkflowExecutionSyncModeTest#testSynchronousExecutionReturnsCompletedAndReportUrl -f backend/pom.xml` | ❌ W0 | ⬜ pending |
| 04-01-03 | 01 | 1 | SYNC-02 | T-04-02 | Failed step returns HTTP 200 with status FAILED and valid report link | integration | `mvn test -Dtest=WorkflowExecutionSyncModeTest#testSynchronousExecutionFailureReturns200WithFailedStatus -f backend/pom.xml` | ❌ W0 | ⬜ pending |
| 04-01-04 | 01 | 1 | SYNC-03 | T-04-02 | HTTP 504 Gateway Timeout on timeout expiry without cancelling background virtual thread | integration | `mvn test -Dtest=WorkflowExecutionSyncModeTest#testSynchronousExecutionTimeoutReturns504 -f backend/pom.xml` | ❌ W0 | ⬜ pending |
| 04-01-05 | 01 | 1 | SYNC-03 | T-04-02 | Background virtual thread continues running to completion after HTTP timeout | integration | `mvn test -Dtest=WorkflowExecutionSyncModeTest#testTimeoutLeavesBackgroundExecutionRunning -f backend/pom.xml` | ❌ W0 | ⬜ pending |
| 04-01-06 | 01 | 1 | SYNC-03 | T-04-01 | `?timeoutStatus=202` parameter overrides default 504 status | integration | `mvn test -Dtest=WorkflowExecutionSyncModeTest#testTimeoutStatus202Override -f backend/pom.xml` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java` — Test scaffold for SYNC-01, SYNC-02, SYNC-03
- [ ] Configurable test workflow fixture with delay step to test timeout conditions deterministically

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| External cURL terminal execution with `--max-time` | SYNC-01, SYNC-02 | Verifies real shell cURL behavior against running orchestrator instance | Run `curl -i -X POST "http://localhost:8080/api/v1/workflows/{id}/execute?wait=true&timeoutSec=10"` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 20s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
