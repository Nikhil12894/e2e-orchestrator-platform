package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.HttpRequestStepConfig;
import com.orchestrator.platform.domain.model.ast.SetupWorkflowConfig;
import com.orchestrator.platform.domain.model.ast.SubWorkflowStepConfig;
import com.orchestrator.platform.domain.model.ast.TransformStepConfig;
import com.orchestrator.platform.domain.model.ast.VariableExtraction;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.TestWorkflowService;
import com.orchestrator.platform.service.execution.DynamicKarateBridge;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = E2EOrchestratorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SubWorkflowCompositionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private DynamicKarateRunner karateRunner;

    @Autowired
    private TestWorkflowService workflowService;

    @Test
    @DisplayName("Verify Sub-Workflow execution and child variable extraction into parent workflow context")
    public void testSubWorkflowExecutionAndVariableExtraction() {
        // 1. Create and persist Child Workflow
        TestWorkflowEntity childWf = new TestWorkflowEntity();
        childWf.setName("Child Workflow - Auth & Order");
        childWf.setDescription("Generates auth token and order ID");

        WorkflowAst childAst = new WorkflowAst();
        WorkflowStep childStep = new WorkflowStep();
        childStep.setId("step_child_transform");
        childStep.setName("Generate Child Data");
        childStep.setType(StepType.TRANSFORM);
        TransformStepConfig childTransform = new TransformStepConfig();
        childTransform.setScript("function(ctx) { return { authToken: 'CHILD-TOKEN-123', orderId: 'ORD-777' }; }");
        childStep.setTransformConfig(childTransform);
        childAst.setSteps(List.of(childStep));
        childWf.setWorkflowAst(childAst);
        childWf.setCanvasState(new CanvasState());
        childWf = workflowRepository.save(childWf);

        // 2. Create and persist Parent Workflow with SUB_WORKFLOW step
        TestWorkflowEntity parentWf = new TestWorkflowEntity();
        parentWf.setName("Parent Workflow - Orchestrator");
        parentWf.setDescription("Calls child workflow and consumes its extracted variables");

        WorkflowAst parentAst = new WorkflowAst();

        // Step 1: Sub-Workflow step
        WorkflowStep subWfStep = new WorkflowStep();
        subWfStep.setId("step_call_sub");
        subWfStep.setName("Execute Child Workflow");
        subWfStep.setType(StepType.SUB_WORKFLOW);
        SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
        subConfig.setWorkflowId(childWf.getId().toString());
        subConfig.setWorkflowName(childWf.getName());
        subConfig.setInheritParentContext(true);
        subWfStep.setSubWorkflowConfig(subConfig);

        // Extractions from child outputs
        VariableExtraction extToken = new VariableExtraction("extractedToken", "SUB_WORKFLOW_VARIABLE", "authToken", null);
        VariableExtraction extOrder = new VariableExtraction("extractedOrder", "SUB_WORKFLOW_VARIABLE", "orderId", null);
        subWfStep.setExtractions(List.of(extToken, extOrder));

        // Step 2: Parent step using extracted variables
        WorkflowStep verifyStep = new WorkflowStep();
        verifyStep.setId("step_verify_parent");
        verifyStep.setName("Verify Extracted Child Variables");
        verifyStep.setType(StepType.TRANSFORM);
        TransformStepConfig verifyTransform = new TransformStepConfig();
        verifyTransform.setScript("function(ctx) { " +
                "  return { " +
                "    verifiedToken: (ctx.extractedToken === 'CHILD-TOKEN-123'), " +
                "    verifiedOrder: (ctx.extractedOrder === 'ORD-777'), " +
                "    combinedResult: ctx.extractedOrder + ':' + ctx.extractedToken " +
                "  }; " +
                "}");
        verifyStep.setTransformConfig(verifyTransform);

        parentAst.setSteps(List.of(subWfStep, verifyStep));
        parentWf.setWorkflowAst(parentAst);
        parentWf.setCanvasState(new CanvasState());
        parentWf = workflowRepository.save(parentWf);

        // 3. Execute Parent Workflow
        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(parentWf.getId());
        exec.setWorkflowName(parentWf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.save(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, parentWf, new HashMap<>(), "default");

        // 4. Assertions
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());
        Map<String, Object> snapshot = finished.getContextSnapshot();
        assertNotNull(snapshot);

        assertEquals("CHILD-TOKEN-123", snapshot.get("extractedToken"));
        assertEquals("ORD-777", snapshot.get("extractedOrder"));
        assertEquals(true, snapshot.get("verifiedToken"));
        assertEquals(true, snapshot.get("verifiedOrder"));
        assertEquals("ORD-777:CHILD-TOKEN-123", snapshot.get("combinedResult"));
    }

    @Test
    @DisplayName("Verify deep inheritance of environment properties and secrets in Sub-Workflow")
    public void testSubWorkflowDeepInheritanceOfEnvAndSecrets() {
        // 1. Create Child Workflow reading env and secrets
        TestWorkflowEntity childWf = new TestWorkflowEntity();
        childWf.setName("Child Workflow - Env & Secrets Consumer");

        WorkflowAst childAst = new WorkflowAst();
        WorkflowStep childStep = new WorkflowStep();
        childStep.setId("step_child_env_check");
        childStep.setName("Read Env and Secret");
        childStep.setType(StepType.TRANSFORM);
        TransformStepConfig childTransform = new TransformStepConfig();
        childTransform.setScript("function(ctx) { " +
                "  var envHost = ctx.env ? ctx.env.DB_HOST : 'NOT_FOUND'; " +
                "  var secretVal = ctx.secrets ? ctx.secrets.API_KEY : 'NOT_FOUND'; " +
                "  return { childCapturedHost: envHost, childCapturedSecret: secretVal }; " +
                "}");
        childStep.setTransformConfig(childTransform);
        childAst.setSteps(List.of(childStep));
        childWf.setWorkflowAst(childAst);
        childWf.setCanvasState(new CanvasState());
        childWf = workflowRepository.save(childWf);

        // 2. Create Parent Workflow with environment scoped properties and secrets
        TestWorkflowEntity parentWf = new TestWorkflowEntity();
        parentWf.setName("Parent Workflow - Scoped Env Provider");

        WorkflowAst parentAst = new WorkflowAst();
        Map<String, Object> stagingEnvProps = new HashMap<>();
        stagingEnvProps.put("DB_HOST", "staging-db.internal");
        parentAst.setEnvProperties(Map.of("staging", stagingEnvProps));

        Map<String, String> stagingSecrets = new HashMap<>();
        stagingSecrets.put("API_KEY", "SuperSecretKey999");
        parentAst.setEnvSecrets(Map.of("staging", stagingSecrets));

        WorkflowStep subWfStep = new WorkflowStep();
        subWfStep.setId("step_call_sub_env");
        subWfStep.setName("Execute Child with Inherited Env/Secrets");
        subWfStep.setType(StepType.SUB_WORKFLOW);
        SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
        subConfig.setWorkflowId(childWf.getId().toString());
        subConfig.setWorkflowName(childWf.getName());
        subConfig.setInheritEnvAndSecrets(true);
        subWfStep.setSubWorkflowConfig(subConfig);

        VariableExtraction extHost = new VariableExtraction("inheritedHost", "SUB_WORKFLOW_VARIABLE", "childCapturedHost", null);
        VariableExtraction extSecret = new VariableExtraction("inheritedSecret", "SUB_WORKFLOW_VARIABLE", "childCapturedSecret", null);
        subWfStep.setExtractions(List.of(extHost, extSecret));

        parentAst.setSteps(List.of(subWfStep));
        parentWf.setWorkflowAst(parentAst);
        parentWf = workflowRepository.save(parentWf);

        // 3. Execute in 'staging' environment
        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(parentWf.getId());
        exec.setWorkflowName(parentWf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.save(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, parentWf, new HashMap<>(), "staging");

        // 4. Verify child received staging env properties and secrets
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());
        Map<String, Object> snapshot = finished.getContextSnapshot();
        assertNotNull(snapshot);
        assertEquals("staging-db.internal", snapshot.get("inheritedHost"));
        assertEquals("SuperSecretKey999", snapshot.get("inheritedSecret"));
    }

    @Test
    @DisplayName("Verify input variable overrides passed to Sub-Workflow")
    public void testSubWorkflowInputVariableOverrides() {
        // 1. Create Child Workflow that uses custom input variables
        TestWorkflowEntity childWf = new TestWorkflowEntity();
        childWf.setName("Child Workflow - Custom Inputs");

        WorkflowAst childAst = new WorkflowAst();
        WorkflowStep childStep = new WorkflowStep();
        childStep.setId("step_child_greet");
        childStep.setName("Greet User");
        childStep.setType(StepType.TRANSFORM);
        TransformStepConfig childTransform = new TransformStepConfig();
        childTransform.setScript("function(ctx) { " +
                "  return { message: (ctx.greeting || 'Hi') + ' ' + (ctx.username || 'Guest') }; " +
                "}");
        childStep.setTransformConfig(childTransform);
        childAst.setSteps(List.of(childStep));
        childWf.setWorkflowAst(childAst);
        childWf.setCanvasState(new CanvasState());
        childWf = workflowRepository.save(childWf);

        // 2. Create Parent Workflow with step 1 defining parent var and step 2 calling sub-workflow with input variables
        TestWorkflowEntity parentWf = new TestWorkflowEntity();
        parentWf.setName("Parent Workflow - Parameterized");

        WorkflowAst parentAst = new WorkflowAst();
        WorkflowStep step1 = new WorkflowStep();
        step1.setId("step_parent_init");
        step1.setName("Init Parent Vars");
        step1.setType(StepType.TRANSFORM);
        TransformStepConfig initTrans = new TransformStepConfig();
        initTrans.setScript("function(ctx) { return { dynamicUser: 'Alice' }; }");
        step1.setTransformConfig(initTrans);

        WorkflowStep step2 = new WorkflowStep();
        step2.setId("step_call_sub_params");
        step2.setName("Call Child with Params");
        step2.setType(StepType.SUB_WORKFLOW);
        SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
        subConfig.setWorkflowId(childWf.getId().toString());
        subConfig.setWorkflowName(childWf.getName());
        subConfig.setInputVariables(Map.of(
                "greeting", "Welcome",
                "username", "${dynamicUser}"
        ));
        step2.setSubWorkflowConfig(subConfig);

        VariableExtraction extMessage = new VariableExtraction("receivedGreeting", "SUB_WORKFLOW_VARIABLE", "message", null);
        step2.setExtractions(List.of(extMessage));

        parentAst.setSteps(List.of(step1, step2));
        parentWf.setWorkflowAst(parentAst);
        parentWf.setCanvasState(new CanvasState());
        parentWf = workflowRepository.save(parentWf);

        // 3. Execute
        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(parentWf.getId());
        exec.setWorkflowName(parentWf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.save(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, parentWf, new HashMap<>(), "default");

        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());
        Map<String, Object> snapshot = finished.getContextSnapshot();
        assertNotNull(snapshot);
        assertEquals("Welcome Alice", snapshot.get("receivedGreeting"));
    }

    @Test
    @DisplayName("Verify circular sub-workflow call detection guards against infinite recursion")
    public void testCircularSubWorkflowCallDetected() {
        UUID rootWfId = UUID.randomUUID();
        String execId = UUID.randomUUID().toString();

        Map<String, Object> rawStep = new HashMap<>();
        rawStep.put("id", "step_recurse");
        rawStep.put("name", "Self Recursive Step");
        rawStep.put("type", "SUB_WORKFLOW");

        SubWorkflowStepConfig config = new SubWorkflowStepConfig();
        config.setWorkflowId(rootWfId.toString());
        rawStep.put("subWorkflowConfig", config);

        // Simulate context where rootWfId is in active payload / call stack
        DynamicKarateBridge.initExecution(execId, Map.of("workflowId", (Object) rootWfId.toString()));

        Map<String, Object> prepResult = DynamicKarateBridge.prepareSubWorkflow(execId, rawStep, new HashMap<>());
        assertNotNull(prepResult);
        assertEquals(false, prepResult.get("success"));
        assertTrue(String.valueOf(prepResult.get("errorMessage")).contains("Circular sub-workflow call detected"),
                "Must report circular sub-workflow call");
    }

    @Test
    @DisplayName("Verify dynamic global header resolution with variables extracted from preceding sub-workflow step")
    public void testDynamicGlobalHeaderVariableResolution() {
        // 1. Create Child Auth Workflow
        TestWorkflowEntity childWf = new TestWorkflowEntity();
        childWf.setName("Child Auth Setup");
        childWf.setDescription("Produces authToken");
        WorkflowAst childAst = new WorkflowAst();
        WorkflowStep childTransform = new WorkflowStep();
        childTransform.setId("step_child_auth");
        childTransform.setName("Generate Token");
        childTransform.setType(StepType.TRANSFORM);
        TransformStepConfig childCfg = new TransformStepConfig();
        childCfg.setScript("function(ctx) { return { authToken: 'JWT-TOKEN-XYZ' }; }");
        childTransform.setTransformConfig(childCfg);
        childAst.setSteps(List.of(childTransform));
        childWf.setWorkflowAst(childAst);
        childWf.setCanvasState(new CanvasState());
        childWf = workflowRepository.save(childWf);

        // 2. Create Parent Workflow with defaultHeader using ${authToken}
        TestWorkflowEntity parentWf = new TestWorkflowEntity();
        parentWf.setName("Parent Dynamic Header Test");
        WorkflowAst parentAst = new WorkflowAst();
        parentAst.setDefaultHeaders(Map.of("Authorization", "Bearer ${authToken}", "X-Static", "CustomVal"));

        // Step 1: Sub-workflow call extracting authToken
        WorkflowStep subWfStep = new WorkflowStep();
        subWfStep.setId("step_call_auth");
        subWfStep.setName("Execute Auth Child");
        subWfStep.setType(StepType.SUB_WORKFLOW);
        SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
        subConfig.setWorkflowId(childWf.getId().toString());
        subConfig.setWorkflowName(childWf.getName());
        subConfig.setInheritParentContext(true);
        subWfStep.setSubWorkflowConfig(subConfig);
        subWfStep.setExtractions(List.of(new VariableExtraction("authToken", "SUB_WORKFLOW_VARIABLE", "authToken", null)));

        // Step 2: HTTP request step to mock endpoint inheriting default headers
        WorkflowStep httpStep = new WorkflowStep();
        httpStep.setId("step_http_orders");
        httpStep.setName("Create Order with Bearer Token");
        httpStep.setType(StepType.HTTP_REQUEST);
        HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
        httpConfig.setUrl("http://localhost:" + port + "/api/v1/mock/orders");
        httpConfig.setMethod("POST");
        httpConfig.setExpectedStatus(201);
        httpConfig.setBody(Map.of("userId", "user-dynamic-header"));
        httpStep.setHttpConfig(httpConfig);

        parentAst.setSteps(List.of(subWfStep, httpStep));
        parentWf.setWorkflowAst(parentAst);
        parentWf.setCanvasState(new CanvasState());
        parentWf = workflowRepository.save(parentWf);

        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(parentWf.getId());
        exec.setWorkflowName(parentWf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.save(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, parentWf, new HashMap<>(), "default");
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());

        List<ExecutionStepResult> stepResults = finished.getStepResults();
        assertNotNull(stepResults);
        assertEquals(3, stepResults.size(), "Should contain sub-workflow step, child inner step, and parent HTTP step");

        ExecutionStepResult httpResult = stepResults.stream()
                .filter(s -> "step_http_orders".equals(s.getStepId()))
                .findFirst()
                .orElse(null);
        assertNotNull(httpResult, "HTTP step result must be present");
        assertEquals(ExecutionStatus.COMPLETED, httpResult.getStatus());
        Map<String, Object> reqHeaders = httpResult.getRequestHeaders();
        assertNotNull(reqHeaders);
        assertEquals("Bearer JWT-TOKEN-XYZ", reqHeaders.get("Authorization"),
                "Authorization header must be dynamically evaluated with extracted authToken");
        assertEquals("CustomVal", reqHeaders.get("X-Static"));
    }

    @Test
    @DisplayName("Verify pre-requisite setup workflow execution and variable promotion into global context")
    public void testSetupWorkflowExecutionAndPromotion() {
        // 1. Create Child Setup Workflow
        TestWorkflowEntity setupChild = new TestWorkflowEntity();
        setupChild.setName("Prerequisite Setup Workflow");
        WorkflowAst setupAst = new WorkflowAst();
        WorkflowStep setupTransform = new WorkflowStep();
        setupTransform.setId("step_gen_setup");
        setupTransform.setName("Generate Session and Tenant");
        setupTransform.setType(StepType.TRANSFORM);
        TransformStepConfig transCfg = new TransformStepConfig();
        transCfg.setScript("function(ctx) { return { setupSessionToken: 'SESSION-ABC-999', tenantCode: 'ACME-CORP' }; }");
        setupTransform.setTransformConfig(transCfg);
        setupAst.setSteps(List.of(setupTransform));
        setupChild.setWorkflowAst(setupAst);
        setupChild.setCanvasState(new CanvasState());
        setupChild = workflowRepository.save(setupChild);

        // 2. Create Parent Workflow with SetupWorkflowConfig and dynamic default header
        TestWorkflowEntity parentWf = new TestWorkflowEntity();
        parentWf.setName("Parent Workflow with Setup Pre-requisite");
        WorkflowAst parentAst = new WorkflowAst();
        parentAst.setDefaultHeaders(Map.of("X-Tenant-Header", "${promotedTenant}", "X-Session-Header", "${promotedSession}"));

        SetupWorkflowConfig setupConfig = new SetupWorkflowConfig(setupChild.getId().toString(), setupChild.getName());
        setupConfig.setEnabled(true);
        setupConfig.setExtractions(List.of(
                new VariableExtraction("promotedSession", "SUB_WORKFLOW_VARIABLE", "setupSessionToken", null),
                new VariableExtraction("promotedTenant", "SUB_WORKFLOW_VARIABLE", "tenantCode", null)
        ));
        parentAst.setSetupWorkflow(setupConfig);

        // Canvas step 1: HTTP request step to mock endpoint
        WorkflowStep httpStep = new WorkflowStep();
        httpStep.setId("step_canvas_http");
        httpStep.setName("HTTP Order Request");
        httpStep.setType(StepType.HTTP_REQUEST);
        HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
        httpConfig.setUrl("http://localhost:" + port + "/api/v1/mock/orders");
        httpConfig.setMethod("POST");
        httpConfig.setExpectedStatus(201);
        httpConfig.setBody(Map.of("userId", "user-setup-test"));
        httpStep.setHttpConfig(httpConfig);

        parentAst.setSteps(List.of(httpStep));
        parentWf.setWorkflowAst(parentAst);
        parentWf.setCanvasState(new CanvasState());
        parentWf = workflowRepository.save(parentWf);

        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(parentWf.getId());
        exec.setWorkflowName(parentWf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.save(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, parentWf, new HashMap<>(), "default");
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());

        Map<String, Object> snapshot = finished.getContextSnapshot();
        assertNotNull(snapshot);
        assertEquals("SESSION-ABC-999", snapshot.get("promotedSession"));
        assertEquals("ACME-CORP", snapshot.get("promotedTenant"));

        // Setup step prepended + child inner step + Canvas HTTP step = 3 step results
        List<ExecutionStepResult> stepResults = finished.getStepResults();
        assertEquals(3, stepResults.size(), "Should have prepended setup step + child inner step + canvas HTTP step");

        ExecutionStepResult httpResult = stepResults.stream()
                .filter(s -> "step_canvas_http".equals(s.getStepId()))
                .findFirst().orElse(null);
        assertNotNull(httpResult);
        assertEquals("ACME-CORP", httpResult.getRequestHeaders().get("X-Tenant-Header"));
        assertEquals("SESSION-ABC-999", httpResult.getRequestHeaders().get("X-Session-Header"));
    }

    @Test
    @DisplayName("Verify standalone Karate feature export produces dynamic header function and inlines setup workflow")
    public void testStandaloneKarateDynamicHeaderExport() {
        // 1. Create Child Setup Workflow
        TestWorkflowEntity setupWf = new TestWorkflowEntity();
        setupWf.setName("Auth Seed Setup");
        WorkflowAst setupAst = new WorkflowAst();
        WorkflowStep setupTransform = new WorkflowStep();
        setupTransform.setId("step_seed_auth");
        setupTransform.setName("Seed Token");
        setupTransform.setType(StepType.TRANSFORM);
        TransformStepConfig transCfg = new TransformStepConfig();
        transCfg.setScript("function(ctx) { return { authToken: 'EXPORT-TOKEN-123' }; }");
        setupTransform.setTransformConfig(transCfg);
        setupAst.setSteps(List.of(setupTransform));
        setupWf.setWorkflowAst(setupAst);
        setupWf.setCanvasState(new CanvasState());
        setupWf = workflowRepository.save(setupWf);

        // 2. Create Parent Workflow with defaultHeaders and setupWorkflow
        TestWorkflowEntity parentWf = new TestWorkflowEntity();
        parentWf.setName("Export Parity Workflow");
        WorkflowAst parentAst = new WorkflowAst();
        parentAst.setDefaultHeaders(Map.of("Authorization", "Bearer ${authToken}", "X-App", "Meterflow"));

        SetupWorkflowConfig setupCfg = new SetupWorkflowConfig(setupWf.getId().toString(), setupWf.getName());
        setupCfg.setEnabled(true);
        setupCfg.setExtractions(List.of(new VariableExtraction("authToken", "SUB_WORKFLOW_VARIABLE", "authToken", null)));
        parentAst.setSetupWorkflow(setupCfg);

        WorkflowStep httpStep = new WorkflowStep();
        httpStep.setId("step_export_http");
        httpStep.setName("Execute API Request");
        httpStep.setType(StepType.HTTP_REQUEST);
        HttpRequestStepConfig httpCfg = new HttpRequestStepConfig();
        httpCfg.setUrl("http://localhost:8080/api/v1/mock/orders");
        httpCfg.setMethod("POST");
        httpCfg.setExpectedStatus(201);
        httpStep.setHttpConfig(httpCfg);
        parentAst.setSteps(List.of(httpStep));

        parentWf.setWorkflowAst(parentAst);
        parentWf.setCanvasState(new CanvasState());
        parentWf = workflowRepository.save(parentWf);

        String featureText = workflowService.generateStandaloneKarateFeature(parentWf.getId());
        assertNotNull(featureText);

        // Verify dynamic header function in Background
        assertTrue(featureText.contains("resolveDynamicHeader"), "Must define resolveDynamicHeader function");
        assertTrue(featureText.contains("configure headers ="), "Must configure headers using dynamic function");
        assertTrue(featureText.contains("resolveDynamicHeader('Bearer ${authToken}')"), "Must evaluate dynamic Authorization header");

        // Verify setup workflow inlining in Scenario
        assertTrue(featureText.contains("Begin Setup Workflow: Auth Seed Setup"), "Must inline setup workflow steps");
        assertTrue(featureText.contains("Variable Extractions from Setup Workflow"), "Must extract setup workflow variables into context");
        assertTrue(featureText.contains("def authToken = karate.get('authToken')"), "Must extract authToken from child");
        assertTrue(featureText.contains("End Setup Workflow: Auth Seed Setup"), "Must complete setup workflow block");
    }
}
