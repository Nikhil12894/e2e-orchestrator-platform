---
phase: "5"
slug: "ci-cd-webhook-modal-pipeline-snippets"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-11-08"
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Node.js built-in `node:test` + `node:assert` (Zero external test runner dependencies) |
| **Config file** | `frontend/package.json` |
| **Quick run command** | `cd frontend && npm run build` |
| **Full suite command** | `cd frontend && npm run build && node --test tests/cicdSnippets.test.ts` |
| **Estimated runtime** | ~3 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontend && npm run build`
- **After every plan wave:** Run `cd frontend && npm run build && node --test tests/cicdSnippets.test.ts`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | CURL-01 | — | Generates pure cURL status polling & report link strings | unit | `node --test tests/cicdSnippets.test.ts` | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 1 | CURL-02 | T-05-01 | Clamps timeoutSec between 1s and 300s in sync mode snippet | unit | `node --test tests/cicdSnippets.test.ts` | ❌ W0 | ⬜ pending |
| 05-01-03 | 01 | 1 | CURL-03 | T-05-02 | Quotes all variables in generated bash script avoiding command injection | unit | `node --test tests/cicdSnippets.test.ts` | ❌ W0 | ⬜ pending |
| 05-01-04 | 01 | 1 | ALL | T-05-03 | React JSX escapes workflow name preventing XSS | build / compile | `cd frontend && npm run build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/src/utils/cicdSnippets.ts` — Snippet formatting pure utility functions
- [ ] `tests/cicdSnippets.test.ts` — Unit tests for snippet formatting and script generation
- [ ] Framework install: None needed (`node:test` is built-in on Node 26)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Modal Visual Layout & Clipboard Copy | CURL-01, CURL-02, CURL-03 | Browser clipboard interaction and visual responsiveness | Open workflow in frontend, click "CI/CD Webhook" in top navbar, toggle Async vs Sync mode, test copy buttons, verify toast notification |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
