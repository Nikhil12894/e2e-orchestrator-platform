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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ExecutorService;

@Service
public class TestWorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TestWorkflowExecutionService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final TestWorkflowRepository workflowRepository;
    private final DynamicKarateRunner karateRunner;
    private final ExecutorService virtualThreadExecutor;

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
}
