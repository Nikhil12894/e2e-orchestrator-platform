# Requirements: E2E Orchestrator Platform - Frontend Node 26 LTS Upgrade

**Defined:** 2026-11-06
**Core Value:** Ensure frontend build reliability, security, and developer environment consistency on the latest Node.js 26 LTS runtime without breaking existing visual canvas workflows or packaging pipelines.

## v1 Requirements

### Environment & Configuration

- [ ] **ENV-01**: Define Node 26 LTS engine constraint in `frontend/package.json` (`"engines": { "node": ">=26.0.0" }`)
- [ ] **ENV-02**: Add `.nvmrc` specifying Node 26 (`26`) for local version management
- [ ] **ENV-03**: Update `@types/node` in `frontend/package.json` devDependencies to compatible Node 26 typings

### Build & Verification

- [ ] **BUILD-01**: Verify clean dependency installation (`npm ci`) under Node 26 with compatible package lock
- [ ] **BUILD-02**: Verify TypeScript typecheck (`tsc`) completes with zero type errors under Node 26
- [ ] **BUILD-03**: Verify Vite production bundler (`vite build`) successfully generates distribution artifacts in `frontend/dist/`

### Containerization

- [ ] **DOCKER-01**: Update `frontend/Dockerfile` builder stage base image to `node:26-alpine`
- [ ] **DOCKER-02**: Verify multi-stage Docker build compiles and packages frontend into Nginx image successfully

## v2 Requirements

### Tooling & UI Modernization

- **MOD-01**: Migrate build tooling to Vite 6 / Rollup 4 enhancements
- **UI-01**: Upgrade to React 19 once `@xyflow/react` declares official support

## Out of Scope

| Feature | Reason |
|---------|--------|
| React 19 / Vite 6 upgrade | Preserving stability of `@xyflow/react` canvas components |
| Backend Java / Spring changes | Backend runs on Java 25 LTS and is completely independent |
| Docker Hub image publishing | Milestone scopes codebase modernization and verification; release tagging manages publishing |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ENV-01 | Phase 1 | Pending |
| ENV-02 | Phase 1 | Pending |
| ENV-03 | Phase 1 | Pending |
| BUILD-01 | Phase 1 | Pending |
| BUILD-02 | Phase 1 | Pending |
| BUILD-03 | Phase 1 | Pending |
| DOCKER-01 | Phase 2 | Pending |
| DOCKER-02 | Phase 2 | Pending |

**Coverage:**
- v1 requirements: 8 total
- Mapped to phases: 8
- Unmapped: 0 ✓

---
*Requirements defined: 2026-11-06*
*Last updated: 2026-11-06 after roadmap creation*
