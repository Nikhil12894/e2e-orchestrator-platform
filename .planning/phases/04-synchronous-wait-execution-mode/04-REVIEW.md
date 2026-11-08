---
phase: "04"
phase_name: "synchronous-wait-execution-mode"
reviewed: 2026-11-08T08:15:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/src/main/java/com/orchestrator/platform/domain/model/dto/ExecutionSyncResult.java
  - backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java
  - backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java
  - backend/src/main/resources/application.yml
  - backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java
reviewed_files:
  - backend/src/main/java/com/orchestrator/platform/domain/model/dto/ExecutionSyncResult.java
  - backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java
  - backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java
  - backend/src/main/resources/application.yml
  - backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java
findings:
  critical: 2
  warning: 3
  info: 2
  total: 7
  necessity: 1
risk_score: 45
status: resolved
summary: "Adversarial code review for Phase 04 (Synchronous Wait Execution Mode). All 2 critical race conditions and 3 warnings have been resolved and verified with passing unit/integration tests."
---

# Phase 04: Code Review Report

**Reviewed:** 2026-11-08T08:15:00Z  
**Depth:** standard  
**Files Reviewed:** 5  
**Status:** resolved  

## Summary

Phase 04 delivers synchronous wait execution mode (`POST /api/v1/workflows/{id}/execute?wait=true&timeoutSec=N`), leveraging Java 25 Virtual Threads and `CompletableFuture<WorkflowExecutionEntity>` to block HTTP connections until completion without platform carrier thread pinning.

The overall architectural design is strong:
- Virtual thread carrier unmounting via `CompletableFuture.get(timeoutSec, TimeUnit.SECONDS)` is non-blocking.
- The standard asynchronous execution path (`wait=false`) is preserved cleanly.
- Defensive timeout clamping mitigates connection starvation and Slowloris threats.
- Navigation URLs and `Location` headers are enriched across all response paths.

However, adversarial static analysis uncovered two critical concurrency race conditions in `TestWorkflowExecutionService`, along with configuration and validation defects in `WorkflowExecutionController` and test fixture isolation weaknesses.

---

## Narrative Findings (AI reviewer)

### Critical Issues

#### CR-01: Race Condition & Memory Leak in `runningTasks` Registry for Fast Executions

**File:** [TestWorkflowExecutionService.java:112-142](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java#L112-L142)  
**Issue:**  
In `triggerExecutionInternal()`, the background task is submitted via `virtualThreadExecutor.submit(...)` before `runningTasks.put(executionId, taskFuture)` is executed on line 142. On lightweight workflows (such as simple transforms or cached steps), the virtual thread starts immediately on another carrier thread and can complete before the submitting thread reaches line 142.

When the fast task terminates:
1. The virtual thread enters the `finally` block and calls `runningTasks.remove(executionId)` (line 137). At this moment, `runningTasks` does not yet contain `executionId`, so `remove()` returns `null`.
2. The virtual thread terminates.
3. The submitting thread then executes line 142: `runningTasks.put(executionId, taskFuture)`.
4. The completed `Future<?>` is now permanently stranded in `runningTasks` and will never be removed by any lifecycle event, causing an unbounded memory leak over high volumes of short-lived executions.

Furthermore, if `cancelExecution()` is called immediately after `submit()` but before line 142, `runningTasks.remove(executionId)` returns `null`, failing to invoke `task.cancel(true)`.

**Fix:**  
Ensure completed tasks are never stranded by checking `taskFuture.isDone()` immediately after insertion, or register the future atomically:
```java
        Future<?> taskFuture = virtualThreadExecutor.submit(() -> {
            try {
                // execution logic...
            } finally {
                runningTasks.remove(executionId);
                executionFutures.remove(executionId);
                pruneWorkflowExecutionsIfExceeded(workflowId);
            }
        });
        runningTasks.put(executionId, taskFuture);
        if (taskFuture.isDone()) {
            runningTasks.remove(executionId);
        }
```

---

#### CR-02: Concurrency Race in `cancelExecution` Overwrites `CANCELLED` Status to `FAILED`

**File:** [TestWorkflowExecutionService.java:123-136](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java#L123-L136) and [TestWorkflowExecutionService.java:150-167](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java#L150-L167)  
**Issue:**  
In `cancelExecution()`, `task.cancel(true)` is called on line 153 *before* the database entity status is updated and flushed to `ExecutionStatus.CANCELLED` on line 167.

When `task.cancel(true)` interrupts the virtual thread (e.g. while paused in `DELAY` or waiting on I/O):
1. The virtual thread catches an `InterruptedException` (or Karate runtime exception) on line 123.
2. In the catch block on line 125, the worker queries `executionRepository.findById(executionId)`.
3. If this lookup occurs before `cancelExecution()` flushes line 167, the entity's database status is still `RUNNING`.
4. Line 126 evaluates `if (ex.getStatus() != ExecutionStatus.CANCELLED)` — which evaluates to `true` because it checks the database entity instead of `DynamicKarateBridge.isExecutionCancelled(executionId.toString())`!
5. The worker thread overwrites the execution status to `ExecutionStatus.FAILED` with error message `"sleep interrupted"` and persists it via `executionRepository.saveAndFlush(ex)`.
6. When `cancelExecution()` subsequently reaches line 159 (`if (execution.getStatus() == ExecutionStatus.RUNNING || execution.getStatus() == ExecutionStatus.PENDING)`), it evaluates to `false` because the status is now `FAILED`.
7. `cancelExecution()` returns the entity with `ExecutionStatus.FAILED` instead of `CANCELLED`, fails to broadcast `WORKFLOW_CANCELLED`, and unblocks any waiting synchronous client with `FAILED`.

**Fix:**  
Check `DynamicKarateBridge.isExecutionCancelled(executionId.toString())` in the worker thread exception handler:
```java
            } catch (Exception e) {
                log.error("Unhandled error during async virtual thread workflow execution: {}", e.getMessage(), e);
                boolean wasCancelled = DynamicKarateBridge.isExecutionCancelled(executionId.toString());
                WorkflowExecutionEntity failed = executionRepository.findById(executionId).map(ex -> {
                    if (!wasCancelled && ex.getStatus() != ExecutionStatus.CANCELLED) {
                        ex.setStatus(ExecutionStatus.FAILED);
                        ex.setErrorMessage(e.getMessage());
                        return executionRepository.saveAndFlush(ex);
                    }
                    return ex;
                }).orElse(savedExecution);
                if (future != null) {
                    future.complete(failed);
                }
            }
```

---

### Warnings

#### WR-01: Dead Configuration Property `orchestrator.execution.default-timeout-status` in `application.yml`

**File:** [application.yml:46](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/resources/application.yml#L46) and [WorkflowExecutionController.java:66](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java#L66)  
**Category:** necessity  
**Issue:**  
`application.yml` configures `default-timeout-status: ${ORCHESTRATOR_EXECUTION_DEFAULT_TIMEOUT_STATUS:504}` under `orchestrator.execution`. However, `WorkflowExecutionController` never injects or references this property. Instead, line 66 hardcodes `defaultValue = "504"`. Any deployment overriding `ORCHESTRATOR_EXECUTION_DEFAULT_TIMEOUT_STATUS` (e.g. in Kubernetes or Docker Compose to default to 202 for strict proxies) has zero effect.

**Fix:**  
Bind the `@RequestParam` default to the property or inject the configuration value:
```java
@RequestParam(value = "timeoutStatus", required = false, defaultValue = "${orchestrator.execution.default-timeout-status:504}") int timeoutStatus
```

---

#### WR-02: Missing Input Validation for `timeoutStatus` Query Parameter

**File:** [WorkflowExecutionController.java:66, 93](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java#L66)  
**Issue:**  
Line 66 accepts any integer for `timeoutStatus`. Line 93 then resolves the HTTP status via:
```java
HttpStatus status = (timeoutStatus == 202) ? HttpStatus.ACCEPTED : HttpStatus.GATEWAY_TIMEOUT;
```
If a caller supplies an invalid or unsupported status code (e.g. `?timeoutStatus=200`, `?timeoutStatus=404`, `?timeoutStatus=999`), the controller silently coerces it to `504 Gateway Timeout` without returning an HTTP 400 Bad Request error. Clients expecting a specific response code receive 504 without warning.

**Fix:**  
Validate that `timeoutStatus` is either `202` or `504`, returning HTTP 400 Bad Request for unsupported values:
```java
if (timeoutStatus != 202 && timeoutStatus != 504) {
    return ResponseEntity.badRequest().build();
}
```

---

#### WR-03: Shared `delayWorkflow` Fixture Causes Intermittent Test Flakiness in Cancellation Test

**File:** [WorkflowExecutionSyncModeTest.java:188-197](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java#L188-L197)  
**Issue:**  
Four tests in `WorkflowExecutionSyncModeTest` execute against the shared fixture `delayWorkflow` (configured with a 3000ms delay). When `testSynchronousExecutionTimeoutReturns504` completes after a 1000ms wait, the delay execution continues running in the background for another ~2000ms.

If `testSynchronousExecutionCancellationUnblocksCaller` executes while that prior background run is still active, line 191 (`executionRepository.findByWorkflowIdOrderByCreatedAtDesc(delayWorkflow.getId())`) can find the running execution from the *previous* test. The test then cancels that stale execution while `requestFuture`'s execution continues sleeping for the full 3000ms, causing intermittent assertion failures on CI runners (`assertTrue(elapsed < 2000)` and status verification).

**Fix:**  
Create a dedicated test workflow entity specifically for the cancellation test:
```java
TestWorkflowEntity cancellationWorkflow = workflowRepository.saveAndFlush(createDelayWorkflow("Cancellation Isolated Flow", 5000L));
```

---

### Info

#### IN-01: Missing Swagger/OpenAPI `@Parameter` Annotations for Execution Parameters

**File:** [WorkflowExecutionController.java:59-68](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java#L59-L68)  
**Issue:**  
While `@Operation` was updated with a summary and description, the query parameters `wait`, `timeoutSec`, and `timeoutStatus` lack OpenAPI `@Parameter` annotations. As a result, Swagger UI (`/swagger-ui.html`) does not display descriptions, defaults, or parameter constraints (such as the 1-300s defensive clamping).

**Fix:**  
Annotate each query parameter with `@Parameter`:
```java
@Parameter(description = "If true, blocks HTTP request until workflow completes or timeoutSec expires", example = "true")
@RequestParam(value = "wait", required = false, defaultValue = "false") boolean wait,
@Parameter(description = "Synchronous wait timeout in seconds (clamped between 1 and max-wait-timeout-sec)", example = "60")
@RequestParam(value = "timeoutSec", required = false, defaultValue = "60") int timeoutSec,
@Parameter(description = "HTTP status code to return on timeout: 504 (Gateway Timeout) or 202 (Accepted)", example = "504")
@RequestParam(value = "timeoutStatus", required = false, defaultValue = "504") int timeoutStatus
```

---

#### IN-02: Uncaught Exception Handler Leaves Fallback Execution Status as `PENDING`

**File:** [TestWorkflowExecutionService.java:125-135](file:///Users/nalinkumar/Developer/e2e-orchestrator-platform/backend/src/main/java/com/orchestrator/platform/service/TestWorkflowExecutionService.java#L125-L135)  
**Issue:**  
In `triggerExecutionInternal()`, the `catch (Exception e)` block uses `.orElse(savedExecution)` if `executionRepository.findById(executionId)` returns empty:
```java
WorkflowExecutionEntity failed = executionRepository.findById(executionId).map(...)
    .orElse(savedExecution);
```
If the database lookup returns empty, `savedExecution` (created on line 101 with `ExecutionStatus.PENDING`) is passed unmodified to `future.complete(failed)`. A waiting synchronous client would receive HTTP 200 with status `PENDING` despite an unhandled exception crashing the execution.

**Fix:**  
Update `savedExecution` status in an `.orElseGet()` fallback:
```java
.orElseGet(() -> {
    savedExecution.setStatus(ExecutionStatus.FAILED);
    savedExecution.setErrorMessage(e.getMessage());
    return savedExecution;
});
```

---

_Reviewed: 2026-11-08T08:15:00Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
