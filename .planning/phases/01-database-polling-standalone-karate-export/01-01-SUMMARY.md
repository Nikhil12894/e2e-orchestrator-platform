---
phase: 01-database-polling-standalone-karate-export
plan: 01
subsystem: backend-export
tags:
  - karate
  - standalone-export
  - database-polling
  - jdbc
  - retry-loop
  - assertions
status: complete
requires: []
provides:
  - standalone-karate-db-poll-export
  - inline-jdbc-background-helpers
  - dynamic-token-interpolation
affects:
  - test-workflow-service
  - standalone-feature-portability
tech-stack:
  added: []
  patterns:
    - inline-jdbc-background-helpers
    - graaljs-retry-loop
    - dual-case-column-mapping
    - token-interpolation
key-files:
  created: []
  modified:
    - backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java
    - backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java
decisions:
  - "D-01: Self-contained inline JDBC functions in Background (dbQuery and dbPoll) using java.sql.DriverManager and zero proprietary platform classes"
  - "D-02: Retry loop respecting maxAttempts, pollIntervalMs, and timeoutMs with Thread.sleep"
  - "D-03: Dual-case field equality condition evaluator and column match assertions"
  - "D-04: Variable extractions aliasing pollResult and result to dbResult for downstream steps"
metrics:
  duration: 10 min
  completed: "2026-11-07"
actuals:
  tokens: 5982
  tasks: 2
  commits: 2
  plan_head_before: "25b1ba5a0e1c51212f693b41c07bed6f6317ea9e"
  plan_head_after: "55bc77d3c3cf6331246b692ca497c440f482027f"
---

# Phase 01 Plan 01: Standalone Database Polling Karate Feature Export Summary

**Substantive Summary:** Standalone Karate feature exporter for database polling workflow steps, emitting self-contained inline JDBC functions (`dbQuery` and `dbPoll`) in the feature `Background:`, retry loops with configurable timing, compound condition evaluation safeguards, step-level column match assertions, and downstream variable extraction.

---

## Performance Against Plan

| Metric | Planned | Actual |
|--------|---------|--------|
| Tasks | 2 | 2 |
| Commits | 2 | 2 |
| Token Scale | 45000 | 5982 |
| Duration | ~15 min | ~10 min |

---

## Tasks Completed

| Task | Type | Description | Commit | Key Files |
|------|------|-------------|--------|-----------|
| Task 1 | tracer | End-to-end standalone DB poll export slice (Background JDBC helpers and basic scenario) | `db41fa5` | `TestWorkflowService.java`, `WorkflowFeaturesAndPortabilityTest.java` |
| Task 2 | auto | Complete DB poll scenario generation with conditions, assertions, extractions, and secret resolution | `55bc77d` | `TestWorkflowService.java`, `WorkflowFeaturesAndPortabilityTest.java` |

---

## Delivered Capabilities

### 1. Self-Contained Inline JDBC Helpers in Background (DBEXP-01 / D-01)
- Emitted in `Background:` only when active `StepType.DB_POLL` steps exist in the workflow AST.
- Implements `dbQuery` using standard JDK `Java.type('java.sql.DriverManager')` and `Java.type('java.lang.Class').forName(...)`.
- Zero proprietary orchestrator platform classes referenced in exported `.feature` files, ensuring standalone portability across any standard Karate CLI or Maven runner.
- Dual-case column name mapping (`row[colName]` and `row[colName.toLowerCase()]`) mitigates cross-database case inconsistencies (e.g., Oracle/H2 uppercase vs PostgreSQL lowercase).
- JDBC `Connection`, `Statement`, and `ResultSet` instances are individually enclosed in `try-finally` blocks to guarantee resource closure (mitigating T-01-03).
- Dynamic token replacer (`/\$\{([^}]+)\}/g`) resolves `${var}`, `${$env:VAR}`, and `${$secret:VAR}` from `karate.get()`, `karate.properties`, and `System.getenv()` (mitigating T-01-01).

### 2. Polling Retry Loops & Timing (DBEXP-02 / D-02)
- Implements `dbPoll` with `maxAttempts`, `pollIntervalMs`, `timeoutMs`, and `conditionFn(rows)` returning a composite result object (`{ matched, attempts, durationMs, rows, row, latestRow }`).
- Uses `Thread.sleep(intervalMs)` between attempts within timeout boundaries.
- Emits formatted `dbConfig` dictionary safely serialized with Jackson `ObjectMapper.writeValueAsString` (mitigating T-01-02).
- Automatically infers database driver class name via `HikariConnectionPoolManager.detectDriverClassName(jdbcUrl, driverClassName)` when omitted.

### 3. Condition Evaluator & Step Assertions (DBEXP-03 / D-03)
- If `expectedRowCount` is specified, generates `return rows.length === expectedRowCount;` and `match karate.sizeOf(dbResult.rows) == expectedRowCount`.
- If a single equality condition is configured (`row.field == 'value'`), uses a strict single-equality regex (`^\s*(?:row\.)?\w+\s*==\s*[^=&|]+$`) and emits dual-case field checking (`r['field'] == val || r['field.toLowerCase()'] == val`).
- If a compound condition is configured (`row.status == 'COMPLETED' && row.retry_count > 2`), preserves the expression in a direct evaluation function with `var row = rows[0]; var r = row;` to prevent `==` split corruption or ReferenceErrors.
- Generates step-level column assertions for `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `GREATER_THAN`, `LESS_THAN`, `MATCHES_REGEX`, and `IS_NOT_NULL`.

### 4. Downstream Variable Extraction (DBEXP-04 / D-04)
- Emits `* def <varName> = dbResult.rows[0].<cleanCol>` for all configured extractions where source is `DB_ROW_COLUMN` or `DB_COLUMN`.
- Uses `cleanDbColumnPath` to strip leading `$.`, `$`, `row.`, and `latestRow.` prefixes.
- Aliases `* def pollResult = dbResult` and `* def result = dbResult` for backward compatibility with downstream Karate expressions.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Populated `expectedRowCount` in `convertNodeToStep`**
- **Found during:** Task 2 implementation
- **Issue:** Canvas nodes containing `expectedRowCount` were not copying the integer into `DbPollStepConfig` during `convertNodeToStep`, which would cause `expectedRowCount` to be lost in canvas-derived ASTs.
- **Fix:** Added parsing of `data.get("expectedRowCount")` into `dbConfig.setExpectedRowCount(...)`.
- **Files modified:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java`
- **Commit:** `55bc77d`

---

## Known Stubs

None - all database polling standalone export features are fully wired with real JDBC helper generation, retry loops, condition evaluation, and assertions.

---

## Threat Mitigation Verification

| Threat ID | Disposition | Status | Verified In |
|-----------|-------------|--------|-------------|
| T-01-01 (Info Disclosure) | mitigate | Verified | Dynamic regex token replacement resolves `${$env:VAR}` / `${$secret:VAR}` at runtime in exported JS helper |
| T-01-02 (Tampering / Injection) | mitigate | Verified | Jackson `ObjectMapper.writeValueAsString` serializes `dbConfig` map safely escaping SQL quotes |
| T-01-03 (Resource Exhaustion) | mitigate | Verified | Separate `try/catch` blocks in `finally` for `ResultSet`, `Statement`, and `Connection` |
| T-01-SC (Supply Chain) | mitigate | Verified | No new dependencies introduced |

---

## Verification Evidence

- `WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExport`: Passes with negative assertions confirming no JDBC helpers on HTTP-only workflows.
- `WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll`: Passes with assertions verifying `DriverManager`, `dbQuery`, `dbPoll`, zero proprietary platform classes, retry parameters, row count matching, compound condition preservation, and variable extraction.
- Full test suite in `WorkflowFeaturesAndPortabilityTest`: 9/9 tests pass.

---

## Self-Check: PASSED
- `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` exists: FOUND
- `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` exists: FOUND
- Commit `db41fa5` exists in ancestry: FOUND
- Commit `55bc77d` exists in ancestry: FOUND
