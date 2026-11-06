# E2E Orchestrator Platform - Frontend Node 26 LTS Upgrade

## What This Is

The End-to-End Test Workflow Orchestrator Platform is a full-stack developer tool enabling visual construction, execution, and real-time monitoring of automated API, database, and event-driven test workflows. This milestone modernizes the frontend development toolchain and container build pipeline by upgrading to Node.js 26 LTS.

## Core Value

Ensure frontend build reliability, security, and developer environment consistency on the latest Node.js 26 LTS runtime without breaking existing visual canvas workflows or packaging pipelines.

## Requirements

### Validated

- ✓ Visual workflow canvas powered by React 18 and @xyflow/react (React Flow) — existing
- ✓ Dynamic node configuration drawer with schema-driven parameter forms — existing
- ✓ OpenAPI 3.0 endpoint catalog with drag-and-drop workflow assembly — existing
- ✓ Embedded Karate 2.1.4 DSL runner supporting HTTP, DB, Kafka, Delay, Transform, and Subworkflows — existing
- ✓ Real-time execution logs and status broadcast via WebSocket STOMP and SSE — existing
- ✓ Multi-architecture Docker container packaging (Nginx frontend, Jlink Java 25 backend) — existing

### Active

- [ ] Update `frontend/package.json` to define Node 26 LTS in `engines` field (`>=26.0.0`)
- [ ] Update `@types/node` in `frontend/devDependencies` to compatible Node 26 typing
- [ ] Create `.nvmrc` in project and frontend root specifying Node 26
- [ ] Update `frontend/Dockerfile` builder stage base image to `node:26-alpine`
- [ ] Verify clean dependencies installation (`npm ci`) and lockfile compatibility under Node 26
- [ ] Verify full frontend typecheck (`tsc`) and production bundle build (`vite build`) succeed cleanly under Node 26
- [ ] Ensure Docker build workflow (`frontend/Dockerfile`) builds and produces functional static assets

### Out of Scope

- Upgrading major framework versions (React 19, Vite 6, Tailwind 4) — preserving stability of current React 18 / Vite 5 / Tailwind 3 stack
- Backend modifications — Spring Boot 4.1.2 and Java 25 LTS backend remains untouched
- Overhauling Docker Compose or CI/CD deployment topology — retaining existing Nginx Alpine serving architecture

## Context

- The platform frontend is built with TypeScript 5.6.3, Vite 5.4.9, and React 18.3.1.
- Node 26 LTS is installed in the local environment (`v26.13.0`).
- The frontend container currently uses `node:22-alpine` as a builder image before copying static assets into `nginx:alpine`.
- Upgrading to Node 26 brings official LTS support, updated V8 engine enhancements, and updated security patches.

## Constraints

- **Compatibility**: All existing frontend packages (`@xyflow/react`, `@stomp/stompjs`, `axios`, `clsx`, `lucide-react`, `sockjs-client`, `tailwind-merge`) must build without errors under Node 26.
- **Docker**: The frontend Dockerfile must build reliably with `node:26-alpine`.
- **Zero Breaking Changes**: The visual canvas UI and execution streaming behavior must not change.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Upgrade frontend toolchain to Node 26 LTS | Keep dependencies, security patches, and container build environments aligned with LTS | — Pending |
| Full upgrade scope (Dockerfile, package.json, types, .nvmrc) | Guarantees consistency between local development, containerized builds, and CI pipelines | — Pending |
| Keep existing React 18 and Vite 5 framework versions | Avoid framework churn and risk to @xyflow/react canvas stability during a runtime engine upgrade | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-11-06 after initialization*
