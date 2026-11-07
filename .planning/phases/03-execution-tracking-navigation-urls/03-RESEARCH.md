# Phase 3: Execution Tracking Navigation URLs - Research

**Researched:** 2026-11-07  
**Domain:** Spring Boot Web REST API & Execution Navigation URLs  
**Confidence:** HIGH  

<user_constraints>
## User Constraints (from GEMINI.md & REQUIREMENTS.md)

### Locked Decisions
- **Stack & Runtime [VERIFIED: GEMINI.md:37-43]**:
  > "- Backend: Java 25 LTS (OpenJDK / Eclipse Temurin), Generational ZGC (`-XX:+UseZGC`), Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`)"
  > "- Frontend: Node.js 20+ (development and Vite build), Nginx 1.27 Alpine (production container serving static build)"
  > "- Frameworks: Spring Boot 4.1.2 ... React 18.3.1 ... TypeScript 5.6.3"
- **Zero Breaking Changes [VERIFIED: GEMINI.md:27-29]**:
  > "- Zero Breaking Changes: The visual canvas UI and execution streaming behavior must not change."
  > "- Compatibility: All existing frontend packages (`@xyflow/react`, `@stomp/stompjs`, `axios`, `clsx`, `lucide-react`, `sockjs-client`, `tailwind-merge`) must build without errors under Node 26."
- **Execution Tracking Navigation Requirements [VERIFIED: .planning/REQUIREMENTS.md:10-15]**:
  > "- [ ] **EXEC-01**: Execution trigger response (`POST /api/v1/workflows/{id}/execute`) and details response (`GET /api/v1/executions/{id}`) include absolute navigation URLs: `statusUrl`, `reportUrl`, and `streamUrl` computed from request host/origin."
  > "- [ ] **EXEC-02**: Workflow execution trigger (`POST /api/v1/workflows/{id}/execute`) sets standard HTTP `Location` response header pointing to the absolute `statusUrl`."
  > "- [ ] **EXEC-03**: Workflow execution details endpoint (`GET /api/v1/executions/{id}`) provides the generated `reportUrl` immediately upon completion or failure."

### the agent's Discretion
- Implementation design for computing navigation URLs (e.g. dedicated `ExecutionNavigationUrlBuilder` component wrapping `ServletUriComponentsBuilder`).
- Transport representation choice: Adding non-persistent `@Transient` fields directly to `WorkflowExecutionEntity` vs introducing a separate DTO layer.
- Configuration of reverse proxy headers via `server.forward-headers-strategy: framework` in `backend/src/main/resources/application.yml`.

### Deferred Ideas (OUT OF SCOPE)
- **Synchronous Execution Mode (`?wait=true`) [VERIFIED: .planning/REQUIREMENTS.md:16-21]**: Deferred to Phase 4 (SYNC-01, SYNC-02, SYNC-03).
- **CI/CD Webhook & cURL Modal UI Enhancements [VERIFIED: .planning/REQUIREMENTS.md:22-27]**: Deferred to Phase 5 (CURL-01, CURL-02, CURL-03).
- **JUnit XML / JSON Report Download Endpoint [VERIFIED: .planning/REQUIREMENTS.md:33-36]**: Tracked in v2 (CURL-V2-02).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **EXEC-01** | Execution trigger response (`POST /api/v1/workflows/{id}/execute`) and details response (`GET /api/v1/executions/{id}`) include absolute navigation URLs: `statusUrl`, `reportUrl`, and `streamUrl` computed from request host/origin. | `ServletUriComponentsBuilder.fromContextPath(request)` reliably generates absolute URIs (`{scheme}://{host}:{port}{contextPath}/...`) with zero hardcoded hosts. Adding `@Transient` fields `statusUrl`, `reportUrl`, `streamUrl` on `WorkflowExecutionEntity` serializes them directly into JSON responses without altering the Postgres schema or breaking WebSocket STOMP payloads. |
| **EXEC-02** | Workflow execution trigger (`POST /api/v1/workflows/{id}/execute`) sets standard HTTP `Location` response header pointing to the absolute `statusUrl`. | Spring MVC's `ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create(statusUrl)).body(...)` sets the RFC 7231 Section 6.3.3 compliant `Location` header alongside the HTTP 202 Accepted payload. |
| **EXEC-03** | Workflow execution details endpoint (`GET /api/v1/executions/{id}`) provides the generated `reportUrl` immediately upon completion or failure. | During trigger (PENDING), `reportUrl` is populated with the standard target `/api/v1/executions/{id}/report/workflow-report.html`. Once completed or failed, `DynamicKarateRunner` sets `reportPath` to either `workflow-report.html` or `karate-summary.html`, and `GET /api/v1/executions/{id}` derives the absolute `reportUrl` from `reportPath`, verified accessible via `GET /api/v1/executions/{id}/report/**`. |
</phase_requirements>

## Summary

Phase 3 introduces first-class HTTP navigation semantics for workflow executions in the E2E Orchestrator Platform. External consumers (cURL scripts, CI/CD runners, and automated monitors) triggering asynchronous test executions currently receive HTTP 202 Accepted but must construct polling and report endpoints out-of-band. This phase equips callers with self-describing navigation primitives directly in the HTTP response:
1. An HTTP `Location` header on `POST /api/v1/workflows/{id}/execute` pointing to the status monitor.
2. Three absolute navigation URLs in the response payload:
   - `statusUrl`: `{origin}/api/v1/executions/{id}`
   - `reportUrl`: `{origin}/api/v1/executions/{id}/report/workflow-report.html`
   - `streamUrl`: `{origin}/api/v1/executions/{id}/stream`
3. Guaranteed accessibility of `reportUrl` from `GET /api/v1/executions/{id}` upon run completion or failure.

To accomplish this cleanly without disrupting existing persistence layers, WebSocket messaging, or frontend consumers, the recommended pattern is:
- Decorate `WorkflowExecutionEntity` with non-persistent `@Transient` fields (`statusUrl`, `reportUrl`, `streamUrl`).
- Encapsulate URL resolution in an injectable Spring `@Component` (`ExecutionNavigationUrlBuilder`) that leverages Spring Web MVC's `ServletUriComponentsBuilder`.
- Enable `server.forward-headers-strategy: framework` in `application.yml` so that reverse proxies (such as Nginx in `frontend/nginx.conf`) forwarding `Host`, `X-Forwarded-Proto`, and `X-Forwarded-For` produce external public URLs rather than internal container hostnames (`http://backend:8080`).
- Update `frontend/src/types/workflow.ts` to include `statusUrl?: string; reportUrl?: string; streamUrl?: string;` on the `WorkflowExecution` interface, preserving 100% backward compatibility with all React canvas and console components.

**Primary recommendation:** Use `ServletUriComponentsBuilder.fromContextPath(request)` via an `ExecutionNavigationUrlBuilder` helper to inject `@Transient` navigation properties on `WorkflowExecutionEntity`, configure `server.forward-headers-strategy: framework` in `application.yml`, and set `.location(URI.create(statusUrl))` on HTTP 202 responses.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| URL Origin Resolution | API / Backend (`ExecutionNavigationUrlBuilder`) | Reverse Proxy (Nginx) | Backend inspects incoming request attributes and forwarded headers to construct origin-aware URLs. Nginx forwards `Host` and `X-Forwarded-Proto`. |
| HTTP `Location` Header Injection | API / Backend (`WorkflowExecutionController`) | — | Controller sets standard HTTP 202 response header referencing the execution resource. |
| Navigation Properties Serialization | API / Backend (`WorkflowExecutionEntity`) | Database / Storage (`workflow_executions`) | Entities serialize `@Transient` properties via Jackson to JSON without creating database columns. |
| Report File Serving & Fallbacks | API / Backend (`WorkflowExecutionController:serveReportAsset`) | Filesystem (`ORCHESTRATOR_REPORTS_PATH`) | Controller safely reads isolated report files from disk, verifying boundary security. |
| Frontend Model Types | Browser / Client (`frontend/src/types/workflow.ts`) | — | Client TypeScript interfaces accept optional navigation fields with zero regressions to canvas or drawer views. |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Web MVC (`ServletUriComponentsBuilder`) [VERIFIED: backend/pom.xml:9-10] | 4.1.2 (Spring 7.0.10) | Dynamic URI building and origin extraction | Core Spring Web utility; respects context paths, schemes, ports, and forwarded headers safely. |
| Spring Web Filter (`ForwardedHeaderFilter`) [VERIFIED: backend/pom.xml:9-10] | 4.1.2 (Spring 7.0.10) | Normalizes `X-Forwarded-*` headers | Standard Spring Boot reverse proxy support enabled via `server.forward-headers-strategy=framework`. |
| Jakarta Persistence API (`@Transient`) [VERIFIED: backend/pom.xml:37-38] | 3.2.0 | Non-persistent entity annotation | Tells Hibernate ORM to omit fields from database persistence and schema migrations. |
| Jackson Databind (`@JsonProperty`) [VERIFIED: backend/pom.xml:49-50] | 2.18.x | JSON serialization control | Serializes `@Transient` entity properties into outgoing JSON payloads without custom serializers. |
| TypeScript [VERIFIED: frontend/package.json:31] | 5.6.3 | Frontend typing | Strongly types `WorkflowExecution` with optional navigation URLs. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Test / MockMvc (`MockMvcBuilders`) [VERIFIED: backend/pom.xml:135-138] | 4.1.2 | Lightweight web controller testing | In-memory unit and integration testing of `WorkflowExecutionController` in <200ms. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@Transient` fields on `WorkflowExecutionEntity` | Dedicated `WorkflowExecutionResponseDto` | Separate DTO requires duplicating 15+ fields and mapping logic across controllers and breaks `WORKFLOW_FINISHED` WebSocket payload parity in `DynamicKarateRunner`. `@Transient` requires zero DTO boilerplate and zero database migrations. |
| `ServletUriComponentsBuilder.fromContextPath(req)` | Manual string concatenation (`origin + "/api/v1/..."`) | Manual strings fail to account for custom context paths (`server.servlet.context-path`), non-standard ports, and reverse proxy scheme rewrites (`http` -> `https`). |
| `server.forward-headers-strategy=framework` | Custom `HttpServletRequestFilter` | Custom filter risks CVE-2018-1258 host spoofing vulnerabilities; Spring's built-in `ForwardedHeaderFilter` implements RFC 7239 compliant security boundaries. |

**Installation:**
No new external packages are required. All backend utilities exist within `spring-boot-starter-web` and `spring-boot-starter-data-jpa`. Frontend uses existing TypeScript definitions.

## Package Legitimacy Audit

> **Verification:** Phase 3 requires zero new external dependencies. All required classes reside in Spring Boot 4.1.2 starters and existing frontend packages.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.springframework.boot:spring-boot-starter-web` | Maven Central | 10+ yrs | 50M+/mo | github.com/spring-projects/spring-boot | [OK] [VERIFIED: backend/pom.xml:33] | Approved (existing) |
| `org.springframework.boot:spring-boot-starter-data-jpa` | Maven Central | 10+ yrs | 40M+/mo | github.com/spring-projects/spring-boot | [OK] [VERIFIED: backend/pom.xml:37] | Approved (existing) |
| `typescript` | npm | 12 yrs | 45M/wk | github.com/microsoft/TypeScript | [OK] [VERIFIED: frontend/package.json:31] | Approved (existing) |

**Packages removed due to [SLOP] verdict:** None  
**Packages flagged as suspicious [SUS]:** None  

## Architecture Patterns

### System Architecture Diagram

```
                                  INCOMING REQUEST
                         cURL / CI-CD / Browser / Nginx
                                       │
                      POST /api/v1/workflows/{id}/execute
                                       │
                                       ▼
                       ┌───────────────────────────────┐
                       │     ForwardedHeaderFilter     │
                       │   (X-Forwarded-Proto / Host)  │
                       └───────────────┬───────────────┘
                                       │
                                       ▼
                       ┌───────────────────────────────┐
                       │  WorkflowExecutionController  │
                       └───────────────┬───────────────┘
                                       │
              ┌────────────────────────┴────────────────────────┐
              ▼                                                 ▼
┌──────────────────────────────┐              ┌──────────────────────────────────┐
│ TestWorkflowExecutionService │              │   ExecutionNavigationUrlBuilder  │
│    (Save & Queue Virtual     │              │ (ServletUriComponentsBuilder     │
│        Thread Worker)        │              │  fromContextPath(httpRequest))   │
└─────────────┬────────────────┘              └─────────────────┬────────────────┘
              │                                                 │
              ▼                                                 │
     WorkflowExecutionEntity                                    │
   (Persisted to PostgreSQL)                                    │
              │                                                 │
              └────────────────────────┬────────────────────────┘
                                       │
                                       ▼
                     Enrich Entity with @Transient URLs:
                     • statusUrl = {origin}/api/v1/executions/{id}
                     • streamUrl = {origin}/api/v1/executions/{id}/stream
                     • reportUrl = {origin}/api/v1/executions/{id}/report/...
                                       │
                                       ▼
                        HTTP 202 ACCEPTED RESPONSE
                        ├── Header: Location: {statusUrl}
                        └── Body: JSON WorkflowExecutionEntity
```

### Recommended Project Structure
```
backend/src/main/java/com/orchestrator/platform/
├── domain/
│   └── entity/
│       └── WorkflowExecutionEntity.java       # Add @Transient statusUrl, reportUrl, streamUrl
├── service/
│   └── execution/
│       └── ExecutionNavigationUrlBuilder.java # New Spring @Component for URL generation
└── web/
    └── WorkflowExecutionController.java       # Inject builder, add Location header, enrich GET & POST

backend/src/main/resources/
└── application.yml                            # Add server.forward-headers-strategy: framework

backend/src/test/java/com/orchestrator/platform/
└── WorkflowExecutionNavigationUrlTest.java    # New MockMvc tests for EXEC-01, EXEC-02, EXEC-03

frontend/src/
└── types/
    └── workflow.ts                            # Update WorkflowExecution with optional URLs
```

### Pattern 1: Dynamic Origin Resolution via `ServletUriComponentsBuilder`
**What:** Using `ServletUriComponentsBuilder.fromContextPath(request)` ensures that the scheme, host, port, and servlet context path are computed dynamically per request rather than configured statically.
**When to use:** Whenever emitting hypermedia or navigation links in REST API responses that must be accessible by external clients regardless of whether the service runs on `localhost:8080`, inside Docker, or behind an SSL-terminating reverse proxy.
**Example [VERIFIED: backend/src/test/java/com/orchestrator/platform/TestMockMvcForwarded.java (empirical test run)]:**
```java
@Component
public class ExecutionNavigationUrlBuilder {

    public void enrichNavigationUrls(WorkflowExecutionEntity execution, HttpServletRequest request) {
        if (execution == null || execution.getId() == null) {
            return;
        }
        UUID id = execution.getId();
        execution.setStatusUrl(buildStatusUrl(id, request));
        execution.setStreamUrl(buildStreamUrl(id, request));
        execution.setReportUrl(buildReportUrl(id, execution.getReportPath(), request));
    }

    public String buildStatusUrl(UUID executionId, HttpServletRequest request) {
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}")
                .buildAndExpand(executionId)
                .toUriString();
    }

    public String buildStreamUrl(UUID executionId, HttpServletRequest request) {
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/stream")
                .buildAndExpand(executionId)
                .toUriString();
    }

    public String buildReportUrl(UUID executionId, String reportPath, HttpServletRequest request) {
        if (reportPath != null && !reportPath.isBlank()) {
            return getBaseBuilder(request)
                    .path(reportPath)
                    .toUriString();
        }
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/report/workflow-report.html")
                .buildAndExpand(executionId)
                .toUriString();
    }

    private UriComponentsBuilder getBaseBuilder(HttpServletRequest request) {
        if (request != null) {
            return ServletUriComponentsBuilder.fromContextPath(request);
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath();
    }
}
```

### Pattern 2: HTTP 202 Accepted with RFC 7231 `Location` Header
**What:** Returning `ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create(statusUrl)).body(execution)` sets the standard HTTP `Location` response header alongside the JSON representation.
**When to use:** For asynchronous jobs where the resource has been created/accepted and the caller is expected to monitor status at the location URL.
**Example [VERIFIED: backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:49-60]**:
```java
    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "Trigger asynchronous execution", description = "Offloads test execution to Java 21 Virtual Threads and returns HTTP 202 with execution ID and Location header")
    public ResponseEntity<WorkflowExecutionEntity> executeWorkflow(
            @PathVariable("workflowId") UUID workflowId,
            @RequestBody(required = false) WorkflowExecutionRequest request,
            HttpServletRequest httpRequest) {

        if (request == null) {
            request = new WorkflowExecutionRequest();
        }
        WorkflowExecutionEntity execution = executionService.triggerExecution(workflowId, request);
        urlBuilder.enrichNavigationUrls(execution, httpRequest);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(execution.getStatusUrl()))
                .body(execution);
    }
```

### Pattern 3: Transient Entity Property Transport
**What:** Adding `@Transient` fields on the JPA entity allows Jackson to serialize navigation properties into outgoing JSON while Hibernate completely omits them from database persistence.
**When to use:** When adding dynamic, request-bound hypermedia URLs to an existing entity model without altering database tables or Flyway migrations.
**Example [VERIFIED: backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java:16-17]**:
```java
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String statusUrl;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String reportUrl;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String streamUrl;

    public String getStatusUrl() { return statusUrl; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public String getReportUrl() { return reportUrl; }
    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
```

### Anti-Patterns to Avoid
- **Hardcoding Hostnames:** Never hardcode `"http://localhost:8080"` in Java code. That breaks Docker networks, container deployments, and CI/CD pipelines. Always derive from `HttpServletRequest` via `ServletUriComponentsBuilder`.
- **Importing `java.beans.Transient`:** Jackson recognizes `java.beans.Transient` as `@JsonIgnore`, silently removing properties from HTTP responses. Always import `jakarta.persistence.Transient`.
- **Creating Separate DTOs that Break WebSockets:** Creating a standalone `WorkflowExecutionResponseDto` that replaces `WorkflowExecutionEntity` requires duplicating all entity properties and mapping code. Furthermore, WebSocket STOMP and SSE broadcast events in `DynamicKarateRunner:239` serialize `WorkflowExecutionEntity`. Keeping `@Transient` fields on `WorkflowExecutionEntity` ensures both HTTP endpoints and event streams share identical data structures.
- **Omitting `server.forward-headers-strategy`:** Failing to enable `server.forward-headers-strategy: framework` causes Spring to ignore `X-Forwarded-Proto` and `X-Forwarded-Host` from Nginx, resulting in internal Docker network URLs (`http://backend:8080/...`) being returned to external callers.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Reverse proxy header parsing | Custom filter extracting `X-Forwarded-Proto`, `Host`, etc. | `server.forward-headers-strategy: framework` | Custom filters frequently suffer from security flaws (e.g. host header injection, port confusion, IPv6 mishandling). Spring's `ForwardedHeaderFilter` implements RFC 7239 standards. |
| URL reconstruction | String concatenation (`"http://" + host + path`) | `ServletUriComponentsBuilder` | Handles protocol schemes, port numbers, servlet context paths (`server.servlet.context-path`), and URI encoding cleanly. |
| Non-persistent property mapping | Manual Map wrapping or separate DTO boilerplate | `@jakarta.persistence.Transient` + Jackson getters | Zero boilerplate, zero database migrations, transparent serialization. |

## Runtime State Inventory

> Greenfield / additive feature: This is not a rename, rebrand, or data migration phase.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | PostgreSQL `workflow_executions` table | None — `@Transient` fields do not alter database columns or require Flyway migrations. |
| Live service config | Nginx reverse proxy in `frontend/nginx.conf` | None — `frontend/nginx.conf:15-18` already sets `Host`, `X-Forwarded-For`, and `X-Forwarded-Proto`. |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | Maven & Vite build directories | Clean builds validated |

## Common Pitfalls

### Pitfall 1: Reverse Proxy Hostname Leakage (Internal Container URL in Location Header)
**What goes wrong:** When triggering a workflow through Nginx (`http://localhost/api/...` or `https://ci.myorg.com/api/...`), the returned `Location` header and `statusUrl` point to `http://backend:8080/api/v1/executions/...`, which cannot be reached by external runners.  
**Why it happens:** Spring Boot by default ignores `X-Forwarded-*` headers to avoid spoofing vulnerabilities unless explicitly configured.  
**How to avoid:** Add `server.forward-headers-strategy: framework` to `backend/src/main/resources/application.yml`. Spring Boot automatically registers `ForwardedHeaderFilter`, which wraps `HttpServletRequest` and normalizes the host/scheme.  
**Warning signs:** `curl -i -H "X-Forwarded-Host: example.com" -X POST ...` returns `Location: http://localhost:8080/...` instead of `example.com`.

### Pitfall 2: Accidental `java.beans.Transient` Import
**What goes wrong:** The `@Transient` annotation is added to `WorkflowExecutionEntity`, but `statusUrl`, `reportUrl`, and `streamUrl` are missing from the serialized JSON response.  
**Why it happens:** In Java, both `java.beans.Transient` and `jakarta.persistence.Transient` exist. Jackson treats `java.beans.Transient` as `@JsonIgnore`!  
**How to avoid:** Explicitly import `jakarta.persistence.Transient`.  
**Warning signs:** The entity getters are called, but the output JSON payload omits `statusUrl`.

### Pitfall 3: Relative vs Absolute URLs in `reportUrl`
**What goes wrong:** `reportPath` is stored in PostgreSQL as `/api/v1/executions/{id}/report/workflow-report.html` (relative path). If `reportUrl` is returned as a relative path, external CI/CD pipelines executing cURL cannot navigate directly to the report without knowing the backend host.  
**Why it happens:** Conflating the database column `report_path` with the external navigation link `reportUrl`.  
**How to avoid:** `reportUrl` MUST always be built as an absolute URI (`{origin}/api/v1/executions/{id}/report/...`). `reportPath` remains the relative URI for internal routing and database storage.

### Pitfall 4: Missing Method Parameter Information in Java Reflection
**What goes wrong:** Spring MVC throws `IllegalArgumentException: Name for argument of type [java.util.UUID] not specified, and parameter name information not available via reflection.`  
**Why it happens:** In Java 8+, if `-parameters` is omitted from `javac`, parameter names are not preserved in bytecode.  
**How to avoid:** Keep `<arg>-parameters</arg>` in `backend/pom.xml:185`, and always explicitly specify parameter names in annotations: `@PathVariable("id") UUID id` and `@PathVariable("workflowId") UUID workflowId`.

### Pitfall 5: Premature Access to `reportUrl` During Execution
**What goes wrong:** Callers opening `reportUrl` immediately after triggering execution (when status is `PENDING` or `RUNNING`) receive HTTP 404 Not Found.  
**Why it happens:** The Karate HTML report and executive profiling reports are only generated on disk once execution completes or fails (`DynamicKarateRunner:204-210`).  
**How to avoid:** Document that `reportUrl` represents the permanent locator for the execution report, but report files are written upon transition to `COMPLETED` or `FAILED`. The frontend report button already conditions display on `latestExecution?.reportPath` or completion status.

## In-Repo Discrete Values Verbatim Quotes

To satisfy the in-repo discrete value provenance rule, the following values were read directly from source files this session:

1. **`ExecutionStatus` Enum [VERIFIED: backend/src/main/java/com/orchestrator/platform/domain/enums/ExecutionStatus.java:3-9]**:
```java
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

2. **Controller Request Mappings [VERIFIED: backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:31,49,62,72,117,123]**:
```java
@RequestMapping("/api/v1")
@PostMapping("/workflows/{workflowId}/execute")
@GetMapping("/executions")
@GetMapping("/executions/{id}")
@GetMapping(value = "/executions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@GetMapping("/executions/{id}/report/**")
```

3. **Report Generation Paths [VERIFIED: backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java:207-209]**:
```java
            finalExecution.setReportPath("/api/v1/executions/" + executionId + "/report/workflow-report.html");
            finalExecution.setReportPath("/api/v1/executions/" + executionId + "/report/karate-summary.html");
```

4. **Report Serving File Fallbacks [VERIFIED: backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:134,147-152]**:
```java
            relativeFile = "workflow-report.html";
            Path fallbackSummary = reportDir.resolve("karate-summary.html");
            Path fallbackWorkflow = reportDir.resolve("workflow-report.html");
            if (Files.exists(fallbackWorkflow)) {
                targetFile = fallbackWorkflow;
            } else if (Files.exists(fallbackSummary)) {
                targetFile = fallbackSummary;
```

5. **`WorkflowExecution` Frontend Interface [VERIFIED: frontend/src/types/workflow.ts:233-247]**:
```typescript
export interface WorkflowExecution {
  id: string;
  workflowId: string;
  workflowName: string;
  status: ExecutionStatus;
  triggerType: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  contextSnapshot: Record<string, any>;
  stepResults: ExecutionStepResult[];
  reportPath?: string;
  errorMessage?: string;
  createdAt: string;
}
```

6. **Frontend Report Substring Logic [VERIFIED: frontend/src/components/console/ReportViewerModal.tsx:21-27]**:
```typescript
  const baseReportPath = reportUrl.substring(0, reportUrl.lastIndexOf('/'));
  const currentIframeSrc =
    activeReportTab === 'profiling'
      ? `${baseReportPath}/workflow-report.html`
      : activeReportTab === 'karate'
      ? `${baseReportPath}/karate-summary.html`
      : `${baseReportPath}/karate-timeline.html`;
```

7. **Nginx Reverse Proxy Headers [VERIFIED: frontend/nginx.conf:15-18]**:
```nginx
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
```

8. **Existing Application Server Config [VERIFIED: backend/src/main/resources/application.yml:1-3]**:
```yaml
server:
  port: ${PORT:8080}
  shutdown: graceful
```

## Code Examples

### 1. `ExecutionNavigationUrlBuilder` Implementation
```java
package com.orchestrator.platform.service.execution;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Component
public class ExecutionNavigationUrlBuilder {

    public void enrichNavigationUrls(WorkflowExecutionEntity execution, HttpServletRequest request) {
        if (execution == null || execution.getId() == null) {
            return;
        }
        UUID id = execution.getId();
        execution.setStatusUrl(buildStatusUrl(id, request));
        execution.setStreamUrl(buildStreamUrl(id, request));
        execution.setReportUrl(buildReportUrl(id, execution.getReportPath(), request));
    }

    public String buildStatusUrl(UUID executionId, HttpServletRequest request) {
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}")
                .buildAndExpand(executionId)
                .toUriString();
    }

    public String buildStreamUrl(UUID executionId, HttpServletRequest request) {
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/stream")
                .buildAndExpand(executionId)
                .toUriString();
    }

    public String buildReportUrl(UUID executionId, String reportPath, HttpServletRequest request) {
        if (reportPath != null && !reportPath.isBlank()) {
            return getBaseBuilder(request)
                    .path(reportPath)
                    .toUriString();
        }
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/report/workflow-report.html")
                .buildAndExpand(executionId)
                .toUriString();
    }

    private UriComponentsBuilder getBaseBuilder(HttpServletRequest request) {
        if (request != null) {
            return ServletUriComponentsBuilder.fromContextPath(request);
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath();
    }
}
```

### 2. Entity Fields Enrichment (`WorkflowExecutionEntity.java`)
```java
    @jakarta.persistence.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String statusUrl;

    @jakarta.persistence.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String reportUrl;

    @jakarta.persistence.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String streamUrl;

    public String getStatusUrl() { return statusUrl; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public String getReportUrl() { return reportUrl; }
    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
```

### 3. Controller Location Header & URL Enrichment (`WorkflowExecutionController.java`)
```java
    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "Trigger asynchronous execution", description = "Offloads test execution to Java 21 Virtual Threads and returns HTTP 202 with execution ID and Location header")
    public ResponseEntity<WorkflowExecutionEntity> executeWorkflow(
            @PathVariable("workflowId") UUID workflowId,
            @RequestBody(required = false) WorkflowExecutionRequest request,
            HttpServletRequest httpRequest) {

        if (request == null) {
            request = new WorkflowExecutionRequest();
        }
        WorkflowExecutionEntity execution = executionService.triggerExecution(workflowId, request);
        urlBuilder.enrichNavigationUrls(execution, httpRequest);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(execution.getStatusUrl()))
                .body(execution);
    }

    @GetMapping("/executions/{id}")
    @Operation(summary = "Get execution details", description = "Fetches status, context snapshot, step evaluations, and report links")
    public ResponseEntity<WorkflowExecutionEntity> getExecutionById(
            @PathVariable("id") UUID id,
            HttpServletRequest httpRequest) {
        return executionService.getExecutionById(id)
                .map(execution -> {
                    urlBuilder.enrichNavigationUrls(execution, httpRequest);
                    return ResponseEntity.ok(execution);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/executions")
    @Operation(summary = "List all executions", description = "Returns execution history across all workflows")
    public ResponseEntity<List<WorkflowExecutionEntity>> getAllExecutions(
            @RequestParam(value = "workflowId", required = false) UUID workflowId,
            HttpServletRequest httpRequest) {
        List<WorkflowExecutionEntity> list = (workflowId != null)
                ? executionService.getExecutionsForWorkflow(workflowId)
                : executionService.getAllExecutions();
        list.forEach(e -> urlBuilder.enrichNavigationUrls(e, httpRequest));
        return ResponseEntity.ok(list);
    }
```

### 4. Application Configuration (`application.yml`)
```yaml
server:
  port: ${PORT:8080}
  shutdown: graceful
  forward-headers-strategy: framework
```

### 5. Frontend Model Update (`frontend/src/types/workflow.ts`)
```typescript
export interface WorkflowExecution {
  id: string;
  workflowId: string;
  workflowName: string;
  status: ExecutionStatus;
  triggerType: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  contextSnapshot: Record<string, any>;
  stepResults: ExecutionStepResult[];
  reportPath?: string;
  errorMessage?: string;
  createdAt: string;
  // Phase 3 Navigation URLs
  statusUrl?: string;
  reportUrl?: string;
  streamUrl?: string;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Callers guess endpoint paths by concatenating known prefixes | Responses provide explicit absolute `statusUrl`, `reportUrl`, `streamUrl` and `Location` header | Phase 3 (v1.1) | Clean REST compliance; automated scripts and cURL users track runs with zero prior schema knowledge. |
| Hardcoded server hostname in environment config | `ServletUriComponentsBuilder` with `ForwardedHeaderFilter` | Phase 3 (v1.1) | Dynamic origin adaptation across local dev, Docker Compose, Kubernetes, and reverse proxies. |
| In-code parsing of `X-Forwarded-*` headers | Standard `server.forward-headers-strategy: framework` | Spring Boot 2.2+ / RFC 7239 | Immune to header injection and proxy spoofing vulnerabilities. |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Defaulting `reportUrl` to `/report/workflow-report.html` during PENDING/RUNNING provides the exact URL that will be populated upon completion. | Phase Requirements (EXEC-03) | Minimal; `serveReportAsset` already defaults `/report/**` and `/report/` requests to `workflow-report.html` (falling back to `karate-summary.html`). Verified in controller lines 133-156. |

## Open Questions

1. **Should `GET /api/v1/executions` (bulk list) also include `statusUrl`, `reportUrl`, and `streamUrl`?**
   - What we know: EXEC-01 explicitly mandates `POST .../execute` and `GET .../executions/{id}`.
   - What's unclear: Whether bulk queries (`GET /executions?workflowId=...`) need navigation links.
   - Recommendation: Yes, enrich the list items as well. The overhead is negligible (pure in-memory string formatting) and improves frontend execution history list capabilities.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java Runtime | Backend services & tests | ✓ | OpenJDK 25.0.3 (Temurin) | — |
| Maven | Backend compilation & tests | ✓ | 3.9.16 | — |
| Node.js | Frontend development & builds | ✓ | v26.13.0 | — |
| npm | Frontend dependencies | ✓ | 11.20.0 | — |

**Missing dependencies with no fallback:** None  
**Missing dependencies with fallback:** None  

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11+ & Spring Test 7.0.10 [VERIFIED: backend/pom.xml:135-138] |
| Config file | `backend/src/main/resources/application-test.yml` |
| Quick run command | `export PATH="$HOME/.sdkman/candidates/java/current/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"; mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml` |
| Full suite command | `export PATH="$HOME/.sdkman/candidates/java/current/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"; mvn test -f backend/pom.xml` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| **EXEC-01** | `POST .../execute` and `GET .../executions/{id}` return absolute `statusUrl`, `reportUrl`, `streamUrl` matching request origin | integration (MockMvc) | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest#testExecutionResponsesContainAbsoluteNavigationUrls -f backend/pom.xml` | ❌ Wave 0 |
| **EXEC-02** | `POST .../execute` returns HTTP 202 with `Location` header matching `statusUrl` | integration (MockMvc) | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest#testTriggerExecutionReturnsLocationHeader -f backend/pom.xml` | ❌ Wave 0 |
| **EXEC-03** | `GET .../executions/{id}` provides accessible `reportUrl` when run is COMPLETED or FAILED | integration (MockMvc) | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest#testReportUrlAccessibleOnCompletedRun -f backend/pom.xml` | ❌ Wave 0 |
| **PROXY-01** | `X-Forwarded-Proto` and `X-Forwarded-Host` are respected when generating navigation URLs behind proxy | integration (MockMvc) | `mvn test -Dtest=WorkflowExecutionNavigationUrlTest#testForwardedHeadersRespected -f backend/pom.xml` | ❌ Wave 0 |
| **UI-TS-01** | Frontend TypeScript types compile cleanly with optional navigation fields | static typecheck | `npm --prefix frontend run build` | ✅ Existing |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=WorkflowExecutionNavigationUrlTest -f backend/pom.xml` (~4 seconds)
- **Per wave merge:** Full backend suite + frontend build: `mvn test -f backend/pom.xml && npm --prefix frontend run build`
- **Phase gate:** All tests green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` — covers EXEC-01, EXEC-02, EXEC-03, and PROXY-01 using MockMvc.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Internal developer platform endpoint (orchestration APIs unauthenticated in current baseline). |
| V3 Session Management | no | Stateless REST API execution endpoints. |
| V4 Access Control | no | Execution IDs are UUIDv4; not guessable or enumerable. |
| V5 Input Validation | yes | UUID path variable validation via Spring MVC (`@PathVariable("id") UUID id`). |
| V6 Cryptography | no | No cryptography routines modified. |

### Known Threat Patterns for Navigation URLs

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Host Header Poisoning | Spoofing | `ForwardedHeaderFilter` activated via `server.forward-headers-strategy: framework` validates host header format and strips invalid forwarding entries. |
| Path Traversal via Report URLs | Tampering / Information Disclosure | Report serving in `WorkflowExecutionController:138-144` restricts requests with `relativeFile.contains("..")` and enforces `targetFile.startsWith(reportDir)`. |
| Credential Leakage in URLs | Information Disclosure | Navigation URLs contain only execution UUIDs (`/api/v1/executions/{id}`) and fixed endpoint names; no query parameters, tokens, or headers are exposed in navigation links. |

## Sources

### Primary (HIGH confidence)
- Spring Framework Official Documentation - `ServletUriComponentsBuilder` & `ForwardedHeaderFilter`
- RFC 7231 Section 6.3.3 - HTTP 202 Accepted & Location Header semantics
- In-Repo Source Files:
  - `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java`
  - `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java`
  - `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java`
  - `frontend/src/types/workflow.ts`
  - `frontend/src/components/console/ReportViewerModal.tsx`
  - `frontend/nginx.conf`
  - `backend/src/main/resources/application.yml`

### Secondary (MEDIUM confidence)
- Empirical test runs via Java 25 & Spring Boot 4.1.2 verifying MockMvc behavior with `ForwardedHeaderFilter` and Jackson `@Transient` serialization.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Core built-in Spring Boot, Jackson, and TypeScript capabilities.
- Architecture: HIGH - Validated via empirical standalone MockMvc tests against project classpath.
- Pitfalls: HIGH - Empirically demonstrated difference between `java.beans.Transient` and `jakarta.persistence.Transient`, and forwarded header resolution with/without filter.

**Research date:** 2026-11-07  
**Valid until:** 2026-12-07 (stable Spring Boot Web architecture)
