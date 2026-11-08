# Phase 4: Synchronous Wait Execution Mode - Pattern Map
**Mapped:** 2026-11-08  
**Files analyzed:** 5  
**Analogs found:** 5 / 5  

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/orchestrator/platform/domain/model/dto/ExecutionSyncResult.java` | model (DTO) | request-response | `backend/src/main/java/com/orchestrator/platform/domain/model/dto/WorkflowExecutionRequest.java` | exact |
| `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java` | service | event-driven / request-response | `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java` | exact |
| `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` | controller | request-response | `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` | exact |
| `backend/src/main/resources/application.yml` | config | request-response | `backend/src/main/resources/application.yml` | exact |
| `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java` | test | request-response | `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` | exact |

---

## Pattern Assignments

### `backend/src/main/java/com/orchestrator/platform/domain/model/dto/ExecutionSyncResult.java` (model / DTO, request-response)

**Analog:** `backend/src/main/java/com/orchestrator/platform/domain/model/dto/WorkflowExecutionRequest.java` and `backend/src/main/java/com/orchestrator/platform/domain/model/dto/DbConnectionTestResult.java`

**Imports pattern:**
```java
package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import java.io.Serializable;
```

**Core Record DTO Pattern:**
Java 25 canonical record pattern for immutable execution transfer result carrying the execution entity and boolean timeout flag:
```java
package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import java.io.Serializable;

public record ExecutionSyncResult(
        WorkflowExecutionEntity execution,
        boolean timedOut
) implements Serializable {}
```

**Alternative Class DTO Pattern** (following `DbConnectionTestResult.java` lines 6-18):
```java
public class ExecutionSyncResult implements Serializable {
    private WorkflowExecutionEntity execution;
    private boolean timedOut;

    public ExecutionSyncResult() {}

    public ExecutionSyncResult(WorkflowExecutionEntity execution, boolean timedOut) {
        this.execution = execution;
        this.timedOut = timedOut;
    }

    public WorkflowExecutionEntity getExecution() { return execution; }
    public void setExecution(WorkflowExecutionEntity execution) { this.execution = execution; }

    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }
}
```

---

### `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java` (service, event-driven / request-response)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java` (itself, lines 20-129)

**Imports pattern** (lines 20-26):
```java
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
```

**State Tracking Pattern** (lines 36-38):
```java
    private final ExecutorService virtualThreadExecutor;
    private final Map<UUID, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<WorkflowExecutionEntity>> executionFutures = new ConcurrentHashMap<>();
```

**Core Execution Synchronization Pattern** (lines 57-96 adapted):
```java
    public WorkflowExecutionEntity triggerExecution(UUID workflowId, WorkflowExecutionRequest request) {
        return triggerExecutionInternal(workflowId, request, null);
    }

    public ExecutionSyncResult triggerExecutionSync(UUID workflowId, WorkflowExecutionRequest request, int timeoutSec) {
        CompletableFuture<WorkflowExecutionEntity> completionFuture = new CompletableFuture<>();
        WorkflowExecutionEntity pending = triggerExecutionInternal(workflowId, request, completionFuture);
        UUID executionId = pending.getId();

        try {
            WorkflowExecutionEntity completed = completionFuture.get(timeoutSec, TimeUnit.SECONDS);
            return new ExecutionSyncResult(completed, false);
        } catch (TimeoutException e) {
            log.warn("Synchronous wait timed out after {}s for execution [id={}] (continuing in background)",
                    timeoutSec, executionId);
            WorkflowExecutionEntity inFlight = executionRepository.findById(executionId).orElse(pending);
            return new ExecutionSyncResult(inFlight, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Synchronous wait interrupted for execution [id={}]", executionId);
            WorkflowExecutionEntity inFlight = executionRepository.findById(executionId).orElse(pending);
            return new ExecutionSyncResult(inFlight, true);
        } catch (ExecutionException e) {
            log.error("Execution failed with exception: {}", e.getMessage(), e);
            WorkflowExecutionEntity inFlight = executionRepository.findById(executionId).orElse(pending);
            return new ExecutionSyncResult(inFlight, false);
        }
    }
```

**Task Dispatch and Future Completion Pattern** (lines 71-93 adapted):
```java
    private WorkflowExecutionEntity triggerExecutionInternal(UUID workflowId,
                                                              WorkflowExecutionRequest request,
                                                              CompletableFuture<WorkflowExecutionEntity> future) {
        TestWorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + workflowId));

        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowName(workflow.getName());
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setTriggerType(request.getTriggerType());

        WorkflowExecutionEntity savedExecution = executionRepository.saveAndFlush(execution);
        UUID executionId = savedExecution.getId();

        if (future != null) {
            executionFutures.put(executionId, future);
        }

        // Offload execution to Java 25 Virtual Threads
        Future<?> taskFuture = virtualThreadExecutor.submit(() -> {
            try {
                WorkflowExecutionEntity finished = karateRunner.executeWorkflow(
                        savedExecution,
                        workflow,
                        request.getInitialVariables(),
                        request.getEnvironment()
                );
                if (future != null) {
                    future.complete(finished);
                }
            } catch (Exception e) {
                log.error("Unhandled error during async virtual thread workflow execution: {}", e.getMessage(), e);
                WorkflowExecutionEntity failed = executionRepository.findById(executionId).map(ex -> {
                    if (ex.getStatus() != ExecutionStatus.CANCELLED) {
                        ex.setStatus(ExecutionStatus.FAILED);
                        ex.setErrorMessage(e.getMessage());
                        return executionRepository.saveAndFlush(ex);
                    }
                    return ex;
                }).orElse(savedExecution);
                if (future != null) {
                    future.complete(failed);
                }
            } finally {
                runningTasks.remove(executionId);
                executionFutures.remove(executionId);
                pruneWorkflowExecutionsIfExceeded(workflowId);
            }
        });
        runningTasks.put(executionId, taskFuture);

        return savedExecution;
    }
```

**Cooperative Cancellation Pattern** (lines 98-129 adapted):
```java
    @Transactional
    public WorkflowExecutionEntity cancelExecution(UUID executionId) {
        log.info("Cancelling execution [id={}]", executionId);
        DynamicKarateBridge.markCancelled(executionId.toString());
        Future<?> task = runningTasks.remove(executionId);
        if (task != null) {
            task.cancel(true);
        }

        WorkflowExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found with ID: " + executionId));

        if (execution.getStatus() == ExecutionStatus.RUNNING || execution.getStatus() == ExecutionStatus.PENDING) {
            execution.setStatus(ExecutionStatus.CANCELLED);
            execution.setErrorMessage("Execution cancelled by user");
            Instant now = Instant.now();
            execution.setEndTime(now);
            if (execution.getStartTime() != null) {
                execution.setDurationMs(Duration.between(execution.getStartTime(), now).toMillis());
            }
            execution = executionRepository.saveAndFlush(execution);

            logBroadcaster.broadcast(new ExecutionEventDto(
                    executionId, execution.getWorkflowId(), "WORKFLOW_CANCELLED", null, null,
                    ExecutionStatus.CANCELLED,
                    "Execution " + executionId + " was cancelled by user",
                    execution
            ));
        }

        // Unblock waiting synchronous callers immediately upon cancellation
        CompletableFuture<WorkflowExecutionEntity> future = executionFutures.remove(executionId);
        if (future != null) {
            future.complete(execution);
        }

        return execution;
    }
```

---

### `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java` (itself, lines 54-72)

**Imports pattern** (lines 3-21):
```java
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.model.dto.ExecutionSyncResult;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.service.TestWorkflowExecutionService;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import com.orchestrator.platform.service.execution.ExecutionLogBroadcaster;
import com.orchestrator.platform.service.execution.ExecutionNavigationUrlBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
```

**Field Injection Pattern:**
```java
    @Value("${orchestrator.execution.max-wait-timeout-sec:300}")
    private int maxWaitTimeoutSec = 300;
```

**Core Endpoint Pattern** (lines 54-72 adapted):
```java
    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "Trigger workflow execution",
               description = "Triggers workflow test execution. With ?wait=true, blocks up to timeoutSec returning HTTP 200 upon completion or HTTP 504 on timeout. Without ?wait=true, returns HTTP 202 immediately.")
    public ResponseEntity<WorkflowExecutionEntity> executeWorkflow(
            @PathVariable("workflowId") UUID workflowId,
            @RequestParam(value = "wait", required = false, defaultValue = "false") boolean wait,
            @RequestParam(value = "timeoutSec", required = false, defaultValue = "60") int timeoutSec,
            @RequestParam(value = "timeoutStatus", required = false, defaultValue = "504") int timeoutStatus,
            @RequestBody(required = false) WorkflowExecutionRequest request,
            HttpServletRequest httpRequest) {

        if (request == null) {
            request = new WorkflowExecutionRequest();
        }

        // Asynchronous branch (default regression behavior: REG-01)
        if (!wait) {
            WorkflowExecutionEntity execution = executionService.triggerExecution(workflowId, request);
            urlBuilder.enrichNavigationUrls(execution, httpRequest);

            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.ACCEPTED);
            if (execution != null && execution.getStatusUrl() != null) {
                responseBuilder.location(URI.create(execution.getStatusUrl()));
            }
            return responseBuilder.body(execution);
        }

        // Synchronous wait branch (SYNC-01, SYNC-02, SYNC-03)
        // Defensive clamp: min 1s, max configured timeout (default 300s)
        int clampedTimeout = Math.max(1, Math.min(timeoutSec, maxWaitTimeoutSec));

        ExecutionSyncResult syncResult = executionService.triggerExecutionSync(workflowId, request, clampedTimeout);
        WorkflowExecutionEntity execution = syncResult.execution();
        urlBuilder.enrichNavigationUrls(execution, httpRequest);

        // SYNC-03: Timeout exceeded
        if (syncResult.timedOut()) {
            HttpStatus status = (timeoutStatus == 202) ? HttpStatus.ACCEPTED : HttpStatus.GATEWAY_TIMEOUT;
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status)
                    .header("X-Execution-Timed-Out", "true");
            if (execution != null && execution.getStatusUrl() != null) {
                responseBuilder.location(URI.create(execution.getStatusUrl()));
            }
            return responseBuilder.body(execution);
        }

        // SYNC-02: Completed within timeout (HTTP 200 with final status COMPLETED or FAILED)
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (execution != null && execution.getStatusUrl() != null) {
            responseBuilder.location(URI.create(execution.getStatusUrl()));
        }
        return responseBuilder.body(execution);
    }
```

---

### `backend/src/main/resources/application.yml` (config, request-response)

**Analog:** `backend/src/main/resources/application.yml` (lines 43-52)

**Configuration Excerpt Pattern:**
```yaml
orchestrator:
  reports:
    base-path: ${ORCHESTRATOR_REPORTS_PATH:./target/karate-reports}
  retention:
    auto-prune-enabled: ${ORCHESTRATOR_RETENTION_AUTO_PRUNE_ENABLED:true}
    max-runs-per-workflow: ${ORCHESTRATOR_RETENTION_MAX_RUNS_PER_WORKFLOW:3}
  async:
    core-pool-size: 8
    max-pool-size: 64
  execution:
    max-wait-timeout-sec: ${ORCHESTRATOR_EXECUTION_MAX_WAIT_TIMEOUT_SEC:300}
    default-timeout-status: ${ORCHESTRATOR_EXECUTION_DEFAULT_TIMEOUT_STATUS:504}
```

---

### `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java` (lines 1-125) and `backend/src/test/java/com/orchestrator/platform/SubWorkflowCompositionTest.java` (lines 48-60)

**Imports pattern** (lines 1-29 of `WorkflowExecutionNavigationUrlTest.java`):
```java
package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.TransformStepConfig;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
```

**Test Fixture Initialization Pattern:**
```java
    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private TestWorkflowEntity fastWorkflow;
    private TestWorkflowEntity delayWorkflow;
    private TestWorkflowEntity failingWorkflow;

    @BeforeEach
    void setUp() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .addFilters(new ForwardedHeaderFilter())
                    .build();
        }

        // Fast completed workflow fixture
        if (fastWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Fast Sync Test Workflow");
            WorkflowAst ast = new WorkflowAst();
            WorkflowStep step = new WorkflowStep();
            step.setId("step_fast_transform");
            step.setName("Fast Transform");
            step.setType(StepType.TRANSFORM);
            TransformStepConfig config = new TransformStepConfig();
            config.setScript("function(ctx) { return { status: 'OK' }; }");
            step.setTransformConfig(config);
            ast.setSteps(List.of(step));
            wf.setWorkflowAst(ast);
            wf.setCanvasState(new CanvasState());
            fastWorkflow = workflowRepository.save(wf);
        }

        // Delay workflow fixture (3000ms delay) for timeout testing
        if (delayWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Delay Sync Test Workflow");
            WorkflowAst ast = new WorkflowAst();
            WorkflowStep step = new WorkflowStep();
            step.setId("step_delay");
            step.setName("Delay Step");
            step.setType(StepType.DELAY);
            step.setDelayMs(3000);
            ast.setSteps(List.of(step));
            wf.setWorkflowAst(ast);
            wf.setCanvasState(new CanvasState());
            delayWorkflow = workflowRepository.save(wf);
        }

        // Failing workflow fixture
        if (failingWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Failing Sync Test Workflow");
            WorkflowAst ast = new WorkflowAst();
            WorkflowStep step = new WorkflowStep();
            step.setId("step_failing_transform");
            step.setName("Failing Transform");
            step.setType(StepType.TRANSFORM);
            TransformStepConfig config = new TransformStepConfig();
            config.setScript("function(ctx) { throw 'step intentional failure'; }");
            step.setTransformConfig(config);
            ast.setSteps(List.of(step));
            wf.setWorkflowAst(ast);
            wf.setCanvasState(new CanvasState());
            failingWorkflow = workflowRepository.save(wf);
        }
    }
```

**Core Test Assertion Patterns:**
```java
    @Test
    @DisplayName("SYNC-01 & SYNC-02: wait=true completes within timeout and returns HTTP 200 with completed status and reportUrl")
    void testSynchronousExecutionReturnsCompletedAndReportUrl() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=30", fastWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.reportUrl", notNullValue()))
                .andExpect(jsonPath("$.reportUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/report/.*")))
                .andExpect(jsonPath("$.statusUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")));
    }

    @Test
    @DisplayName("SYNC-02: wait=true with failing workflow step returns HTTP 200 with status FAILED and valid report link")
    void testSynchronousExecutionFailureReturns200WithFailedStatus() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=30", failingWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.reportUrl", notNullValue()));
    }

    @Test
    @DisplayName("SYNC-03: wait=true exceeding timeoutSec returns HTTP 504 with X-Execution-Timed-Out and in-flight entity")
    void testSynchronousExecutionTimeoutReturns504() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=1", delayWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Execution-Timed-Out", "true"))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", isOneOf("RUNNING", "PENDING")));
    }

    @Test
    @DisplayName("SYNC-03: wait=true timeout leaves background execution running without cancelling")
    void testTimeoutLeavesBackgroundExecutionRunning() throws Exception {
        String responseJson = mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=1", delayWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isGatewayTimeout())
                .andReturn().getResponse().getContentAsString();

        WorkflowExecutionEntity entity = objectMapper.readValue(responseJson, WorkflowExecutionEntity.class);
        UUID executionId = entity.getId();

        // Execution must not be CANCELLED or FAILED prematurely
        assertNotEquals(ExecutionStatus.CANCELLED, entity.getStatus());

        // Wait for background delay (up to 5s) to complete normally
        long deadline = System.currentTimeMillis() + 5000;
        ExecutionStatus finalStatus = entity.getStatus();
        while (System.currentTimeMillis() < deadline) {
            WorkflowExecutionEntity current = executionRepository.findById(executionId).orElseThrow();
            finalStatus = current.getStatus();
            if (finalStatus == ExecutionStatus.COMPLETED || finalStatus == ExecutionStatus.FAILED) {
                break;
            }
            Thread.sleep(200);
        }
        assertEquals(ExecutionStatus.COMPLETED, finalStatus, "Background execution should finish as COMPLETED");
    }

    @Test
    @DisplayName("SYNC-03: timeoutStatus=202 parameter overrides default 504 status code")
    void testTimeoutStatus202Override() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=1&timeoutStatus=202", delayWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Execution-Timed-Out", "true"))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", isOneOf("RUNNING", "PENDING")));
    }

    @Test
    @DisplayName("REG-01: Asynchronous trigger without wait=true continues returning HTTP 202 immediately")
    void testAsynchronousExecutionRegressionReturns202() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute", fastWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().doesNotExist("X-Execution-Timed-Out"));
    }
```

---

## Shared Patterns

### 1. Navigation URL & Location Header Enrichment
**Source:** `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java:16-59` and `WorkflowExecutionController.java:65-70`  
**Apply to:** All controller execution responses (`executeWorkflow` for HTTP 200, 202, 504)  
```java
// Always enrich execution entity with absolute URLs derived from request context/forwarded headers
urlBuilder.enrichNavigationUrls(execution, httpRequest);

// Populate standard Location header pointing to execution's statusUrl
if (execution != null && execution.getStatusUrl() != null) {
    responseBuilder.location(URI.create(execution.getStatusUrl()));
}
```

### 2. Virtual Thread Carrier-Unmounting Synchronization
**Source:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java:71-93` and JDK 25 `CompletableFuture`  
**Apply to:** Service asynchronous execution tracking  
```java
CompletableFuture<WorkflowExecutionEntity> completionFuture = new CompletableFuture<>();
executionFutures.put(executionId, completionFuture);

// Inside virtualThreadExecutor task:
try {
    WorkflowExecutionEntity finished = karateRunner.executeWorkflow(...);
    completionFuture.complete(finished);
} catch (Exception e) {
    ...
    completionFuture.complete(failed);
} finally {
    executionFutures.remove(executionId);
}

// Inside request thread (parks virtual thread without OS carrier pinning):
WorkflowExecutionEntity completed = completionFuture.get(timeoutSec, TimeUnit.SECONDS);
```

### 3. Defensive Parameter Clamping & Environment Fallbacks
**Source:** `backend/src/main/resources/application.yml:43-52` and `WorkflowExecutionController.java:57-60`  
**Apply to:** Controller query parameter parsing and configuration resolution  
```java
@Value("${orchestrator.execution.max-wait-timeout-sec:300}")
private int maxWaitTimeoutSec = 300;

// Clamp timeoutSec: minimum 1s, maximum maxWaitTimeoutSec
int clampedTimeout = Math.max(1, Math.min(timeoutSec, maxWaitTimeoutSec));
```

### 4. MockMvc Test Harness with ForwardedHeaderFilter
**Source:** `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java:51-56`  
**Apply to:** All web integration tests (`WorkflowExecutionSyncModeTest.java`)  
```java
mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .addFilters(new ForwardedHeaderFilter())
        .build();
```

---

## No Analog Found

All files in scope for Phase 4 have exact in-codebase analogs.

| File | Role | Data Flow | Reason |
|---|---|---|---|
| *(none)* | — | — | All files map directly to existing controller, service, DTO, test, and config patterns. |

---

## Metadata

**Analog search scope:**
- `backend/src/main/java/com/orchestrator/platform/web/`
- `backend/src/main/java/com/orchestrator/platform/service/`
- `backend/src/main/java/com/orchestrator/platform/domain/model/dto/`
- `backend/src/main/resources/`
- `backend/src/test/java/com/orchestrator/platform/`

**Files scanned:** 16  
**Pattern extraction date:** 2026-11-08  
