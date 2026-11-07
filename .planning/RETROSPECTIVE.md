# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — Standalone DB Export & Form URL-Encoded Requests

**Shipped:** 2026-11-07  
**Phases:** 2 | **Plans:** 4 | **Tasks:** 4 | **Sessions:** 1  

### What Was Built
- Self-contained standalone Karate `.feature` exporter for database polling steps (`dbQuery` and `dbPoll`) with retry loops, assertions, and variable extractions running without platform classpath dependencies.
- Full-stack `application/x-www-form-urlencoded` HTTP request support: drawer segmented control, auto-synced `Content-Type`, interactive parameter table with variable picker and duplicate detection, dynamic Karate runtime execution branching with native `form fields`, and standalone exporter syntax generation.
- Integration and portability test suite (`WorkflowFeaturesAndPortabilityTest`) validating standalone Karate Core execution against in-memory H2 databases and mock REST endpoints.

### What Worked
- Parallel subagent specialization (planner, executor, verifier, integration checker) caught edge cases early (e.g., Karate 2.x JS parser syntax restrictions on multi-token keywords in `* if` statements).
- Reusing `formatKarateExpression` across phases enabled seamless cross-phase variable chaining from DB polling results directly into URL-encoded form parameters.
- Inline JDBC helper generation in Karate `Background:` eliminated external JAR dependencies, guaranteeing 100% portable standalone execution.

### What Was Inefficient
- Stale verification fingerprints when later phases modify shared source files (`TestWorkflowService.java`); required re-fingerprinting Phase 1 before milestone close.
- Initial UI-SPEC spacing and typography dimensions failed strict 4-multiple grid checks, requiring a revision cycle before planning.

### Patterns Established
- Native Karate keyword branching: separating `form fields` and `request` to prevent JSON payload clobbering.
- Array-backed local React component state (`formRows`) for interactive key-value table inputs to handle transient duplicate/empty keys during user editing before persisting to JSONB.

### Key Lessons
1. Always branch Karate HTTP body submission logic (`* form fields` vs `* request`) because Karate defaults to raw JSON serialization if `request` is populated.
2. Use unified `Scenario: Execute Workflow Steps` in standalone Karate export to enable native scenario variable scope sharing without bespoke bridges.

### Cost Observations
- Sessions: 1
- Notable: Rapid execution (< 30 minutes total planning and execution across 2 phases) with zero broken flows and 100% test pass rate.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 1 | 2 | Introduced standalone Karate export with inline JDBC and native URL-encoded form handling |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v1.0 | 16/16 | 100% v1 requirements | 2 (inline JDBC helper, native form fields) |

### Top Lessons (Verified Across Milestones)

1. Avoid classpath dependencies in exported artifacts by embedding standard Java standard library logic directly in feature `Background:`.
2. Standardize on native Karate DSL keywords for non-JSON payloads rather than attempting raw body serialization.
