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
import com.orchestrator.platform.service.WorkflowContextEngine;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import com.orchestrator.platform.service.execution.ExecutionLogBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
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

    @Autowired
    private ExecutionLogBroadcaster logBroadcaster;

    @Autowired
    private WorkflowContextEngine contextEngine;

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
        assertFalse(feature.contains("def dbQuery ="), "HTTP-only workflow must not emit dbQuery helper");
        assertFalse(feature.contains("def dbPoll ="), "HTTP-only workflow must not emit dbPoll helper");
    }

    @Test
    @DisplayName("Verify Standalone Karate .feature export generation for DB Poll steps (DBEXP-01..04)")
    public void testStandaloneKarateFeatureExportDbPoll() {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("DB Poll Export Flow");
        req.setDescription("Tests .feature file generation for DB polling steps");

        CanvasState state = new CanvasState();
        CanvasNode dbNode = new CanvasNode();
        dbNode.setId("step_db");
        dbNode.setType("dbPoll");
        dbNode.setPosition(new CanvasPosition(100, 100));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Poll User Table");
        data.put("jdbcUrl", "jdbc:h2:mem:export_test_db;DB_CLOSE_DELAY=-1");
        data.put("username", "sa");
        data.put("password", "");
        data.put("driverClassName", "org.h2.Driver");
        data.put("query", "SELECT id, status FROM users WHERE id = 1");
        data.put("condition", "row.status == 'ACTIVE'");
        data.put("maxAttempts", 3);
        data.put("pollIntervalMs", 250L);
        data.put("timeoutMs", 3000L);
        data.put("extractions", List.of(
                Map.of("variableName", "userStatus", "source", "DB_ROW_COLUMN", "jsonPath", "status"),
                Map.of("variableName", "userId", "source", "DB_ROW_COLUMN", "jsonPath", "$.id")
        ));
        data.put("assertions", List.of(
                Map.of("field", "status", "operator", "EQUALS", "expectedValue", "ACTIVE")
        ));
        dbNode.setData(data);

        // Node 2: DB Poll with expectedRowCount
        CanvasNode dbNode2 = new CanvasNode();
        dbNode2.setId("step_db_count");
        dbNode2.setType("dbPoll");
        dbNode2.setPosition(new CanvasPosition(100, 250));
        Map<String, Object> data2 = new HashMap<>();
        data2.put("name", "Poll Row Count");
        data2.put("jdbcUrl", "jdbc:h2:mem:export_test_db;DB_CLOSE_DELAY=-1");
        data2.put("query", "SELECT * FROM orders");
        data2.put("expectedRowCount", 5);
        dbNode2.setData(data2);

        // Node 3: DB Poll with compound condition (safeguard against == split corruption)
        CanvasNode dbNode3 = new CanvasNode();
        dbNode3.setId("step_db_compound");
        dbNode3.setType("dbPoll");
        dbNode3.setPosition(new CanvasPosition(100, 400));
        Map<String, Object> data3 = new HashMap<>();
        data3.put("name", "Poll Compound Condition");
        data3.put("jdbcUrl", "jdbc:h2:mem:export_test_db;DB_CLOSE_DELAY=-1");
        data3.put("query", "SELECT status, retry_count FROM tasks WHERE id = 10");
        data3.put("condition", "row.status == 'COMPLETED' && row.retry_count > 2");
        dbNode3.setData(data3);

        state.setNodes(List.of(dbNode, dbNode2, dbNode3));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);

        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertNotNull(feature);

        // DBEXP-01: Background contains self-contained JDBC functions
        assertTrue(feature.contains("Java.type('java.sql.DriverManager')"), "Must use standard DriverManager");
        assertTrue(feature.contains("def dbQuery ="), "Must define dbQuery helper");
        assertTrue(feature.contains("def dbPoll ="), "Must define dbPoll helper");
        assertFalse(feature.contains("Java.type('com.orchestrator.platform"), "Must not reference proprietary platform classes");
        assertTrue(feature.contains("str.replace(/\\$\\{([^}]+)\\}/g"), "Must contain token replacer regex");

        // DBEXP-02: Retry loop respecting maxAttempts, pollIntervalMs, and timeoutMs
        assertTrue(feature.contains("dbPoll(dbConfig_step_db, 3, 250, 3000"), "Must pass retry parameters to dbPoll");

        // DBEXP-03: Row match assertions and condition generation
        assertTrue(feature.contains("match dbResult.matched == true"), "Must assert matched == true");
        assertTrue(feature.contains("match dbResult.rows[0].status == 'ACTIVE'"), "Must assert column equality");
        assertTrue(feature.contains("return rows.length === 5;"), "Must emit expectedRowCount condition function");
        assertTrue(feature.contains("match karate.sizeOf(dbResult.rows) == 5"), "Must assert expectedRowCount");

        // Condition safeguard: Compound condition preserves expression without corrupted split
        assertTrue(feature.contains("return row.status == 'COMPLETED' && row.retry_count > 2;"),
                "Compound condition must evaluate directly without reference errors");

        // DBEXP-04: Variable extractions
        assertTrue(feature.contains("def userStatus = dbResult.rows[0].status"), "Must extract status column");
        assertTrue(feature.contains("def userId = dbResult.rows[0].id"), "Must extract stripped id column");
        assertTrue(feature.contains("def pollResult = dbResult"), "Must alias pollResult");
        assertTrue(feature.contains("def result = dbResult"), "Must alias result");
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

    @Test
    @DisplayName("Verify self-healing fallback when configured reports path throws AccessDeniedException")
    public void testSelfHealingFallbackWhenReportPathNotWritable() {
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName("Fallback Test Flow");
        wf.setVersion(1);

        WorkflowAst ast = new WorkflowAst();
        WorkflowStep step = new WorkflowStep();
        step.setId("step_fallback");
        step.setName("Transform Step");
        step.setType(StepType.TRANSFORM);
        step.setEnabled(true);
        TransformStepConfig transCfg = new TransformStepConfig();
        transCfg.setScript("function(ctx) { return { fallbackWorked: true }; }");
        step.setTransformConfig(transCfg);
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
        Path reportDir = karateRunner.getReportDir(finished.getId());
        assertNotNull(reportDir);
        assertTrue(java.nio.file.Files.exists(reportDir));
    }

    @Test
    void testEnvironmentScopedSecretsAndDbPollVariableResolution() {
        // 1. Create Workflow with envProperties and envSecrets
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName("Env Secrets & DB Var Test Flow");
        wf.setStatus("ACTIVE");

        WorkflowAst ast = new WorkflowAst();
        // Env properties
        Map<String, Map<String, Object>> envProperties = new LinkedHashMap<>();
        envProperties.put("default", Map.of("DB_HOST", "localhost", "DB_NAME", "dev_db"));
        envProperties.put("staging", Map.of("DB_HOST", "stg-db.internal", "DB_NAME", "stg_orders"));
        ast.setEnvProperties(envProperties);

        // Env secrets
        Map<String, Map<String, String>> envSecrets = new LinkedHashMap<>();
        envSecrets.put("default", Map.of("DB_PASS", "dev-pass-123", "SHARED_KEY", "shared-base-token"));
        envSecrets.put("staging", Map.of("DB_PASS", "stg-vault-secret-999"));
        ast.setEnvSecrets(envSecrets);

        // Add a delay step so workflow executes cleanly without requiring live remote Postgres
        WorkflowStep step = new WorkflowStep();
        step.setId("step_test");
        step.setName("Verify Env Secret Injection");
        step.setType(StepType.DELAY);
        step.setDelayMs(50L);
        step.setEnabled(true);
        ast.setSteps(List.of(step));

        wf.setWorkflowAst(ast);
        wf = workflowRepository.saveAndFlush(wf);

        // 2. Execute under 'staging' environment
        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(wf.getId());
        exec.setWorkflowName(wf.getName());
        exec.setStatus(ExecutionStatus.PENDING);
        exec = executionRepository.saveAndFlush(exec);

        WorkflowExecutionEntity finished = karateRunner.executeWorkflow(exec, wf, new HashMap<>(), "staging");
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());

        Map<String, Object> snapshot = finished.getContextSnapshot();
        assertNotNull(snapshot);

        // Verify staging envProperties took effect
        assertEquals("stg-db.internal", snapshot.get("DB_HOST"));
        assertEquals("stg_orders", snapshot.get("DB_NAME"));

        // Verify staging envSecrets overridden default DB_PASS
        assertEquals("stg-vault-secret-999", snapshot.get("DB_PASS"));

        // Verify SHARED_KEY was inherited from default envSecrets
        assertEquals("shared-base-token", snapshot.get("SHARED_KEY"));

        // Verify logBroadcaster masks staging secret and inherited secret
        String rawLog = "Connecting to stg-db.internal with pass stg-vault-secret-999 and token shared-base-token";
        String maskedLog = logBroadcaster.maskSecrets(finished.getId(), rawLog);
        assertEquals("Connecting to stg-db.internal with pass [REDACTED] and token [REDACTED]", maskedLog);

        // Verify ContextEngine resolves DB JDBC URL string with env and secret prefixes
        String resolvedJdbcUrl = contextEngine.resolveString(
                "jdbc:postgresql://${$env:DB_HOST}:5432/${$env:DB_NAME}?password=${$secret:DB_PASS}",
                snapshot
        );
        assertEquals("jdbc:postgresql://stg-db.internal:5432/stg_orders?password=stg-vault-secret-999", resolvedJdbcUrl);
    }
}
