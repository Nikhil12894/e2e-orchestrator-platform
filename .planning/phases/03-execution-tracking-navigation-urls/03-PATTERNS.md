# Phase 03: Execution Tracking Navigation URLs - Pattern Map

**Mapped:** 2026-11-07  
**Files analyzed:** 6 (2 new, 4 modified)  
**Analogs found:** 6 / 6  

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java` | service / component | transform / request-response | `backend/src/main/java/com/orchestrator/platform/service/execution/WorkflowReportGenerator.java` | role-match |
| `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java` | model / entity | CRUD / request-response | `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java` | exact |
| `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` | controller | request-response | `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` | exact |
| `backend/src/main/resources/application.yml` | config | config | `backend/src/main/resources/application.yml` | exact |
| `frontend/src/types/workflow.ts` | model / type | request-response | `frontend/src/types/workflow.ts` | exact |
| `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` | test | request-response | `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` | role-match |

---

## Pattern Assignments

### 1. `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java` (service / component, transform)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/execution/WorkflowReportGenerator.java`

**Imports pattern** (lines 1-22 in analog):
```java
package com.orchestrator.platform.service.execution;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
```

**Component & Dependency Injection pattern** (lines 23-34 in analog):
```java
@Component
public class ExecutionNavigationUrlBuilder {
    // Stateless helper component participating in Spring ApplicationContext
```

**Core Transform pattern** (analog lines 36-52 pattern of taking entity and generating artifact paths/URIs; adapted for `ServletUriComponentsBuilder` origin resolution):
```java
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
```

**Error handling / Null-safety pattern** (analog lines 48-51, 55-60):
- Guard clauses check for `execution == null` and `execution.getId() == null` before building URIs.
- Fallback in `getBaseBuilder`: when `request` is null (e.g. background threads or unmanaged invocations), falls back to `ServletUriComponentsBuilder.fromCurrentContextPath()`.
- Default report path fallback to `/api/v1/executions/{id}/report/workflow-report.html` when `reportPath` is null or blank.

---

### 2. `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java` (model / entity, CRUD / request-response)

**Analog:** `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java` (self)

**Imports pattern** (lines 1-13 in analog):
```java
package com.orchestrator.platform.domain.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.TriggerType;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
```

**Core Transient Field pattern** (analog lines 45-57 for JSON/persistence fields):
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
```

**Getter & Setter pattern** (lines 70-108 in analog):
```java
    public String getStatusUrl() { return statusUrl; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public String getReportUrl() { return reportUrl; }
    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
```

**Critical annotation guard:**
- Must use `jakarta.persistence.Transient` and NOT `java.beans.Transient`.
- `JsonProperty.Access.READ_ONLY` marks properties as read-only for incoming client requests, preventing external manipulation during deserialization while ensuring Jackson includes them during serialization.

---

### 3. `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` (self)

**Imports pattern** (lines 1-28 in analog):
```java
package com.orchestrator.platform.web;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.service.TestWorkflowExecutionService;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import com.orchestrator.platform.service.execution.ExecutionLogBroadcaster;
import com.orchestrator.platform.service.execution.ExecutionNavigationUrlBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;
```

**Constructor Injection pattern** (lines 37-47 in analog):
```java
    private final TestWorkflowExecutionService executionService;
    private final ExecutionLogBroadcaster logBroadcaster;
    private final DynamicKarateRunner karateRunner;
    private final ExecutionNavigationUrlBuilder urlBuilder;

    public WorkflowExecutionController(TestWorkflowExecutionService executionService,
                                       ExecutionLogBroadcaster logBroadcaster,
                                       DynamicKarateRunner karateRunner,
                                       ExecutionNavigationUrlBuilder urlBuilder) {
        this.executionService = executionService;
        this.logBroadcaster = logBroadcaster;
        this.karateRunner = karateRunner;
        this.urlBuilder = urlBuilder;
    }
```

**Core Endpoint patterns (EXEC-01, EXEC-02, EXEC-03)** (analog lines 49-78):
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
```

**Error Handling & Parameter Validation pattern** (lines 75-77 and 137-156 in analog):
- Explicit `@PathVariable("id") UUID id` prevents reflection lookup issues under Java compiler optimization.
- `Optional.map(...).orElse(ResponseEntity.notFound().build())` returns HTTP 404 cleanly when execution does not exist.

---

### 4. `backend/src/main/resources/application.yml` (config, config)

**Analog:** `backend/src/main/resources/application.yml` (self)

**Configuration block pattern** (lines 1-4 in analog):
```yaml
server:
  port: ${PORT:8080}
  shutdown: graceful
  forward-headers-strategy: framework
```

**Rationale:**
- Enabling `server.forward-headers-strategy: framework` activates Spring Boot's built-in `ForwardedHeaderFilter`.
- Inspects `Host`, `X-Forwarded-Host`, `X-Forwarded-Proto`, and `X-Forwarded-For` forwarded by Nginx (`frontend/nginx.conf:15-18`) and adapts `ServletUriComponentsBuilder` origin resolution to external public URLs.

---

### 5. `frontend/src/types/workflow.ts` (model / type, request-response)

**Analog:** `frontend/src/types/workflow.ts` (self, lines 233-247)

**Core Interface Extension pattern** (lines 233-248 in analog):
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
  // Navigation URLs (Phase 3)
  statusUrl?: string;
  reportUrl?: string;
  streamUrl?: string;
}
```

**Backward compatibility:**
- All navigation URLs are marked optional (`?: string`) ensuring zero breaking changes to existing canvas state, drawer components, or execution streaming consoles.

---

### 6. `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` (lines 44-71) and `backend/src/test/java/com/orchestrator/platform/DynamicKarateRunnerTest.java` (lines 22-40)

**Imports & Annotations pattern**:
```java
package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
```

**Core Test Structure pattern** (using Spring Boot Test with MockMvc):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkflowExecutionNavigationUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private TestWorkflowEntity testWorkflow;

    @BeforeEach
    void setUp() {
        if (testWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Navigation URL Verification Flow");
            wf.setCanvasState(new CanvasState());
            testWorkflow = workflowRepository.save(wf);
        }
    }

    @Test
    @DisplayName("EXEC-01 & EXEC-02: Triggering execution returns HTTP 202, Location header, and absolute statusUrl/reportUrl/streamUrl")
    void testTriggerExecutionReturnsLocationHeaderAndUrls() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute", testWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.statusUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.streamUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/stream")))
                .andExpect(jsonPath("$.reportUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/report/.*")));
    }

    @Test
    @DisplayName("EXEC-03: Details endpoint returns reportUrl and statusUrl matching execution ID")
    void testGetExecutionDetailsContainsReportUrl() throws Exception {
        WorkflowExecutionEntity entity = new WorkflowExecutionEntity();
        entity.setWorkflowId(testWorkflow.getId());
        entity.setWorkflowName(testWorkflow.getName());
        entity.setStatus(ExecutionStatus.COMPLETED);
        entity.setReportPath("/api/v1/executions/" + UUID.randomUUID() + "/report/workflow-report.html");
        entity = executionRepository.save(entity);

        mockMvc.perform(get("/api/v1/executions/{id}", entity.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusUrl", endsWith("/api/v1/executions/" + entity.getId())))
                .andExpect(jsonPath("$.reportUrl", containsString(entity.getReportPath())));
    }

    @Test
    @DisplayName("PROXY-01: Reverse proxy headers (X-Forwarded-Host & X-Forwarded-Proto) adapt navigation URLs")
    void testForwardedHeadersAdaptNavigationUrls() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute", testWorkflow.getId())
                        .header("X-Forwarded-Host", "ci-runner.internal.org:9000")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")))
                .andExpect(jsonPath("$.statusUrl", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")))
                .andExpect(jsonPath("$.reportUrl", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")))
                .andExpect(jsonPath("$.streamUrl", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")));
    }
}
```

---

## Shared Patterns

### Dynamic URI Building & Origin Resolution
**Source:** `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java`
**Apply to:** All hypermedia and link generation services/controllers
```java
private UriComponentsBuilder getBaseBuilder(HttpServletRequest request) {
    if (request != null) {
        return ServletUriComponentsBuilder.fromContextPath(request);
    }
    return ServletUriComponentsBuilder.fromCurrentContextPath();
}
```
*Key principle:* Never hardcode scheme (`http://`) or host (`localhost:8080`). Derive the base builder dynamically using `ServletUriComponentsBuilder.fromContextPath(request)`.

### HTTP 202 Accepted & RFC 7231 `Location` Header
**Source:** `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:51-60`
**Apply to:** Async execution trigger endpoints
```java
return ResponseEntity.status(HttpStatus.ACCEPTED)
        .location(URI.create(execution.getStatusUrl()))
        .body(execution);
```
*Key principle:* RFC 7231 Section 6.3.3 requires that HTTP 202 Accepted responses for created/scheduled processing jobs provide a `Location` header identifying the resource where status can be queried.

### Non-Persistent Entity Transport Properties
**Source:** `backend/src/main/java/com/orchestrator/platform/domain/entity/WorkflowExecutionEntity.java`
**Apply to:** Entity models requiring computed, request-scoped fields in API responses
```java
@jakarta.persistence.Transient
@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
private String statusUrl;
```
*Key principle:* Use `@jakarta.persistence.Transient` so Hibernate omits the field from SQL DDL/DML, while Jackson serializes it cleanly in REST and WebSocket payloads without needing separate DTO mapping layers.

### Reverse Proxy Forwarded Header Propagation
**Source:** `backend/src/main/resources/application.yml:1-4` & `frontend/nginx.conf:15-18`
**Apply to:** Spring Boot application configuration
```yaml
server:
  forward-headers-strategy: framework
```
*Key principle:* Spring Boot's built-in `ForwardedHeaderFilter` normalizes `X-Forwarded-*` headers, preventing internal Docker container hostnames (`http://backend:8080`) from leaking into external links.

---

## No Analog Found

None. All files have exact or role-matched analogs in the existing codebase:
- Controller pattern: `WorkflowExecutionController.java`
- JPA entity pattern: `WorkflowExecutionEntity.java`
- Service component pattern: `WorkflowReportGenerator.java` / `ExecutionLogBroadcaster.java`
- Configuration pattern: `application.yml`
- TypeScript model pattern: `frontend/src/types/workflow.ts`
- Web integration test pattern: `WorkflowFeaturesAndPortabilityTest.java` / `DynamicKarateRunnerTest.java`

---

## Metadata

**Analog search scope:**
- `backend/src/main/java/com/orchestrator/platform/web/`
- `backend/src/main/java/com/orchestrator/platform/service/execution/`
- `backend/src/main/java/com/orchestrator/platform/domain/entity/`
- `backend/src/test/java/com/orchestrator/platform/`
- `frontend/src/types/`
- `frontend/nginx.conf`
- `backend/src/main/resources/`

**Files scanned:** 18  
**Pattern extraction date:** 2026-11-07  
