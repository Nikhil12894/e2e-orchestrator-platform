package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DynamicKarateRunnerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private DynamicKarateRunner karateRunner;

    @Autowired
    private com.orchestrator.platform.service.execution.ExecutionLogBroadcaster logBroadcaster;

    @Test
    void testExecuteDynamicWorkflowWithKarate() {
        List<TestWorkflowEntity> workflows = workflowRepository.findAll();
        assertFalse(workflows.isEmpty(), "Workflow should have been seeded");

        TestWorkflowEntity workflow = workflows.get(0);

        // Adjust localhost URLs to use random port
        workflow.getWorkflowAst().getSteps().forEach(step -> {
            if (step.getHttpConfig() != null && step.getHttpConfig().getUrl() != null) {
                step.getHttpConfig().setUrl(step.getHttpConfig().getUrl().replace("8080", String.valueOf(port)));
            }
        });

        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowName(workflow.getName());
        execution.setStatus(ExecutionStatus.PENDING);
        execution = executionRepository.save(execution);

        WorkflowExecutionEntity finishedExecution = karateRunner.executeWorkflow(
                execution, workflow, new HashMap<>(), "test");

        assertNotNull(finishedExecution);
        assertEquals(ExecutionStatus.COMPLETED, finishedExecution.getStatus());
        assertTrue(finishedExecution.getDurationMs() > 0);
        assertNotNull(finishedExecution.getStepResults());
        assertFalse(finishedExecution.getStepResults().isEmpty());

        // Verify context snapshot contains extracted tokens
        Map<String, Object> snapshot = finishedExecution.getContextSnapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.containsKey("authToken") || snapshot.containsKey("step_auth.authToken"));

        // Verify HTML report was generated
        assertNotNull(finishedExecution.getReportPath());
        assertTrue(finishedExecution.getReportPath().contains("workflow-report.html"));
        File reportDir = karateRunner.getReportDir(finishedExecution.getId()).toFile();
        assertTrue(reportDir.exists(), "Report directory should exist");

        // Verify dedicated executive workflow profiling report was generated
        File workflowReport = new File(reportDir, "workflow-report.html");
        assertTrue(workflowReport.exists(), "workflow-report.html should be generated");
        assertTrue(workflowReport.length() > 500, "workflow-report.html should contain content");

        // Verify dynamic feature was generated with workflow name
        File featureFile = new File(reportDir, "workflow-execution.feature");
        assertTrue(featureFile.exists(), "workflow-execution.feature should be generated");
    }

    @Test
    void testWorkflowExecutionWithFlowHeadersEnvAndSecrets() {
        List<TestWorkflowEntity> workflows = workflowRepository.findAll();
        assertFalse(workflows.isEmpty(), "Workflow should have been seeded");

        TestWorkflowEntity workflow = workflows.get(0);

        // Configure default headers, dynamic headers, env properties and secrets
        java.util.LinkedHashMap<String, String> defaultHeaders = new java.util.LinkedHashMap<>();
        defaultHeaders.put("X-Custom-Flow", "FlowTestValue");
        workflow.getWorkflowAst().setDefaultHeaders(defaultHeaders);

        java.util.LinkedHashMap<String, String> dynamicHeaders = new java.util.LinkedHashMap<>();
        dynamicHeaders.put("X-Dynamic-Req-Id", "${$randomUuid}");
        dynamicHeaders.put("X-Dynamic-Time", "${$timestamp}");
        workflow.getWorkflowAst().setDynamicHeaders(dynamicHeaders);

        java.util.LinkedHashMap<String, java.util.Map<String, Object>> envProperties = new java.util.LinkedHashMap<>();
        envProperties.put("staging", Map.of("clusterRegion", "us-east-1", "rateLimit", 500));
        envProperties.put("default", Map.of("clusterRegion", "us-central-1"));
        workflow.getWorkflowAst().setEnvProperties(envProperties);

        java.util.LinkedHashMap<String, String> secrets = new java.util.LinkedHashMap<>();
        secrets.put("apiKey", "super-secret-token-value-9988");
        workflow.getWorkflowAst().setSecrets(secrets);

        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowName(workflow.getName());
        execution.setStatus(ExecutionStatus.PENDING);
        execution = executionRepository.save(execution);

        WorkflowExecutionEntity finishedExecution = karateRunner.executeWorkflow(
                execution, workflow, new HashMap<>(), "staging");

        assertNotNull(finishedExecution);
        assertEquals(ExecutionStatus.COMPLETED, finishedExecution.getStatus());

        Map<String, Object> snapshot = finishedExecution.getContextSnapshot();
        assertNotNull(snapshot);

        // Verify dynamic headers evaluated once and persisted in context
        assertTrue(snapshot.containsKey("X-Dynamic-Req-Id"));
        String dynamicId = String.valueOf(snapshot.get("X-Dynamic-Req-Id"));
        assertEquals(36, dynamicId.length(), "Should be a valid UUID");

        assertTrue(snapshot.containsKey("X-Dynamic-Time"));
        assertTrue(Long.parseLong(String.valueOf(snapshot.get("X-Dynamic-Time"))) > 0);

        // Verify environment property resolved for staging
        assertEquals("us-east-1", snapshot.get("clusterRegion"));
        assertEquals(500, snapshot.get("rateLimit"));

        // Verify secrets masking
        String maskedLog = logBroadcaster.maskSecrets(finishedExecution.getId(),
                "Authorization: Bearer super-secret-token-value-9988 sent to remote");
        assertEquals("Authorization: Bearer [REDACTED] sent to remote", maskedLog);
    }
}
