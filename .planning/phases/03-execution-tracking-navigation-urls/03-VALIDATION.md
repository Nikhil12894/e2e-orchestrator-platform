---
phase: "03"
slug: "execution-tracking-navigation-urls"
status: complete
nyquist_compliant: true
wave_0_complete: true
created: "2026-11-08"
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.11+ & Spring Test 7.0.10 |
| **Config file** | `backend/src/main/resources/application-test.yml` |
| **Quick run command** | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml` |
| **Full suite command** | `mvn test -f backend/pom.xml && npm --prefix frontend run build` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml`
- **After every plan wave:** Run `mvn test -f backend/pom.xml && npm --prefix frontend run build`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | EXEC-01, EXEC-02 | T-03-01 | RFC 7231 Location header matches statusUrl, host header poison immunity | integration | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest#testTriggerExecutionReturnsLocationHeaderAndUrls -f backend/pom.xml` | ✅ W0 | ✅ green |
| 03-01-02 | 01 | 1 | EXEC-01, EXEC-03 | T-03-01, T-03-02 | Details and list endpoints return URLs, completed runs provide direct report link, proxy headers normalized | integration | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml` | ✅ W0 | ✅ green |
| 03-01-03 | 01 | 1 | EXEC-01 | T-03-SC | Optional frontend TypeScript types compile with zero warnings or breaking changes | static typecheck | `mvn test -f backend/pom.xml && npm --prefix frontend run build` | ✅ Existing | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` — MockMvc test covering EXEC-01, EXEC-02, EXEC-03, Location header, and ForwardedHeaderFilter.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| None | — | — | All phase behaviors have automated verification. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 5s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** complete

