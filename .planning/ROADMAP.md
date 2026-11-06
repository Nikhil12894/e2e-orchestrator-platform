# Roadmap: E2E Orchestrator Platform - Frontend Node 26 LTS Upgrade

## Overview

This roadmap modernizes the frontend development toolchain and container packaging pipeline of the E2E Orchestrator Platform by transitioning to Node.js 26 LTS. The execution spans two phases: first configuring the local frontend environment and validating dependency resolution, typechecking, and production builds under Node 26; second, updating and verifying the multi-stage Docker build with a Node 26 Alpine builder image to guarantee deployment parity.

## Phases

**Phase Numbering:**
- Integer phases (1, 2): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Frontend Toolchain & Build Verification** - Upgrade frontend configuration to Node 26 LTS and verify dependency installation, typechecking, and production bundling.
- [ ] **Phase 2: Docker Container Packaging** - Update Dockerfile builder to Node 26 Alpine and verify production container packaging.

## Phase Details

### Phase 1: Frontend Toolchain & Build Verification
**Goal:** Upgrade frontend configuration to Node 26 LTS and verify clean dependency installation, typechecking, and production bundling
**Mode:** mvp
**Depends on:** Nothing (first phase)
**Requirements:** ENV-01, ENV-02, ENV-03, BUILD-01, BUILD-02, BUILD-03
**Success Criteria** (what must be TRUE):
  1. Developers running Node 26 LTS can install dependencies cleanly with `npm ci` without lockfile mismatch or peer dependency conflicts
  2. `frontend/package.json` engines field enforces `node >=26.0.0` and `.nvmrc` specifies Node 26 for local environment consistency
  3. TypeScript compilation (`tsc`) succeeds with zero type errors using Node 26 typings
  4. Production bundle build (`vite build`) completes cleanly and outputs verified distribution assets in `frontend/dist/`
**Plans:** TBD
**UI hint:** yes

### Phase 2: Docker Container Packaging
**Goal:** Modernize the container build pipeline to use Node 26 Alpine and produce a verified production Nginx container image
**Mode:** mvp
**Depends on:** Phase 1
**Requirements:** DOCKER-01, DOCKER-02
**Success Criteria** (what must be TRUE):
  1. `frontend/Dockerfile` builder stage specifies `node:26-alpine` and completes multi-stage image build without errors
  2. Built Docker container starts and serves the compiled frontend static bundle via Nginx
**Plans:** TBD
**UI hint:** yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Frontend Toolchain & Build Verification | 0/TBD | Not started | - |
| 2. Docker Container Packaging | 0/TBD | Not started | - |
