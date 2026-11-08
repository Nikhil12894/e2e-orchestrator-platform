---
phase: 03-execution-tracking-navigation-urls
plan: 01
subsystem: api
tags: [spring-boot, rest, navigation-urls, reverse-proxy, hypermedia, rfc-7231, location-header, typescript]

requires:
  - phase: 02-form-urlencoded-requests-end-to-end
    provides: form-urlencoded request execution and Karate feature generation
provides:
  - Dynamic navigation URLs (statusUrl, reportUrl, streamUrl) on execution trigger, details, and list endpoints
  - RFC 7231 Location response header on POST /api/v1/workflows/{id}/execute
  - Reverse proxy header normalization via server.forward-headers-strategy: framework
  - ExecutionNavigationUrlBuilder component for origin-aware URI resolution
  - Frontend TypeScript WorkflowExecution interface extensions
  - MockMvc integration test suite for navigation URLs and proxy handling
affects: [04-synchronous-execution-mode, 05-cicd-webhook-curl-modal]

actuals:
  tokens: 4467
  tasks: 3
  commits: 3

commits: 3
plan_head_before: e4016c9468a8effd2f3ea3f2589ec115135f9636
plan_head_after: 5523b20b5bf79fbd1847deb68fbf1305d1861060

tech-stack:
  added: []
  patterns: [Dynamic origin URI generation using ServletUriComponentsBuilder, RFC 7231 Location header injection, @Transient JPA Jackson READ_ONLY transport fields, Reverse proxy forwarded header normalization]

key-files:
  created:
    - backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java
    - backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java
  modified:
    - backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java
    - backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java
    - backend/src/main/resources/application.yml
    - frontend/src/types/workflow.ts

key-decisions:
  - "Used ServletUriComponentsBuilder.fromContextPath(request) with fallback to fromCurrentContextPath() in ExecutionNavigationUrlBuilder for dynamic origin resolution without hardcoded hosts"
  - "Decorated WorkflowExecutionEntity with @Transient and @JsonProperty(access = Access.READ_ONLY) to transport navigation URLs in REST responses without PostgreSQL schema migrations"
  - "Configured server.forward-headers-strategy: framework to activate ForwardedHeaderFilter so reverse proxies (X-Forwarded-Host, X-Forwarded-Proto) adapt origin URLs safely"
  - "Initialized MockMvc with MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(new ForwardedHeaderFilter()) to guarantee proxy filter coverage under Spring Boot 4.1.2 test starter"

patterns-established:
  - "ExecutionNavigationUrlBuilder as centralized origin builder for all hypermedia execution URLs"
  - "Spring ForwardedHeaderFilter proxy normalization for external cURL and CI/CD callers"

requirements-completed:
  - EXEC-01
  - EXEC-02
  - EXEC-03

coverage:
  - id: D1
    description: "POST /api/v1/workflows/{id}/execute returns HTTP 202 Accepted with RFC 7231 Location header matching statusUrl and response body containing absolute statusUrl, reportUrl, and streamUrl"
    requirement: "EXEC-01, EXEC-02"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java#testTriggerExecutionReturnsLocationHeaderAndUrls"
        status: pass
    human_judgment: false
  - id: D2
    description: "GET /api/v1/executions/{id} returns absolute statusUrl, streamUrl, and populated reportUrl for completed runs"
    requirement: "EXEC-01, EXEC-03"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java#testGetExecutionDetailsContainsReportUrl"
        status: pass
    human_judgment: false
  - id: D3
    description: "Reverse proxy headers (X-Forwarded-Host, X-Forwarded-Proto) dynamically adapt navigation URLs and Location header"
    requirement: "EXEC-01, EXEC-02"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java#testForwardedHeadersAdaptNavigationUrls"
        status: pass
    human_judgment: false
  - id: D4
    description: "GET /api/v1/executions enriches all executions in the list with navigation URLs"
    requirement: "EXEC-01"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java#testGetAllExecutionsContainsNavigationUrls"
        status: pass
    human_judgment: false
  - id: D5
    description: "Frontend WorkflowExecution TypeScript interface accepts optional statusUrl, reportUrl, streamUrl with 0 compiler errors"
    requirement: "EXEC-01"
    verification:
      - kind: other
        ref: "npm --prefix frontend run build"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-11-08
status: complete
---

# Phase 03 Plan 01: Wave 1 Execution Tracking Navigation URLs Summary

**First-class execution navigation URLs (`statusUrl`, `reportUrl`, `streamUrl`) and RFC 7231 `Location` response headers dynamically adapted to reverse proxy headers across execution trigger, details, and list endpoints.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-11-08T00:26:34Z
- **Completed:** 2026-11-08T00:38:40Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Implemented `ExecutionNavigationUrlBuilder` component using Spring's `ServletUriComponentsBuilder` to resolve absolute status, stream, and report URLs from incoming request contexts without hardcoded hostnames.
- Enriched `WorkflowExecutionEntity` with non-persistent `@Transient` fields (`statusUrl`, `reportUrl`, `streamUrl`) marked `@JsonProperty(access = Access.READ_ONLY)` ensuring zero database schema changes while providing seamless JSON serialization.
- Updated `WorkflowExecutionController` to return RFC 7231-compliant `Location` headers and enriched bodies on `POST /api/v1/workflows/{id}/execute`, and enriched entity outputs on `GET /api/v1/executions/{id}` and `GET /api/v1/executions`.
- Configured `server.forward-headers-strategy: framework` in `application.yml` to normalize `X-Forwarded-Host`, `X-Forwarded-Proto`, and `X-Forwarded-For` proxy headers.
- Extended the frontend `WorkflowExecution` TypeScript interface with optional navigation URL properties, verified with 0 type errors across all visual canvas and modal components.
- Added comprehensive integration tests in `WorkflowExecutionNavigationUrlTest.java` verifying EXEC-01, EXEC-02, EXEC-03, and proxy header normalization with 4/4 tests passing.

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end execution trigger with navigation URLs and Location header** - `69d2519` (feat)
2. **Task 2: Expand navigation URLs to execution details, list endpoints, and reverse proxy support** - `855b718` (feat)
3. **Task 3: Update frontend WorkflowExecution model and verify platform build** - `5523b20` (feat)

## Files Created/Modified

- `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java` - Spring component for dynamic navigation URL and Location header construction.
- `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java` - Added transient `statusUrl`, `reportUrl`, and `streamUrl` properties and getters/setters.
- `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` - Injected `ExecutionNavigationUrlBuilder`, added `Location` header to `POST .../execute`, and enriched `GET .../executions/{id}` and `GET .../executions`.
- `backend/src/main/resources/application.yml` - Configured `server.forward-headers-strategy: framework`.
- `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` - Integration tests for execution trigger, details, list, and proxy forwarded headers.
- `frontend/src/types/workflow.ts` - Added optional `statusUrl`, `reportUrl`, and `streamUrl` properties to `WorkflowExecution` interface.

## Decisions Made

- Used `ServletUriComponentsBuilder.fromContextPath(request)` with fallback to `fromCurrentContextPath()` in `ExecutionNavigationUrlBuilder` for dynamic origin resolution without hardcoded hosts.
- Decorated `WorkflowExecutionEntity` with `@Transient` and `@JsonProperty(access = Access.READ_ONLY)` to transport navigation URLs in REST responses without PostgreSQL schema migrations.
- Configured `server.forward-headers-strategy: framework` to activate `ForwardedHeaderFilter` so reverse proxies (`X-Forwarded-Host`, `X-Forwarded-Proto`) adapt origin URLs safely.
- Initialized MockMvc with `MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(new ForwardedHeaderFilter())` to guarantee proxy filter coverage under Spring Boot 4.1.2 test starter.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Issue] MockMvc initialization under Spring Boot 4.1.2**
- **Found during:** Task 1 (MockMvc test execution)
- **Issue:** `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` package was not present on the test classpath under Spring Boot 4.1.2 starter-test.
- **Fix:** Initialized `MockMvc` using Spring Test's `MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(new ForwardedHeaderFilter()).build()`, which cleanly integrates the web context and proxy filter.
- **Files modified:** `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java`
- **Verification:** `mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml` executed cleanly with all tests passing.
- **Committed in:** `69d2519` (Task 1 commit)

**2. [Rule 1 - Bug] JPA Entity ID assignment on pre-persisted test execution**
- **Found during:** Task 2 (MockMvc test execution for execution details endpoint)
- **Issue:** Manually setting `id` on a new `WorkflowExecutionEntity` prior to `save()` caused Hibernate to attempt an SQL update on a nonexistent row, triggering `ObjectOptimisticLockingFailure`.
- **Fix:** Removed manual `setId()` call in test setup, allowing Hibernate's `@PrePersist` hook to generate the UUID upon save.
- **Files modified:** `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java`
- **Verification:** `mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml` executed with 4/4 tests passing.
- **Committed in:** `855b718` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking issue, 1 bug)
**Impact on plan:** Both auto-fixes were confined to test scaffolding and prevented test runtime failures. Zero production scope creep.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Navigation URLs and Location response headers are fully functional and verified across backend and frontend models.
- Ready for Phase 4: Synchronous Execution Mode (`?wait=true` and timeout handling).

## Self-Check: PASSED
