# Phase 1: Frontend Toolchain & Build Verification - Context

**Gathered:** 2026-11-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Upgrade the frontend configuration and toolchain to Node.js 26 LTS. This phase encompasses updating `frontend/package.json` engine restrictions, updating `@types/node`, creating `.nvmrc` and `frontend/.npmrc`, and verifying that dependencies resolve cleanly (`npm ci`), TypeScript typecheck passes (`tsc`), and production build completes (`vite build`) producing functional distribution artifacts.

</domain>

<decisions>
## Implementation Decisions

### Engine Constraint & Strictness
- **D-01:** Define `"engines": { "node": ">=26.0.0" }` in `frontend/package.json` — **Reversibility:** reversible — Allows Node 26 LTS and forward-compatible minor/patch releases while disallowing older Node runtimes.
- **D-02:** Add `engine-strict=true` in `frontend/.npmrc` — **Reversibility:** reversible — Enforces that `npm install` and `npm ci` fail immediately if an unsupported Node version is detected in local development or CI.
- **D-03:** Create `.nvmrc` specifying `26` at repository root and in `frontend/` — **Reversibility:** reversible — Guarantees consistent Node version pinning for developers using nvm / fnm.

### Build Verification & Tooling
- **D-04:** Update `@types/node` in `frontend/devDependencies` to compatible Node 26 typings while keeping existing core frontend dependencies (`@xyflow/react`, `react`, `react-dom`, `vite`, `tailwindcss`) pinned to preserve visual canvas stability.
- **D-05:** Verify clean dependency installation using `npm ci` without `--force` or `--legacy-peer-deps`.
- **D-06:** Verify `tsc --noEmit` and `vite build` complete with zero errors and generate static production assets in `frontend/dist/`.

### the agent's Discretion
- TypeScript compiler target in `frontend/tsconfig.json` (`ES2020` / `ESNext`) and Vite build target (`ES2022`) are retained to maximize browser compatibility while benefiting from Node 26 LTS build performance.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Specifications & Requirements
- `.planning/PROJECT.md` — Project context and core constraints
- `.planning/REQUIREMENTS.md` — Active v1 requirements (ENV-01, ENV-02, ENV-03, BUILD-01, BUILD-02, BUILD-03)
- `.planning/ROADMAP.md` — Phase 1 scope and success criteria

### Frontend Toolchain Configuration
- `frontend/package.json` — Frontend dependencies and scripts
- `frontend/package-lock.json` — Existing dependency lockfile
- `frontend/tsconfig.json` — TypeScript compiler configuration
- `frontend/vite.config.ts` — Vite bundling configuration

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `frontend/package.json`: Contains scripts `"dev": "vite"`, `"build": "tsc && vite build"`, `"preview": "vite preview"`.
- `frontend/src`: React 18 application with @xyflow/react canvas and STOMP WebSocket client.

### Established Patterns
- TypeScript strict mode (`strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`) enforced during `npm run build`.
- Vite manual chunk splitting configured in `frontend/vite.config.ts` for vendor bundles.

### Integration Points
- `frontend/dist/`: Target directory for compiled static assets served by Nginx in Phase 2.

</code_context>

<specifics>
## Specific Ideas

- The local environment already has Node 26 installed (`v26.13.0`).
- Ensure lockfile compatibility so that `npm ci` runs reliably in clean CI/container environments.

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope.

</deferred>

---

*Phase: 1-Frontend Toolchain & Build Verification*
*Context gathered: 2026-11-06*
