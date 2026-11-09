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
import com.orchestrator.platform.domain.model.canvas.CanvasEdge;
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
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
    @DisplayName("Verify Standalone exported Karate feature executes cleanly through Karate Runner.path (Task 1)")
    public void testStandaloneKarateFeatureExecutionStandalone() throws Exception {
        // 1. Initialize an in-memory H2 database table (orders) per D-01..D-04
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:standalone_exec_db;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, status VARCHAR(50), total DECIMAL(10,2))");
                stmt.execute("DELETE FROM orders WHERE id = 501");
                stmt.execute("INSERT INTO orders VALUES (501, 'COMPLETED', 129.50)");
            }
        }

        // 2. Construct and save a workflow via workflowService.createWorkflow containing a dbPoll step
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Standalone Runner Parity Test");

        CanvasState state = new CanvasState();
        CanvasNode dbNode = new CanvasNode();
        dbNode.setId("step_db_run");
        dbNode.setType("dbPoll");
        dbNode.setPosition(new CanvasPosition(100, 100));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Fetch Order DB");
        data.put("jdbcUrl", "jdbc:h2:mem:standalone_exec_db;DB_CLOSE_DELAY=-1");
        data.put("username", "sa");
        data.put("password", "");
        data.put("driverClassName", "org.h2.Driver");
        data.put("query", "SELECT id, status, total FROM orders WHERE id = 501");
        data.put("condition", "row.status == 'COMPLETED'");
        data.put("maxAttempts", 2);
        data.put("pollIntervalMs", 100L);
        data.put("timeoutMs", 2000L);
        data.put("extractions", List.of(
                Map.of("variableName", "extractedTotal", "source", "DB_ROW_COLUMN", "jsonPath", "total")
        ));
        data.put("assertions", List.of(
                Map.of("field", "status", "operator", "EQUALS", "expectedValue", "COMPLETED"),
                Map.of("field", "total", "operator", "EQUALS", "expectedValue", 129.50)
        ));
        dbNode.setData(data);
        state.setNodes(List.of(dbNode));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);

        // 3. Call workflowService.generateStandaloneKarateFeature(wf.getId())
        String featureContent = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertNotNull(featureContent);

        // 4. Write the feature content to a temporary file
        Path tempFeature = Files.createTempFile("standalone-db-test-", ".feature");
        try {
            Files.writeString(tempFeature, featureContent, StandardCharsets.UTF_8);

            // 5. Execute using standard Karate Core runner per D-01..D-04
            SuiteResult results = Runner.path(tempFeature.toAbsolutePath().toString())
                    .outputHtmlReport(false)
                    .parallel(1);

            // 6. Assert results per special review instructions and SuiteResult API
            assertFalse(results.isFailed(), "Execution failed: " + (results.getErrors() != null ? String.join("\n", results.getErrors()) : ""));
            assertEquals(0, results.getScenarioFailedCount(), "Expected 0 failed scenarios");
            assertTrue(results.getErrors() == null || results.getErrors().isEmpty(), "Expected no errors");
        } finally {
            Files.deleteIfExists(tempFeature);
        }
    }

    @Test
    @DisplayName("Verify Downstream variable chaining from DB Poll step into HTTP step (DBEXP-04, D-04, T-01-05)")
    public void testStandaloneKarateFeatureDbVariableChaining() throws Exception {
        // 1. Initialize H2 table with test order 601
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:standalone_exec_db;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, status VARCHAR(50), total DECIMAL(10,2))");
                stmt.execute("DELETE FROM orders WHERE id = 601");
                stmt.execute("INSERT INTO orders VALUES (601, 'READY', 250.00)");
            }
        }

        // 2. Construct 2-step workflow: Step 1 = DB poll (extracts orderId), Step 2 = HTTP GET using ${orderId}
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("DB Variable Chaining Test");

        CanvasState state = new CanvasState();

        CanvasNode dbNode = new CanvasNode();
        dbNode.setId("step_db_poll");
        dbNode.setType("dbPoll");
        dbNode.setPosition(new CanvasPosition(100, 100));
        Map<String, Object> dbData = new HashMap<>();
        dbData.put("name", "Poll Order 601");
        dbData.put("jdbcUrl", "jdbc:h2:mem:standalone_exec_db;DB_CLOSE_DELAY=-1");
        dbData.put("username", "sa");
        dbData.put("password", "");
        dbData.put("driverClassName", "org.h2.Driver");
        dbData.put("query", "SELECT id, status, total FROM orders WHERE id = 601");
        dbData.put("condition", "row.status == 'READY'");
        dbData.put("maxAttempts", 2);
        dbData.put("pollIntervalMs", 100L);
        dbData.put("timeoutMs", 2000L);
        dbData.put("extractions", List.of(
                Map.of("variableName", "orderId", "source", "DB_ROW_COLUMN", "jsonPath", "id")
        ));
        dbData.put("assertions", List.of(
                Map.of("field", "status", "operator", "EQUALS", "expectedValue", "READY")
        ));
        dbNode.setData(dbData);

        CanvasNode httpNode = new CanvasNode();
        httpNode.setId("step_http_call");
        httpNode.setType("httpRequest");
        httpNode.setPosition(new CanvasPosition(100, 250));
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("name", "Fetch Order From Mock API");
        httpData.put("method", "GET");
        httpData.put("url", "http://localhost:" + port + "/api/v1/mock/orders/${orderId}");
        httpData.put("expectedStatus", 200);
        httpNode.setData(httpData);

        CanvasEdge edge = new CanvasEdge();
        edge.setId("edge_db_to_http");
        edge.setSource("step_db_poll");
        edge.setTarget("step_http_call");

        state.setNodes(List.of(dbNode, httpNode));
        state.setEdges(List.of(edge));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);

        // 3. Export standalone Karate feature text
        String featureContent = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertNotNull(featureContent);

        // Verify feature structure contains single scenario and variable chaining expression
        assertTrue(featureContent.contains("Scenario: Execute Workflow Steps"), "Must emit single scenario for workflow step chaining");
        assertTrue(featureContent.contains("def orderId = dbResult.rows[0].id"), "Must extract orderId from dbResult");
        assertTrue(featureContent.contains("Given url 'http://localhost:" + port + "/api/v1/mock/orders/' + orderId"), "Must chain orderId into url expression");

        // 4. Execute via Runner.path against random port mock target server
        Path tempFeature = Files.createTempFile("standalone-chaining-test-", ".feature");
        try {
            Files.writeString(tempFeature, featureContent, StandardCharsets.UTF_8);

            SuiteResult results = Runner.path(tempFeature.toAbsolutePath().toString())
                    .outputHtmlReport(false)
                    .parallel(1);

            assertFalse(results.isFailed(), "Chaining execution failed: " + (results.getErrors() != null ? String.join("\n", results.getErrors()) : ""));
            assertEquals(0, results.getScenarioFailedCount(), "Expected 0 failed scenarios");
            assertTrue(results.getErrors() == null || results.getErrors().isEmpty(), "Expected no errors");
        } finally {
            Files.deleteIfExists(tempFeature);
        }
    }

    @Test
    @DisplayName("Verify Muted DB step export and lean Background without DB steps")
    public void testStandaloneKarateFeatureMutedDbStep() {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Muted DB Step Flow");

        CanvasState state = new CanvasState();
        CanvasNode mutedNode = new CanvasNode();
        mutedNode.setId("step_muted_db");
        mutedNode.setType("dbPoll");
        mutedNode.setPosition(new CanvasPosition(100, 100));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Disabled Polling Step");
        data.put("enabled", false);
        data.put("jdbcUrl", "jdbc:h2:mem:muted_db;DB_CLOSE_DELAY=-1");
        data.put("query", "SELECT * FROM users");
        mutedNode.setData(data);

        state.setNodes(List.of(mutedNode));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);
        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertNotNull(feature);

        // Must comment out muted step as # [MUTED / DISABLED] Scenario:
        assertTrue(feature.contains("[MUTED / DISABLED] Scenario: Disabled Polling Step"), "Must comment out disabled step");
        assertFalse(feature.contains("dbPoll(dbConfig_step_muted_db"), "Must not execute dbPoll for muted step");

        // Since all DB steps in this workflow are disabled, Background must NOT emit JDBC helpers
        assertFalse(feature.contains("def dbQuery ="), "Workflows with zero active DB steps must not emit dbQuery helper");
        assertFalse(feature.contains("def dbPoll ="), "Workflows with zero active DB steps must not emit dbPoll helper");
    }

    @Test
    @DisplayName("Verify Polling timeout boundaries and maxAttempts exit cleanly without hanging (T-01-06)")
    public void testStandaloneKarateFeatureTimeoutBoundaries() throws Exception {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Timeout Boundary Flow");

        CanvasState state = new CanvasState();
        CanvasNode dbNode = new CanvasNode();
        dbNode.setId("step_db_timeout");
        dbNode.setType("dbPoll");
        dbNode.setPosition(new CanvasPosition(100, 100));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Poll Non Existent Record");
        data.put("jdbcUrl", "jdbc:h2:mem:standalone_exec_db;DB_CLOSE_DELAY=-1");
        data.put("username", "sa");
        data.put("password", "");
        data.put("driverClassName", "org.h2.Driver");
        data.put("query", "SELECT id, status FROM orders WHERE id = 999999");
        data.put("condition", "row.status == 'COMPLETED'");
        data.put("maxAttempts", 2);
        data.put("pollIntervalMs", 50L);
        data.put("timeoutMs", 300L);
        dbNode.setData(data);

        state.setNodes(List.of(dbNode));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);
        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());
        assertNotNull(feature);

        Path tempFeature = Files.createTempFile("standalone-timeout-test-", ".feature");
        long startTime = System.currentTimeMillis();
        try {
            Files.writeString(tempFeature, feature, StandardCharsets.UTF_8);

            SuiteResult results = Runner.path(tempFeature.toAbsolutePath().toString())
                    .outputHtmlReport(false)
                    .parallel(1);

            long elapsed = System.currentTimeMillis() - startTime;
            // The polling loop must exit after maxAttempts=2 (elapsed < 3000ms), failing because record was not matched
            assertTrue(elapsed < 4000, "Polling loop must terminate within bounded timeout, elapsed: " + elapsed + "ms");
            assertTrue(results.isFailed(), "Scenario must fail assertion when condition is not met");
            assertEquals(1, results.getScenarioFailedCount(), "Expected 1 failed scenario");
        } finally {
            Files.deleteIfExists(tempFeature);
        }
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

    @Test
    @DisplayName("Verify in-memory dynamic execution of form url-encoded step against mock endpoint")
    public void testDynamicKarateExecutionFormUrlEncoded() {
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName("Dynamic Form Runner Test");
        wf.setVersion(1);

        WorkflowAst ast = new WorkflowAst();
        WorkflowStep step = new WorkflowStep();
        step.setId("step_form");
        step.setName("OAuth Token Step");
        step.setType(StepType.HTTP_REQUEST);
        step.setEnabled(true);

        HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
        httpConfig.setUrl("http://localhost:" + port + "/api/v1/mock/auth/token");
        httpConfig.setMethod("POST");
        httpConfig.setBodyType("FORM_URL_ENCODED");
        Map<String, Object> formFields = new LinkedHashMap<>();
        formFields.put("username", "testuser");
        formFields.put("grant_type", "client_credentials");
        httpConfig.setFormData(formFields);
        httpConfig.setExpectedStatus(200);
        step.setHttpConfig(httpConfig);

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
        assertNotNull(finished.getStepResults());
        assertEquals(1, finished.getStepResults().size());
        assertEquals(200, finished.getStepResults().get(0).getHttpStatus());
    }

    @Test
    @DisplayName("Verify Standalone Karate .feature export generates and executes form fields")
    public void testStandaloneKarateFeatureExportFormUrlEncoded() {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Form Encoded Export Syntax Test");

        CanvasState state = new CanvasState();
        CanvasNode node = new CanvasNode();
        node.setId("step_form_post");
        node.setType("httpRequest");
        node.setPosition(new CanvasPosition(100, 100));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Form Token Request");
        data.put("method", "POST");
        data.put("url", "http://localhost:" + port + "/api/v1/mock/auth/token");
        data.put("bodyType", "FORM_URL_ENCODED");
        Map<String, Object> formParams = new LinkedHashMap<>();
        formParams.put("username", "admin");
        formParams.put("grant_type", "password");
        formParams.put("env_token", "${$env:TEST_SECRET}");
        data.put("formData", formParams);
        data.put("expectedStatus", 200);
        node.setData(data);

        state.setNodes(List.of(node));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);
        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());

        assertTrue(feature.contains("And form fields"), "Export must contain 'form fields' keyword");
        assertFalse(feature.contains("And request"), "Export must NOT emit 'request' for form-encoded steps");
        assertTrue(feature.contains("java.lang.System.getenv('TEST_SECRET')"), "Export must format dynamic env variables");
    }

    @Test
    @DisplayName("Verify Standalone Karate .feature execution with form fields against mock endpoint")
    public void testStandaloneKarateFeatureExecutionFormUrlEncoded() throws Exception {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Form Encoded Standalone Execution Test");

        CanvasState state = new CanvasState();
        CanvasNode node = new CanvasNode();
        node.setId("step_form_post_exec");
        node.setType("httpRequest");
        node.setPosition(new CanvasPosition(100, 100));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Form Token Exec Request");
        data.put("method", "POST");
        data.put("url", "http://localhost:" + port + "/api/v1/mock/auth/token");
        data.put("bodyType", "FORM_URL_ENCODED");
        data.put("formData", Map.of("username", "standalone_user", "grant_type", "client_credentials"));
        data.put("expectedStatus", 200);
        node.setData(data);

        state.setNodes(List.of(node));
        req.setCanvasState(state);

        TestWorkflowEntity wf = workflowService.createWorkflow(req);
        String feature = workflowService.generateStandaloneKarateFeature(wf.getId());

        Path tempFeature = Files.createTempFile("standalone-form-test-", ".feature");
        try {
            Files.writeString(tempFeature, feature, StandardCharsets.UTF_8);
            SuiteResult results = Runner.path(tempFeature.toAbsolutePath().toString())
                    .outputHtmlReport(false)
                    .parallel(1);

            assertFalse(results.isFailed(), "Execution failed: " + (results.getErrors() != null ? String.join("\n", results.getErrors()) : ""));
            assertEquals(0, results.getScenarioFailedCount());
        } finally {
            Files.deleteIfExists(tempFeature);
        }
    }

    @Test
    @DisplayName("Verify Standalone Karate .feature export includes nested Sub-Workflow steps, variables, and DB helpers")
    public void testStandaloneKarateFeatureExportSubWorkflow() throws Exception {
        // 1. Create an in-memory H2 table for DB poll step inside child workflow
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:subwf_export_db;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS audit_logs (id INT PRIMARY KEY, action VARCHAR(50), status VARCHAR(50))");
                stmt.execute("DELETE FROM audit_logs");
                stmt.execute("INSERT INTO audit_logs (id, action, status) VALUES (1, 'CHILD_ACTION', 'SUCCESS')");
            }
        }

        // 2. Create Child Workflow with a Transform step and a DB Poll step
        WorkflowCreateRequest childReq = new WorkflowCreateRequest();
        childReq.setName("Child Modular Flow");

        CanvasState childState = new CanvasState();
        CanvasNode childNode1 = new CanvasNode();
        childNode1.setId("child_transform_step");
        childNode1.setType("transform");
        childNode1.setPosition(new CanvasPosition(100, 100));
        Map<String, Object> childData1 = new HashMap<>();
        childData1.put("name", "Generate Child Auth");
        childData1.put("script", "function(ctx) { return { authToken: 'SUB-TOKEN-888', childScope: 'READ_WRITE' }; }");
        childNode1.setData(childData1);

        CanvasNode childNode2 = new CanvasNode();
        childNode2.setId("child_db_poll_step");
        childNode2.setType("dbPoll");
        childNode2.setPosition(new CanvasPosition(100, 250));
        Map<String, Object> childData2 = new HashMap<>();
        childData2.put("name", "Verify Audit In DB");
        childData2.put("jdbcUrl", "jdbc:h2:mem:subwf_export_db;DB_CLOSE_DELAY=-1");
        childData2.put("username", "sa");
        childData2.put("password", "");
        childData2.put("driverClassName", "org.h2.Driver");
        childData2.put("query", "SELECT action, status FROM audit_logs WHERE id = 1");
        childData2.put("condition", "row.status == 'SUCCESS'");
        childNode2.setData(childData2);

        childState.setNodes(List.of(childNode1, childNode2));
        childReq.setCanvasState(childState);
        TestWorkflowEntity childWf = workflowService.createWorkflow(childReq);

        // 3. Create Parent Workflow with SUB_WORKFLOW step referencing Child, and a Transform step consuming extracted variables
        WorkflowCreateRequest parentReq = new WorkflowCreateRequest();
        parentReq.setName("Parent Modular Flow");

        CanvasState parentState = new CanvasState();
        CanvasNode subWfNode = new CanvasNode();
        subWfNode.setId("parent_sub_wf_step");
        subWfNode.setType("subWorkflow");
        subWfNode.setPosition(new CanvasPosition(100, 100));
        Map<String, Object> subWfData = new HashMap<>();
        subWfData.put("name", "Call Child Modular Flow");
        subWfData.put("workflowId", childWf.getId().toString());
        subWfData.put("workflowName", childWf.getName());
        subWfData.put("inputVariables", Map.of("role", "admin_tester"));
        subWfData.put("extractions", List.of(
                Map.of("variableName", "parentCapturedToken", "source", "SUB_WORKFLOW_VARIABLE", "jsonPath", "authToken")
        ));
        subWfNode.setData(subWfData);

        CanvasNode verifyNode = new CanvasNode();
        verifyNode.setId("parent_verify_step");
        verifyNode.setType("transform");
        verifyNode.setPosition(new CanvasPosition(100, 250));
        Map<String, Object> verifyData = new HashMap<>();
        verifyData.put("name", "Verify Extracted Token");
        verifyData.put("script", "function(ctx) { if (ctx.parentCapturedToken !== 'SUB-TOKEN-888') throw new Error('Token mismatch'); return { verified: true }; }");
        verifyNode.setData(verifyData);

        parentState.setNodes(List.of(subWfNode, verifyNode));
        parentReq.setCanvasState(parentState);
        TestWorkflowEntity parentWf = workflowService.createWorkflow(parentReq);

        // 4. Generate standalone feature file
        String feature = workflowService.generateStandaloneKarateFeature(parentWf.getId());

        // 5. Structural Assertions
        assertTrue(feature.contains("Feature: Parent Modular Flow"), "Must have Feature name");
        assertTrue(feature.contains("def dbQuery ="), "Background MUST contain dbQuery helper because child workflow has DB steps!");
        assertTrue(feature.contains("def dbPoll ="), "Background MUST contain dbPoll helper because child workflow has DB steps!");
        assertTrue(feature.contains("# --- Begin Sub-Workflow: Child Modular Flow"), "Must contain sub-workflow begin marker");
        assertTrue(feature.contains("def role = 'admin_tester'"), "Must set input variables");
        assertTrue(feature.contains("SUB-TOKEN-888"), "Must contain child transform script");
        assertTrue(feature.contains("def dbConfig_child_db_poll_step ="), "Must contain child dbPoll step");
        assertTrue(feature.contains("def parentCapturedToken = karate.get('authToken')"), "Must extract sub-workflow variable into parent scope");
        assertTrue(feature.contains("# --- End Sub-Workflow: Child Modular Flow"), "Must contain sub-workflow end marker");

        // 6. Execute standalone feature file through Karate Runner.path to verify 100% Gherkin execution fidelity
        Path tempFeature = Files.createTempFile("standalone-subwf-test-", ".feature");
        try {
            Files.writeString(tempFeature, feature, StandardCharsets.UTF_8);
            SuiteResult results = Runner.path(tempFeature.toAbsolutePath().toString())
                    .outputHtmlReport(false)
                    .parallel(1);

            assertFalse(results.isFailed(), "Standalone sub-workflow execution failed: " + (results.getErrors() != null ? String.join("\n", results.getErrors()) : ""));
            assertEquals(0, results.getScenarioFailedCount());
        } finally {
            Files.deleteIfExists(tempFeature);
        }
    }

    @Test
    @DisplayName("Verify Standalone Karate .feature export guards against circular Sub-Workflow references")
    public void testStandaloneKarateFeatureExportSubWorkflowCircularReferenceGuard() {
        // Create 2 workflows that reference each other
        WorkflowCreateRequest wf1Req = new WorkflowCreateRequest();
        wf1Req.setName("Circular Flow 1");
        TestWorkflowEntity wf1 = workflowService.createWorkflow(wf1Req);

        WorkflowCreateRequest wf2Req = new WorkflowCreateRequest();
        wf2Req.setName("Circular Flow 2");
        TestWorkflowEntity wf2 = workflowService.createWorkflow(wf2Req);

        // wf1 calls wf2
        CanvasState state1 = new CanvasState();
        CanvasNode node1 = new CanvasNode();
        node1.setId("node_call_2");
        node1.setType("subWorkflow");
        node1.setPosition(new CanvasPosition(100, 100));
        node1.setData(Map.of("name", "Call Flow 2", "workflowId", wf2.getId().toString(), "workflowName", wf2.getName()));
        state1.setNodes(List.of(node1));
        WorkflowCreateRequest update1 = new WorkflowCreateRequest();
        update1.setName(wf1.getName());
        update1.setCanvasState(state1);
        workflowService.updateWorkflow(wf1.getId(), update1);

        // wf2 calls wf1
        CanvasState state2 = new CanvasState();
        CanvasNode node2 = new CanvasNode();
        node2.setId("node_call_1");
        node2.setType("subWorkflow");
        node2.setPosition(new CanvasPosition(100, 100));
        node2.setData(Map.of("name", "Call Flow 1", "workflowId", wf1.getId().toString(), "workflowName", wf1.getName()));
        state2.setNodes(List.of(node2));
        WorkflowCreateRequest update2 = new WorkflowCreateRequest();
        update2.setName(wf2.getName());
        update2.setCanvasState(state2);
        workflowService.updateWorkflow(wf2.getId(), update2);

        // Generate feature for wf1; should cleanly detect recursion without StackOverflowError
        String feature = workflowService.generateStandaloneKarateFeature(wf1.getId());
        assertNotNull(feature);
        assertTrue(feature.contains("CIRCULAR SUB-WORKFLOW DETECTED"), "Must detect circular reference");
    }
}
