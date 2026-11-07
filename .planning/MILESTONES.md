# Milestones

## v1.0 Standalone DB Export & Form URL-Encoded Requests (Shipped: 2026-11-07)

**Phases completed:** 2 phases, 4 plans, 4 tasks

**Key accomplishments:**
- Standalone Karate feature exporter for database polling workflow steps, emitting self-contained inline JDBC functions (`dbQuery` and `dbPoll`) in the feature `Background:`, retry loops with configurable timing, compound condition evaluation safeguards, step-level column match assertions, and downstream variable extraction.
- Standalone Karate feature execution verification running generated database polling features directly through `io.karatelabs.core.Runner.path` against in-memory H2 database, proving downstream variable chaining into subsequent HTTP requests, muted step handling, bounded timeout termination, and zero platform classpath dependencies.
- Implemented backend AST model extension (bodyType, formData), dynamic Karate execution with native form fields keyword, mock /api/v1/mock/auth/token endpoint, and standalone Karate .feature export formatting dynamic variable expressions.
- Implemented frontend TypeScript data models (bodyType, formData), segmented Request Body Format selector, interactive key-value table editor with ${var} selector insertion, duplicate key inline warnings, and header auto-synchronization in NodeConfigDrawer.tsx.

---
