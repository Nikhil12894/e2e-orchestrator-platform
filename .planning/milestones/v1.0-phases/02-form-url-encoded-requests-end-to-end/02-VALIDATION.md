---
phase: "02"
slug: "form-url-encoded-requests-end-to-end"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-11-07"
---

# Phase 02 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.11.x + Spring Boot Test 4.1.2 + Karate Core 2.1.4 / TypeScript + Vite Build |
| **Config file** | `backend/src/main/resources/application-test.yml` & `frontend/vite.config.ts` |
| **Quick run command (Backend)** | `export PATH="/Users/nalinkumar/.sdkman/candidates/maven/current/bin:$PATH" && export JAVA_HOME="/Users/nalinkumar/.sdkman/candidates/java/25.0.3-tem" && cd backend && mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExport` |
| **Quick run command (Frontend)** | `cd frontend && npm run build` |
| **Full suite command** | `export PATH="/Users/nalinkumar/.sdkman/candidates/maven/current/bin:$PATH" && export JAVA_HOME="/Users/nalinkumar/.sdkman/candidates/java/25.0.3-tem" && cd backend && mvn test -Dtest=WorkflowFeaturesAndPortabilityTest && cd ../frontend && npm run build` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command for modified module (`mvn test` or `npm run build`)
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 20 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | FORM-03 | T-02-02 | Form fields serialized via Karate `form fields` keyword without parameter delimiter injection | integration | `cd backend && mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testDynamicKarateExecutionFormUrlEncoded` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | FORM-04 | T-02-01 | Exported `.feature` files format `${$secret:VAR}` / `${$env:VAR}` as environment property lookups, never raw secrets | integration | `cd backend && mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportFormUrlEncoded` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 2 | FORM-01 | — | Body type toggle state cleanly typed and switchable | build/typecheck | `cd frontend && npm run build` | ✅ | ⬜ pending |
| 02-02-02 | 02 | 2 | FORM-02 | T-02-03 | Duplicate parameter keys validated in real time with inline rose-400 warning; Content-Type synchronized | build/typecheck | `cd frontend && npm run build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java` — add `POST /api/v1/mock/auth/token` endpoint consuming `application/x-www-form-urlencoded`
- [ ] `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` — add test methods `testStandaloneKarateFeatureExportFormUrlEncoded`, `testDynamicKarateExecutionFormUrlEncoded`, and `testStandaloneKarateFeatureExecutionFormUrlEncoded`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual drawer layout & color contrast | FORM-01, FORM-02 | Visual UX alignment with UI-SPEC design tokens | Open node config drawer in browser (`http://localhost:5173`), toggle between `JSON Body` and `URL-Encoded Form`, verify table rows and badge styling |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 20s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
