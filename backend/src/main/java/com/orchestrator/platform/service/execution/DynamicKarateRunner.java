package com.orchestrator.platform.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.dto.ExecutionEventDto;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.WorkflowContextEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@Service
public class DynamicKarateRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamicKarateRunner.class);

    @Value("${orchestrator.reports.base-path:./target/karate-reports}")
    private String reportsBasePath;

    private final WorkflowExecutionRepository executionRepository;
    private final ExecutionLogBroadcaster logBroadcaster;
    private final ObjectMapper objectMapper;
    private final WorkflowContextEngine contextEngine;

    public DynamicKarateRunner(WorkflowExecutionRepository executionRepository,
                               ExecutionLogBroadcaster logBroadcaster,
                               ObjectMapper objectMapper,
                               WorkflowContextEngine contextEngine) {
        this.executionRepository = executionRepository;
        this.logBroadcaster = logBroadcaster;
        this.objectMapper = objectMapper;
        this.contextEngine = contextEngine;
    }

    public WorkflowExecutionEntity executeWorkflow(WorkflowExecutionEntity execution,
                                                  TestWorkflowEntity workflow,
                                                  Map<String, Object> initialVariables,
                                                  String environment) {
        UUID executionId = execution.getId();
        log.info("Starting Dynamic Karate Execution [id={}, workflow={}]", executionId, workflow.getName());

        long startTime = System.currentTimeMillis();
        WorkflowExecutionEntity currentExecution = executionRepository.findById(executionId).orElse(execution);
        currentExecution.setStatus(ExecutionStatus.RUNNING);
        currentExecution.setStartTime(Instant.ofEpochMilli(startTime));
        currentExecution = executionRepository.saveAndFlush(currentExecution);

        // Prepare execution report directory
        Path reportDir = Paths.get(reportsBasePath, executionId.toString()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(reportDir);
        } catch (Exception e) {
            log.error("Failed to create report directory: {}", reportDir, e);
        }

        // Initialize variables and context in the Bridge
        Map<String, Object> mergedVariables = new HashMap<>();
        WorkflowAst ast = workflow.getWorkflowAst();

        // 1. Environment Properties
        String selectedEnv = (environment != null && !environment.isBlank()) ? environment : "default";
        if (ast != null && ast.getEnvProperties() != null) {
            Map<String, Object> envVars = ast.getEnvProperties().get(selectedEnv);
            if (envVars != null) {
                mergedVariables.putAll(envVars);
                mergedVariables.put("env", envVars);
            }
        }

        // 2. Global Workflow Variables
        if (ast != null && ast.getVariables() != null) {
            mergedVariables.putAll(ast.getVariables());
        }

        // 3. Secrets (Inject into context & register for redaction)
        if (ast != null && ast.getSecrets() != null && !ast.getSecrets().isEmpty()) {
            mergedVariables.putAll(ast.getSecrets());
            logBroadcaster.registerSecrets(executionId, ast.getSecrets().values());
        }

        // 4. Trigger Request Initial Variables
        if (initialVariables != null) {
            mergedVariables.putAll(initialVariables);
        }

        // 5. Evaluate Flow-Level Headers & Dynamic Headers (once per execution!)
        Map<String, String> resolvedFlowHeaders = new LinkedHashMap<>();
        if (ast != null && ast.getDefaultHeaders() != null) {
            for (Map.Entry<String, String> entry : ast.getDefaultHeaders().entrySet()) {
                String resolvedVal = contextEngine.resolveString(entry.getValue(), mergedVariables);
                resolvedFlowHeaders.put(entry.getKey(), resolvedVal);
            }
        }
        if (ast != null && ast.getDynamicHeaders() != null) {
            for (Map.Entry<String, String> entry : ast.getDynamicHeaders().entrySet()) {
                // Dynamic header evaluated ONCE for the entire workflow run
                String resolvedVal = contextEngine.resolveString(entry.getValue(), mergedVariables);
                resolvedFlowHeaders.put(entry.getKey(), resolvedVal);
                // Also cache in context so steps can reference as ${headerName}
                mergedVariables.put(entry.getKey(), resolvedVal);
            }
        }

        // Build runtime payload for generic-flow-executor.feature
        Map<String, Object> workflowPayload = new HashMap<>();
        workflowPayload.put("executionId", executionId.toString());
        workflowPayload.put("workflowId", workflow.getId().toString());
        workflowPayload.put("workflowName", workflow.getName());
        workflowPayload.put("flowHeaders", resolvedFlowHeaders);

        List<?> stepList = Collections.emptyList();
        if (ast != null && ast.getSteps() != null) {
            stepList = objectMapper.convertValue(ast.getSteps(), List.class);
        }
        workflowPayload.put("steps", stepList);
        workflowPayload.put("initialVariables", mergedVariables);

        // Initialize payload and context in the Bridge
        DynamicKarateBridge.initExecution(executionId.toString(), workflowPayload);

        SuiteResult karateResults = null;
        try {
            // Execute Karate in-memory using generic master feature (Karate v2)
            karateResults = Runner.path("classpath:karate/generic-flow-executor.feature")
                    .configDir("classpath:karate")
                    .systemProperty("karate.executionId", executionId.toString())
                    .karateEnv(environment != null ? environment : "default")
                    .outputHtmlReport(true)
                    .outputDir(reportDir.toString())
                    .parallel(1);

        } catch (Exception e) {
            log.error("Exception during Karate execution run: {}", e.getMessage(), e);
            execution.setErrorMessage(e.getMessage());
        }

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        execution.setEndTime(Instant.ofEpochMilli(endTime));
        execution.setDurationMs(durationMs);

        // Fetch captured context snapshot and step results from the Bridge
        Map<String, Object> finalContext = DynamicKarateBridge.getExecutionContext(executionId.toString());
        List<ExecutionStepResult> stepResults = DynamicKarateBridge.getExecutionStepResults(executionId.toString());

        execution.setContextSnapshot(finalContext);
        execution.setStepResults(stepResults);

        // Determine final execution status
        boolean failed = (karateResults != null && karateResults.isFailed()) || execution.getErrorMessage() != null;
        if (!failed && stepResults.stream().anyMatch(s -> s.getStatus() == ExecutionStatus.FAILED)) {
            failed = true;
        }

        WorkflowExecutionEntity finalExecution = executionRepository.findById(executionId).orElse(currentExecution);
        finalExecution.setStatus(failed ? ExecutionStatus.FAILED : ExecutionStatus.COMPLETED);
        finalExecution.setDurationMs(durationMs);
        finalExecution.setEndTime(Instant.ofEpochMilli(endTime));
        finalExecution.setContextSnapshot(finalContext);
        finalExecution.setStepResults(stepResults);

        // Locate generated report file
        String relativeReportPath = "/api/v1/executions/" + executionId + "/report/karate-summary.html";
        File summaryHtml = new File(reportDir.toFile(), "karate-summary.html");
        if (summaryHtml.exists()) {
            finalExecution.setReportPath(relativeReportPath);
        } else {
            // Check for generic-flow-executor.html
            File featureHtml = new File(reportDir.toFile(), "karate.generic-flow-executor.html");
            if (featureHtml.exists()) {
                finalExecution.setReportPath("/api/v1/executions/" + executionId + "/report/karate.generic-flow-executor.html");
            } else {
                finalExecution.setReportPath(relativeReportPath);
            }
        }

        if (karateResults != null && karateResults.getErrors() != null && !karateResults.getErrors().isEmpty()) {
            String rawErrors = String.join("\n", karateResults.getErrors());
            finalExecution.setErrorMessage(logBroadcaster.maskSecrets(executionId, rawErrors));
        } else if (finalExecution.getErrorMessage() != null) {
            finalExecution.setErrorMessage(logBroadcaster.maskSecrets(executionId, finalExecution.getErrorMessage()));
        }

        WorkflowExecutionEntity saved = executionRepository.saveAndFlush(finalExecution);
        DynamicKarateBridge.cleanupExecution(executionId.toString());

        // Broadcast final execution finished event
        logBroadcaster.broadcast(new ExecutionEventDto(
                executionId, workflow.getId(), "WORKFLOW_FINISHED", null, null,
                saved.getStatus(),
                "Execution " + executionId + " finished with status: " + saved.getStatus() + " in " + durationMs + "ms",
                saved
        ));

        log.info("Dynamic Karate Execution finished [id={}, status={}, duration={}ms]",
                executionId, saved.getStatus(), durationMs);

        return saved;
    }

    public Path getReportDir(UUID executionId) {
        return Paths.get(reportsBasePath, executionId.toString()).toAbsolutePath().normalize();
    }
}
