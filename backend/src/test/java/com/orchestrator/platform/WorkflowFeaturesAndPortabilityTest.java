package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.enums.TriggerType;
import com.orchestrator.platform.domain.model.ast.HttpRequestStepConfig;
import com.orchestrator.platform.domain.model.ast.TransformStepConfig;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasNode;
import com.orchestrator.platform.domain.model.canvas.CanvasPosition;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.TestWorkflowExecutionService;
import com.orchestrator.platform.service.TestWorkflowService;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WorkflowFeaturesAndPortabilityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestWorkflowService workflowService;

    @Autowired
    private TestWorkflowExecutionService executionService;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private DynamicKarateRunner karateRunner;

    @Test
    @DisplayName("Verify Standalone Karate .feature export generation")
    public void testStandaloneKarateFeatureExport() {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Feature Export Test");
        req.setDescription("Tests .feature file generation for CI/CD");

        CanvasState state = new CanvasState();
        CanvasNode node1 = new CanvasNode();
        node1.setId("step_http");
        node1.setType("httpRequest");
        node1.setPosition(new CanvasPosition(100, 100));
        Map<String, Object> data1 = new HashMap<>();
        data1.put("name", "Health Check");
        data1.put("method", "GET");
        data1.put("url", "http://localhost:" + port + "/api/v1/mock/orders");
        data1.put("expectedStatus", 200);
        data1.put("retryCount", 3);
        data1.put("retryIntervalMs", 200);
        node1.setData(data1);

        CanvasNode node2 = new CanvasNode();
        node2.setId("step_muted");
        node2.setType("httpRequest");
        node2.setPosition(new CanvasPosition(100, 250));
        Map<String, Object> data2 = new HashMap<>();
        data2.put("name", "Skipped Muted Step");
        data2.put("enabled", false);
        node2.setData(data2);

        state.setNodes(List.of(node1, node2));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);

        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertNotNull(feature);
        assertTrue(feature.contains("Feature: Feature Export Test"), "Feature title must match");
        assertTrue(feature.contains("Given url 'http://localhost:" + port + "/api/v1/mock/orders'"), "Url must match");
        assertTrue(feature.contains("When method get"), "Method must be GET");
        assertTrue(feature.contains("Then status 200"), "Status check must exist");
        assertTrue(feature.contains("[MUTED / DISABLED] Scenario: Skipped Muted Step"), "Muted step should be commented out");
    }

    @Test
    @DisplayName("Verify Workflow Import creates a new workflow with '(Imported)' suffix")
    public void testWorkflowImport() {
        WorkflowCreateRequest importReq = new WorkflowCreateRequest();
        importReq.setName("Original Flow");
        importReq.setDescription("Import test description");
        importReq.setCanvasState(new CanvasState());

        TestWorkflowEntity imported = workflowService.importWorkflow(importReq);
        assertNotNull(imported.getId());
        assertEquals("Original Flow (Imported)", imported.getName());
        assertEquals("Import test description", imported.getDescription());
    }

    @Test
    @DisplayName("Verify Execution Cancellation marks run as CANCELLED")
    public void testCancelRunningExecution() throws InterruptedException {
        // Create workflow with a 5-second delay
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Cancel Flow Test");
        CanvasState state = new CanvasState();
        CanvasNode node = new CanvasNode();
        node.setId("step_delay");
        node.setType("delay");
        node.setPosition(new CanvasPosition(100, 100));
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Long Pause");
        data.put("delayMs", 5000L);
        node.setData(data);
        state.setNodes(List.of(node));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);

        WorkflowExecutionRequest execReq = new WorkflowExecutionRequest();
        execReq.setTriggerType(TriggerType.MANUAL);
        WorkflowExecutionEntity run = executionService.triggerExecution(wf.getId(), execReq);
        assertNotNull(run.getId());

        // Cancel execution mid-flight
        Thread.sleep(100);
        WorkflowExecutionEntity cancelled = executionService.cancelExecution(run.getId());

        assertEquals(ExecutionStatus.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.getErrorMessage().contains("cancelled by user"));

        Optional<WorkflowExecutionEntity> inDb = executionRepository.findById(run.getId());
        assertTrue(inDb.isPresent());
        assertEquals(ExecutionStatus.CANCELLED, inDb.get().getStatus());
    }

    @Test
    @DisplayName("Verify JavaScript Transform step executes and injects variables into context")
    public void testTransformStepExecution() {
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName("Transform Runner Test");
        wf.setVersion(1);

        WorkflowAst ast = new WorkflowAst();
        WorkflowStep step = new WorkflowStep();
        step.setId("step_trans");
        step.setName("Generate Token");
        step.setType(StepType.TRANSFORM);
        TransformStepConfig config = new TransformStepConfig();
        config.setScript("function(ctx) { return { generatedKey: 'KEY-999', numVal: 42 }; }");
        step.setTransformConfig(config);
        ast.setSteps(List.of(step));
        wf.setWorkflowAst(ast);
        wf = workflowRepository.saveAndFlush(wf);

        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(wf.getId());
        exec.setWorkflowName(wf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.saveAndFlush(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, wf, new HashMap<>(), "default");
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());
        assertNotNull(finished.getContextSnapshot());
        assertEquals("KEY-999", finished.getContextSnapshot().get("generatedKey"));
        assertEquals(42, finished.getContextSnapshot().get("numVal"));
    }

    @Test
    @DisplayName("Verify Muted steps are skipped while active steps execute normally")
    public void testMutedStepExecutionSkipped() {
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName("Muted Step Runner Test");
        wf.setVersion(1);

        WorkflowAst ast = new WorkflowAst();

        // Step 1: Muted step (enabled = false)
        WorkflowStep step1 = new WorkflowStep();
        step1.setId("step_muted");
        step1.setName("Muted Transform Step");
        step1.setType(StepType.TRANSFORM);
        step1.setEnabled(false);
        TransformStepConfig config1 = new TransformStepConfig();
        config1.setScript("function(ctx) { return { mutedVar: 'SHOULD_NOT_EXIST' }; }");
        step1.setTransformConfig(config1);

        // Step 2: Active step (enabled = true)
        WorkflowStep step2 = new WorkflowStep();
        step2.setId("step_active");
        step2.setName("Active Transform Step");
        step2.setType(StepType.TRANSFORM);
        step2.setEnabled(true);
        TransformStepConfig config2 = new TransformStepConfig();
        config2.setScript("function(ctx) { return { activeVar: 'EXISTS_OK' }; }");
        step2.setTransformConfig(config2);

        ast.setSteps(List.of(step1, step2));
        wf.setWorkflowAst(ast);
        wf = workflowRepository.saveAndFlush(wf);

        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(wf.getId());
        exec.setWorkflowName(wf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.saveAndFlush(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, wf, new HashMap<>(), "default");
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());
        assertNotNull(finished.getContextSnapshot());
        assertFalse(finished.getContextSnapshot().containsKey("mutedVar"), "Muted step variable must not be in context");
        assertEquals("EXISTS_OK", finished.getContextSnapshot().get("activeVar"));
    }

    @Test
    @DisplayName("Verify HTTP Step Retry configuration is preserved and exported")
    public void testStepRetryConfiguration() {
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName("Retry Config Flow");
        wf.setVersion(1);

        WorkflowAst ast = new WorkflowAst();
        WorkflowStep step = new WorkflowStep();
        step.setId("step_retry");
        step.setName("Flaky Endpoint Call");
        step.setType(StepType.HTTP_REQUEST);
        step.setEnabled(true);

        HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
        httpConfig.setUrl("https://api.example.com/status");
        httpConfig.setMethod("GET");
        httpConfig.setRetryCount(3);
        httpConfig.setRetryIntervalMs(1200);
        step.setHttpConfig(httpConfig);

        ast.setSteps(List.of(step));
        wf.setWorkflowAst(ast);
        wf = workflowRepository.saveAndFlush(wf);

        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertTrue(feature.contains("configure retry = { count: 3, interval: 1200 }"));
        assertTrue(feature.contains("retry until responseStatus =="));
    }
}
