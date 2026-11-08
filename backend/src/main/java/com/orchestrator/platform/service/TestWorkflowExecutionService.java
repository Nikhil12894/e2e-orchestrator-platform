package com.orchestrator.platform.service;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.dto.ExecutionEventDto;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.execution.DynamicKarateBridge;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import com.orchestrator.platform.service.execution.ExecutionLogBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.orchestrator.platform.domain.model.dto.ExecutionSyncResult;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class TestWorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TestWorkflowExecutionService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final TestWorkflowRepository workflowRepository;
    private final DynamicKarateRunner karateRunner;
    private final ExecutionLogBroadcaster logBroadcaster;
    private final ExecutorService virtualThreadExecutor;
    private final Map<UUID, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<WorkflowExecutionEntity>> executionFutures = new ConcurrentHashMap<>();

    @Value("${orchestrator.retention.auto-prune-enabled:true}")
    private boolean autoPruneEnabled;

    @Value("${orchestrator.retention.max-runs-per-workflow:30}")
    private int maxRunsPerWorkflow;

    public TestWorkflowExecutionService(WorkflowExecutionRepository executionRepository,
                                        TestWorkflowRepository workflowRepository,
                                        DynamicKarateRunner karateRunner,
                                        ExecutionLogBroadcaster logBroadcaster,
                                        @Qualifier("workflowVirtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
        this.karateRunner = karateRunner;
        this.logBroadcaster = logBroadcaster;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

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

        return execution;
    }

    public List<WorkflowExecutionEntity> getAllExecutions() {
        return executionRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<WorkflowExecutionEntity> getExecutionsForWorkflow(UUID workflowId) {
        return executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
    }

    public Optional<WorkflowExecutionEntity> getExecutionById(UUID id) {
        return executionRepository.findById(id);
    }

    @Transactional
    public void deleteExecution(UUID executionId) {
        log.info("Deleting execution run [id={}]", executionId);
        karateRunner.deleteExecutionFiles(executionId);
        executionRepository.deleteById(executionId);
    }

    @Transactional
    public void deleteExecutionsForWorkflow(UUID workflowId) {
        log.info("Deleting all execution runs for workflow [id={}]", workflowId);
        List<WorkflowExecutionEntity> executions = executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
        for (WorkflowExecutionEntity ex : executions) {
            karateRunner.deleteExecutionFiles(ex.getId());
        }
        executionRepository.deleteByWorkflowId(workflowId);
    }

    @Transactional
    public int purgeExecutions(Integer keepLatest, Integer olderThanDays) {
        log.info("Purging executions [keepLatest={}, olderThanDays={}]", keepLatest, olderThanDays);
        List<WorkflowExecutionEntity> all = executionRepository.findAllByOrderByCreatedAtDesc();
        int purged = 0;
        Instant cutoff = (olderThanDays != null && olderThanDays > 0)
                ? Instant.now().minus(Duration.ofDays(olderThanDays))
                : null;
        int limit = (keepLatest != null && keepLatest >= 0) ? keepLatest : 30;

        Map<UUID, List<WorkflowExecutionEntity>> byWorkflow = new HashMap<>();
        for (WorkflowExecutionEntity ex : all) {
            byWorkflow.computeIfAbsent(ex.getWorkflowId(), k -> new ArrayList<>()).add(ex);
        }

        for (Map.Entry<UUID, List<WorkflowExecutionEntity>> entry : byWorkflow.entrySet()) {
            List<WorkflowExecutionEntity> runs = entry.getValue();
            for (int i = 0; i < runs.size(); i++) {
                WorkflowExecutionEntity ex = runs.get(i);
                boolean shouldDelete = false;
                if (i >= limit) {
                    shouldDelete = true;
                }
                if (cutoff != null && ex.getCreatedAt() != null && ex.getCreatedAt().isBefore(cutoff)) {
                    shouldDelete = true;
                }
                if (shouldDelete) {
                    karateRunner.deleteExecutionFiles(ex.getId());
                    executionRepository.deleteById(ex.getId());
                    purged++;
                }
            }
        }
        return purged;
    }

    public void pruneWorkflowExecutionsIfExceeded(UUID workflowId) {
        if (!autoPruneEnabled || maxRunsPerWorkflow <= 0 || workflowId == null) {
            return;
        }
        try {
            List<WorkflowExecutionEntity> runs = executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
            if (runs.size() > maxRunsPerWorkflow) {
                List<WorkflowExecutionEntity> toDelete = runs.subList(maxRunsPerWorkflow, runs.size());
                log.info("Auto-pruning {} excess execution runs for workflow {}", toDelete.size(), workflowId);
                for (WorkflowExecutionEntity ex : toDelete) {
                    karateRunner.deleteExecutionFiles(ex.getId());
                    executionRepository.deleteById(ex.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to auto-prune executions for workflow {}: {}", workflowId, e.getMessage());
        }
    }
}
