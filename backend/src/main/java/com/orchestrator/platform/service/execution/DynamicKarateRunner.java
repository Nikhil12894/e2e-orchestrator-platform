package com.orchestrator.platform.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.SetupWorkflowConfig;
import com.orchestrator.platform.domain.model.ast.SubWorkflowStepConfig;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.dto.ExecutionEventDto;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.WorkflowContextEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicKarateRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamicKarateRunner.class);

    @Value("${orchestrator.reports.base-path:./target/karate-reports}")
    private String reportsBasePath;

    private final WorkflowExecutionRepository executionRepository;
    private final ExecutionLogBroadcaster logBroadcaster;
    private final ObjectMapper objectMapper;
    private final WorkflowContextEngine contextEngine;
    private final WorkflowReportGenerator reportGenerator;
    private final Map<UUID, Path> activeReportDirs = new ConcurrentHashMap<>();

    public DynamicKarateRunner(WorkflowExecutionRepository executionRepository,
                               ExecutionLogBroadcaster logBroadcaster,
                               ObjectMapper objectMapper,
                               WorkflowContextEngine contextEngine,
                               WorkflowReportGenerator reportGenerator) {
        this.executionRepository = executionRepository;
        this.logBroadcaster = logBroadcaster;
        this.objectMapper = objectMapper;
        this.contextEngine = contextEngine;
        this.reportGenerator = reportGenerator;
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

        // Prepare execution report directory (with resilient fallback to java.io.tmpdir on permission errors)
        Path reportDir = resolveReportDir(executionId);

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

        // 3. Secrets (Environment-Scoped & Global Fallback with Log Redaction)
        Map<String, String> activeSecrets = new LinkedHashMap<>();
        if (ast != null && ast.getEnvSecrets() != null && ast.getEnvSecrets().containsKey("default")) {
            Map<String, String> defaultSec = ast.getEnvSecrets().get("default");
            if (defaultSec != null) activeSecrets.putAll(defaultSec);
        }
        if (ast != null && ast.getEnvSecrets() != null && ast.getEnvSecrets().containsKey(selectedEnv)) {
            Map<String, String> envSec = ast.getEnvSecrets().get(selectedEnv);
            if (envSec != null) activeSecrets.putAll(envSec);
        }
        if (ast != null && ast.getSecrets() != null && !ast.getSecrets().isEmpty()) {
            activeSecrets.putAll(ast.getSecrets());
        }

        if (!activeSecrets.isEmpty()) {
            mergedVariables.putAll(activeSecrets);
            mergedVariables.put("secrets", activeSecrets);
            logBroadcaster.registerSecrets(executionId, activeSecrets.values());
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

        List<WorkflowStep> executableSteps = new ArrayList<>();
        if (ast != null && ast.getSetupWorkflow() != null && ast.getSetupWorkflow().isEnabled()
                && ast.getSetupWorkflow().getWorkflowId() != null && !ast.getSetupWorkflow().getWorkflowId().isBlank()) {
            SetupWorkflowConfig setupCfg = ast.getSetupWorkflow();
            if (workflow.getId() != null && workflow.getId().toString().equals(setupCfg.getWorkflowId().trim())) {
                throw new IllegalArgumentException("Circular setup workflow reference detected: workflow cannot reference itself as setup workflow");
            }
            WorkflowStep setupStep = new WorkflowStep();
            setupStep.setId("step_setup_" + UUID.randomUUID().toString().replace("-", ""));
            setupStep.setName(setupCfg.getWorkflowName() != null ? setupCfg.getWorkflowName() : "Setup Workflow");
            setupStep.setType(StepType.SUB_WORKFLOW);
            setupStep.setEnabled(true);

            SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
            subConfig.setWorkflowId(setupCfg.getWorkflowId().trim());
            subConfig.setWorkflowName(setupCfg.getWorkflowName());
            subConfig.setInheritEnvAndSecrets(setupCfg.isInheritEnvAndSecrets());
            subConfig.setInheritParentContext(true);
            subConfig.setInheritFlowHeaders(true);
            setupStep.setSubWorkflowConfig(subConfig);
            if (setupCfg.getExtractions() != null) {
                setupStep.setExtractions(new ArrayList<>(setupCfg.getExtractions()));
            }
            executableSteps.add(setupStep);
        }
        if (ast != null && ast.getSteps() != null) {
            executableSteps.addAll(ast.getSteps());
        }

        List<?> stepList = objectMapper.convertValue(executableSteps, List.class);
        workflowPayload.put("steps", stepList);
        workflowPayload.put("initialVariables", mergedVariables);

        // Initialize payload and context in the Bridge
        DynamicKarateBridge.initExecution(executionId.toString(), workflowPayload);

        SuiteResult karateResults = null;
        try {
            // Dynamically generate feature attributed to this specific workflow in a dedicated staging directory
            Path featuresDir = resolveFeaturesDir(reportDir, executionId);
            Path featureFile = featuresDir.resolve("workflow-execution.feature");
            String featureContent = buildDynamicFeatureContent(workflow, environment);
            Files.writeString(featureFile, featureContent, StandardCharsets.UTF_8);

            // Execute Karate with workflow-attributed feature file (Karate v2)
            karateResults = Runner.path(featureFile.toAbsolutePath().toString())
                    .configDir("classpath:karate")
                    .systemProperty("karate.executionId", executionId.toString())
                    .karateEnv(environment != null ? environment : "default")
                    .outputHtmlReport(true)
                    .outputDir(reportDir.toString())
                    .parallel(1);

            // Copy feature file into reportDir for self-contained archiving
            Files.copy(featureFile, reportDir.resolve("workflow-execution.feature"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

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
        if (finalExecution.getStatus() != ExecutionStatus.CANCELLED) {
            finalExecution.setStatus(failed ? ExecutionStatus.FAILED : ExecutionStatus.COMPLETED);
        }
        finalExecution.setDurationMs(durationMs);
        finalExecution.setEndTime(Instant.ofEpochMilli(endTime));
        finalExecution.setContextSnapshot(sanitizeContextSnapshot(finalContext));
        finalExecution.setStepResults(stepResults);

        // Generate Executive Workflow Profiling & Performance Report
        File profilingReport = reportGenerator.generateReport(finalExecution, workflow, reportDir);
        if (profilingReport != null && profilingReport.exists()) {
            finalExecution.setReportPath("/api/v1/executions/" + executionId + "/report/workflow-report.html");
        } else {
            finalExecution.setReportPath("/api/v1/executions/" + executionId + "/report/karate-summary.html");
        }

        if (finalExecution.getStatus() != ExecutionStatus.CANCELLED) {
            if (karateResults != null && karateResults.getErrors() != null && !karateResults.getErrors().isEmpty()) {
                String rawErrors = String.join("\n", karateResults.getErrors());
                finalExecution.setErrorMessage(logBroadcaster.maskSecrets(executionId, rawErrors));
            } else if (finalExecution.getErrorMessage() != null) {
                finalExecution.setErrorMessage(logBroadcaster.maskSecrets(executionId, finalExecution.getErrorMessage()));
            }
        }

        WorkflowExecutionEntity saved;
        try {
            saved = executionRepository.saveAndFlush(finalExecution);
        } catch (Exception e) {
            log.error("Failed to save execution entity with contextSnapshot: {}. Retrying with stringified context...", e.getMessage());
            Map<String, Object> fallbackContext = new HashMap<>();
            if (finalExecution.getContextSnapshot() != null) {
                for (Map.Entry<String, Object> entry : finalExecution.getContextSnapshot().entrySet()) {
                    fallbackContext.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            finalExecution.setContextSnapshot(fallbackContext);
            saved = executionRepository.saveAndFlush(finalExecution);
        }

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

    private Path resolveReportDir(UUID executionId) {
        Path primaryDir = Paths.get(reportsBasePath, executionId.toString()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(primaryDir);
            activeReportDirs.put(executionId, primaryDir);
            return primaryDir;
        } catch (Exception e) {
            log.warn("Cannot create primary report directory at {} ({}). Falling back to system temporary directory.",
                    primaryDir, e.getMessage());
            Path fallbackDir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "orchestrator-reports", executionId.toString()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(fallbackDir);
                activeReportDirs.put(executionId, fallbackDir);
                return fallbackDir;
            } catch (Exception ex) {
                log.error("Failed to create fallback report directory {}: {}", fallbackDir, ex.getMessage(), ex);
                activeReportDirs.put(executionId, primaryDir);
                return primaryDir;
            }
        }
    }

    private Path resolveFeaturesDir(Path reportDir, UUID executionId) {
        try {
            Path featuresDir = reportDir.resolveSibling("karate-features").resolve(executionId.toString());
            Files.createDirectories(featuresDir);
            return featuresDir;
        } catch (Exception e) {
            log.warn("Cannot create features directory adjacent to {} ({}). Falling back to system temporary directory.",
                    reportDir, e.getMessage());
            Path fallbackFeaturesDir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "karate-features", executionId.toString()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(fallbackFeaturesDir);
                return fallbackFeaturesDir;
            } catch (Exception ex) {
                log.error("Failed to create fallback features directory {}: {}", fallbackFeaturesDir, ex.getMessage(), ex);
                return reportDir;
            }
        }
    }

    public Path getReportDir(UUID executionId) {
        if (executionId != null && activeReportDirs.containsKey(executionId)) {
            Path cached = activeReportDirs.get(executionId);
            if (Files.exists(cached)) {
                return cached;
            }
        }
        Path primary = Paths.get(reportsBasePath, executionId != null ? executionId.toString() : "").toAbsolutePath().normalize();
        if (Files.exists(primary)) {
            return primary;
        }
        Path fallback = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "orchestrator-reports", executionId != null ? executionId.toString() : "").toAbsolutePath().normalize();
        if (Files.exists(fallback)) {
            return fallback;
        }
        return primary;
    }

    public void deleteExecutionFiles(UUID executionId) {
        if (executionId == null) {
            return;
        }
        activeReportDirs.remove(executionId);

        // Clean configured reportsBasePath
        Path primaryDir = Paths.get(reportsBasePath, executionId.toString()).toAbsolutePath().normalize();
        Path primaryFeaturesDir = primaryDir.resolveSibling("karate-features").resolve(executionId.toString());
        cleanDir(primaryDir);
        cleanDir(primaryFeaturesDir);

        // Also clean up any fallback directories
        Path fallbackDir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "orchestrator-reports", executionId.toString()).toAbsolutePath().normalize();
        Path fallbackFeaturesDir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "karate-features", executionId.toString()).toAbsolutePath().normalize();
        cleanDir(fallbackDir);
        cleanDir(fallbackFeaturesDir);
    }

    private void cleanDir(Path path) {
        try {
            if (Files.exists(path)) {
                FileSystemUtils.deleteRecursively(path);
                log.info("Deleted directory: {}", path);
            }
        } catch (Exception e) {
            log.warn("Failed to delete directory {}: {}", path, e.getMessage());
        }
    }

    private String buildDynamicFeatureContent(TestWorkflowEntity workflow, String environment) {
        String cleanName = workflow.getName() != null ? workflow.getName().replace("\n", " ").trim() : "Workflow";
        int version = workflow.getVersion();
        String envStr = environment != null ? environment : "default";

        return """
@lock=workflow-execution
Feature: Workflow: %s (v%d) [Env: %s]

  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def executionId = karate.properties['karate.executionId'] || java.lang.System.getProperty('karate.executionId')
    * def payload = Bridge.getActiveWorkflowPayload(executionId)
    * def workflowId = payload.workflowId
    * def flowHeaders = payload.flowHeaders || {}
    * def steps = payload.steps || []
    * def globalContext = payload.initialVariables || {}

  Scenario: Execute Steps For Workflow: %s
    * def stepCount = karate.sizeOf(steps)
    * eval Bridge.onWorkflowStart(executionId, workflowId, stepCount)
    * def executeStepFeature = 'classpath:karate/execute-step.feature'
    * def runStep =
      \"\"\"
      function(stepItem, idx) {
        if (!stepItem.enabled && stepItem.enabled !== undefined) {
          karate.log('Skipping disabled step:', stepItem.id);
          return { skipped: true };
        }
        var callArgs = {
          step: stepItem,
          executionId: executionId,
          stepIndex: idx,
          flowHeaders: flowHeaders,
          globalContext: globalContext
        };
        var stepResult = karate.call(executeStepFeature, callArgs);
        if (stepResult && stepResult.extractedVars) {
          for (var key in stepResult.extractedVars) {
            globalContext[key] = stepResult.extractedVars[key];
          }
        }
        return stepResult;
      }
      \"\"\"
    * def results = karate.forEach(steps, runStep)
    * eval Bridge.onWorkflowComplete(executionId, globalContext)
""".formatted(cleanName, version, envStr, cleanName);
    }

    private Map<String, Object> sanitizeContextSnapshot(Map<String, Object> context) {
        if (context == null) return Collections.emptyMap();
        Map<String, Object> sanitized = new ConcurrentHashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getKey() == null) continue;
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return sanitized;
    }

    private Object sanitizeValue(Object val) {
        if (val == null) return null;
        if (val instanceof String || val instanceof Number || val instanceof Boolean) {
            return val;
        }
        if (val instanceof Map<?, ?> map) {
            Map<String, Object> safeMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    safeMap.put(String.valueOf(e.getKey()), sanitizeValue(e.getValue()));
                }
            }
            return safeMap;
        }
        if (val instanceof Collection<?> coll) {
            List<Object> safeList = new ArrayList<>(coll.size());
            for (Object item : coll) {
                safeList.add(sanitizeValue(item));
            }
            return safeList;
        }
        try {
            objectMapper.writeValueAsString(val);
            return val;
        } catch (Exception e) {
            return String.valueOf(val);
        }
    }
}
