package com.orchestrator.platform.service;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;

@Service
public class TestWorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TestWorkflowExecutionService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final TestWorkflowRepository workflowRepository;
    private final DynamicKarateRunner karateRunner;
    private final ExecutorService virtualThreadExecutor;

    @Value("${orchestrator.retention.auto-prune-enabled:true}")
    private boolean autoPruneEnabled;

    @Value("${orchestrator.retention.max-runs-per-workflow:30}")
    private int maxRunsPerWorkflow;

    public TestWorkflowExecutionService(WorkflowExecutionRepository executionRepository,
                                        TestWorkflowRepository workflowRepository,
                                        DynamicKarateRunner karateRunner,
                                        @Qualifier("workflowVirtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
        this.karateRunner = karateRunner;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    public WorkflowExecutionEntity triggerExecution(UUID workflowId, WorkflowExecutionRequest request) {
        TestWorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + workflowId));

        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowName(workflow.getName());
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setTriggerType(request.getTriggerType());

        WorkflowExecutionEntity savedExecution = executionRepository.saveAndFlush(execution);
        UUID executionId = savedExecution.getId();

        // Offload execution to Java 21 Virtual Threads
        virtualThreadExecutor.submit(() -> {
            try {
                karateRunner.executeWorkflow(
                        savedExecution,
                        workflow,
                        request.getInitialVariables(),
                        request.getEnvironment()
                );
            } catch (Exception e) {
                log.error("Unhandled error during async virtual thread workflow execution: {}", e.getMessage(), e);
                executionRepository.findById(executionId).ifPresent(ex -> {
                    ex.setStatus(ExecutionStatus.FAILED);
                    ex.setErrorMessage(e.getMessage());
                    executionRepository.saveAndFlush(ex);
                });
            } finally {
                pruneWorkflowExecutionsIfExceeded(workflowId);
            }
        });

        return savedExecution;
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
