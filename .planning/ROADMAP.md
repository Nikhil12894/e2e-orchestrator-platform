# Roadmap: E2E Test Workflow Orchestrator

## Overview

This roadmap defines the delivery phases for achieving complete standalone Karate export fidelity for database polling steps and first-class support for `application/x-www-form-urlencoded` HTTP requests across the visual canvas, dynamic execution engine, and export pipeline. In Phase 1, database polling steps are enhanced to export self-contained inline JDBC functions, retry loops, row assertions, and variable extractions into runnable Karate `.feature` files without external platform dependencies. In Phase 2, the HTTP request lifecycle is updated end-to-end to support form-encoded payloads in the UI configuration drawer, runtime in-memory execution bridge, and standalone feature generator.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Database Polling Standalone Karate Export** - Export database polling workflow steps to self-contained standalone Karate features with JDBC querying, retries, assertions, and variable capture
- [ ] **Phase 2: Form URL-Encoded Requests End-to-End** - Add visual configuration, dynamic runtime execution, and standalone export for application/x-www-form-urlencoded HTTP requests

## Phase Details

### Phase 1: Database Polling Standalone Karate Export
**Mode:** mvp
**Goal**: Enable users to export database polling workflow steps into completely self-contained, executable standalone Karate `.feature` files with retry loops, assertions, and variable capture that run in standard Karate environments without platform dependencies
**Depends on**: Nothing (first phase)
**Requirements**: DBEXP-01, DBEXP-02, DBEXP-03, DBEXP-04
**Success Criteria** (what must be TRUE):
  1. User can export a workflow containing DB polling steps and receive a valid, runnable standalone `.feature` file with inline JDBC querying logic defined in the feature Background
  2. Standalone `.feature` file executes polling retries matching configured `pollIntervalMs` and `maxAttempts` until matching rows are returned or timeout is reached
  3. Standalone `.feature` file automatically asserts expected row counts and custom column condition expressions against query results
  4. Standalone `.feature` file captures extracted row values into Karate variables accessible to subsequent steps in the scenario
**Plans:** 2/2 plans complete

Plans:
**Wave 1**
- [x] 01-01-PLAN.md — Standalone Karate feature exporter enhancement for DB polling steps

**Wave 2**
- [x] 01-02-PLAN.md — Standalone Karate execution and portability tests

### Phase 2: Form URL-Encoded Requests End-to-End
**Mode:** mvp
**Goal**: Enable users to visually configure, execute in-memory, and export standalone Karate tests for HTTP requests with `application/x-www-form-urlencoded` payloads across all supported HTTP methods
**Depends on**: Phase 1
**Requirements**: FORM-01, FORM-02, FORM-03, FORM-04
**Success Criteria** (what must be TRUE):
  1. User can switch the request body type to `x-www-form-urlencoded` in the HTTP node drawer for `POST`, `PUT`, and `PATCH` requests
  2. User can add, edit, and delete form field key-value pairs with workflow variable expressions while the `Content-Type` header remains automatically synchronized
  3. Running a workflow with form-encoded HTTP steps executes via the dynamic Karate runner using native Karate form submission without errors
  4. Exporting a workflow with form-encoded HTTP steps produces standalone `.feature` scenarios using Karate's `form fields` syntax
**Plans:** 2/2 plans executed

Plans:
**Wave 1**
- [x] 02-01-PLAN.md — Backend AST, Karate runtime & standalone exporter

**Wave 2**
- [x] 02-02-PLAN.md — Frontend drawer UI & end-to-end verification

**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Database Polling Standalone Karate Export | 2/2 | Complete | 2026-11-07 |
| 2. Form URL-Encoded Requests End-to-End | 2/2 | In Progress | - |
