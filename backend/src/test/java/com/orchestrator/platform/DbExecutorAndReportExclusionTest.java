package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.config.HikariConnectionPoolManager;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.DbPollStepConfig;
import com.orchestrator.platform.domain.model.dto.DbConnectionTestResult;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import com.orchestrator.platform.service.WorkflowContextEngine;
import com.orchestrator.platform.service.execution.DbPollExecutor;
import com.orchestrator.platform.service.execution.DynamicKarateBridge;
import com.orchestrator.platform.service.execution.ExecutionLogBroadcaster;
import com.orchestrator.platform.service.execution.WorkflowReportGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DbExecutorAndReportExclusionTest {

    @Test
    @DisplayName("Verify Oracle Driver detection and classpath loading")
    public void testOracleDriverDetectionAndClasspath() throws Exception {
        // 1. Verify driver inference for Oracle
        String oracleDriver = HikariConnectionPoolManager.detectDriverClassName(
                "jdbc:oracle:thin:@//localhost:1521/XEPDB1", null);
        assertEquals("oracle.jdbc.OracleDriver", oracleDriver);

        // 2. Verify driver inference for other supported databases
        assertEquals("org.postgresql.Driver",
                HikariConnectionPoolManager.detectDriverClassName("jdbc:postgresql://localhost:5432/mydb", null));
        assertEquals("com.mysql.cj.jdbc.Driver",
                HikariConnectionPoolManager.detectDriverClassName("jdbc:mysql://localhost:3306/mydb", null));
        assertEquals("org.h2.Driver",
                HikariConnectionPoolManager.detectDriverClassName("jdbc:h2:mem:testdb", null));
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver",
                HikariConnectionPoolManager.detectDriverClassName("jdbc:sqlserver://localhost:1433;databaseName=test", null));

        // 3. Verify explicit driver override
        assertEquals("custom.driver.Driver",
                HikariConnectionPoolManager.detectDriverClassName("jdbc:oracle:thin:@...", "custom.driver.Driver"));

        // 4. Verify Oracle driver class is loaded from runtime classpath
        Class<?> driverClass = Class.forName("oracle.jdbc.OracleDriver");
        assertNotNull(driverClass, "oracle.jdbc.OracleDriver must be present in classpath");
    }

    @Test
    @DisplayName("Verify excludeFromReport omits auxiliary steps from report scorecard and pass rate")
    public void testExcludeFromReportMetrics() throws Exception {
        WorkflowReportGenerator generator = new WorkflowReportGenerator(new ObjectMapper());

        TestWorkflowEntity workflow = new TestWorkflowEntity();
        workflow.setId(UUID.randomUUID());
        workflow.setName("Order Checkout Flow with DB Seed");
        workflow.setVersion(1);

        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        UUID execId = UUID.randomUUID();
        execution.setId(execId);
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowName(workflow.getName());
        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setStartTime(Instant.now().minusSeconds(10));
        execution.setEndTime(Instant.now());
        execution.setDurationMs(1250L);

        List<ExecutionStepResult> stepResults = new ArrayList<>();

        // Step 1: Auxiliary Setup Step (Token Gen / DB Seed) - EXCLUDED FROM REPORT
        ExecutionStepResult auxStep = new ExecutionStepResult();
        auxStep.setStepId("step_aux_setup");
        auxStep.setStepName("Seed Order Preconditions in DB");
        auxStep.setStepType(StepType.DB_POLL);
        auxStep.setStatus(ExecutionStatus.COMPLETED);
        auxStep.setAssertionsPassed(true);
        auxStep.setDurationMs(150L);
        auxStep.setExcludeFromReport(true);
        stepResults.add(auxStep);

        // Step 2: Test Step 1 (HTTP Request) - PASSED
        ExecutionStepResult step1 = new ExecutionStepResult();
        step1.setStepId("step_test_1");
        step1.setStepName("Create Order API");
        step1.setStepType(StepType.HTTP_REQUEST);
        step1.setStatus(ExecutionStatus.COMPLETED);
        step1.setHttpStatus(201);
        step1.setAssertionsPassed(true);
        step1.setDurationMs(300L);
        step1.setExcludeFromReport(false);
        stepResults.add(step1);

        // Step 3: Test Step 2 (HTTP Request) - PASSED
        ExecutionStepResult step2 = new ExecutionStepResult();
        step2.setStepId("step_test_2");
        step2.setStepName("Process Payment API");
        step2.setStepType(StepType.HTTP_REQUEST);
        step2.setStatus(ExecutionStatus.COMPLETED);
        step2.setHttpStatus(200);
        step2.setAssertionsPassed(true);
        step2.setDurationMs(500L);
        step2.setExcludeFromReport(false);
        stepResults.add(step2);

        // Step 4: Test Step 3 (HTTP Request) - FAILED
        ExecutionStepResult step3 = new ExecutionStepResult();
        step3.setStepId("step_test_3");
        step3.setStepName("Verify Shipping Status");
        step3.setStepType(StepType.HTTP_REQUEST);
        step3.setStatus(ExecutionStatus.FAILED);
        step3.setHttpStatus(500);
        step3.setAssertionsPassed(false);
        step3.setDurationMs(300L);
        step3.setExcludeFromReport(false);
        stepResults.add(step3);

        execution.setStepResults(stepResults);

        Path tempReportDir = Files.createTempDirectory("test-report-" + execId);
        File reportFile = generator.generateReport(execution, workflow, tempReportDir);

        assertNotNull(reportFile);
        assertTrue(reportFile.exists());

        String reportHtml = Files.readString(reportFile.toPath());

        // Verification:
        // Total steps reported should be 3 (excluding the 1 auxiliary setup step)
        // Passed steps should be 2 out of 3
        assertTrue(reportHtml.contains("2 / 3"), "Scorecard must report 2 / 3 steps (excluding auxiliary step)");
        assertTrue(reportHtml.contains("67%"), "Success rate must be calculated on reportable steps (2/3 = 67%)");
        assertTrue(reportHtml.contains("1 auxiliary setup omitted"), "Must indicate that 1 auxiliary step was omitted from metrics");
        assertTrue(reportHtml.contains("UNCOUNTED / AUXILIARY"), "Diagnostics must tag uncounted auxiliary step");
    }

    @Test
    @DisplayName("Verify granular HTTP node header inheritance, selective exclusions, and custom overrides")
    public void testGranularHttpNodeHeaderInheritance() {
        Map<String, Object> flowHeaders = new LinkedHashMap<>();
        flowHeaders.put("Authorization", "Bearer global-jwt-token");
        flowHeaders.put("Content-Type", "application/json");
        flowHeaders.put("X-Tenant-Id", "tenant-alpha");

        // Case 1: Default behavior - Inherit all global headers
        Map<String, Object> stepConfig1 = new HashMap<>();
        stepConfig1.put("inheritGlobalHeaders", true);
        stepConfig1.put("headers", Map.of("X-Step-Id", "step-101"));

        Map<String, Object> effective1 = DynamicKarateBridge.computeEffectiveHeaders(flowHeaders, stepConfig1);
        assertEquals(4, effective1.size());
        assertEquals("Bearer global-jwt-token", effective1.get("Authorization"));
        assertEquals("application/json", effective1.get("Content-Type"));
        assertEquals("tenant-alpha", effective1.get("X-Tenant-Id"));
        assertEquals("step-101", effective1.get("X-Step-Id"));

        // Case 2: Do Not Inherit (Standalone / Public step)
        Map<String, Object> stepConfig2 = new HashMap<>();
        stepConfig2.put("inheritGlobalHeaders", false);
        stepConfig2.put("headers", Map.of("Content-Type", "application/x-www-form-urlencoded"));

        Map<String, Object> effective2 = DynamicKarateBridge.computeEffectiveHeaders(flowHeaders, stepConfig2);
        assertEquals(1, effective2.size(), "Must contain only step custom header, zero global headers");
        assertFalse(effective2.containsKey("Authorization"), "Global Authorization must not be inherited");
        assertFalse(effective2.containsKey("X-Tenant-Id"), "Global X-Tenant-Id must not be inherited");
        assertEquals("application/x-www-form-urlencoded", effective2.get("Content-Type"));

        // Case 3: Selective Exclude - Keep Content-Type & X-Tenant-Id, but omit Authorization
        Map<String, Object> stepConfig3 = new HashMap<>();
        stepConfig3.put("inheritGlobalHeaders", true);
        stepConfig3.put("excludedGlobalHeaders", List.of("authorization")); // case-insensitive check
        stepConfig3.put("headers", Map.of("X-Custom-Scope", "read:orders"));

        Map<String, Object> effective3 = DynamicKarateBridge.computeEffectiveHeaders(flowHeaders, stepConfig3);
        assertEquals(3, effective3.size());
        assertFalse(effective3.containsKey("Authorization"), "Excluded Authorization must be stripped");
        assertEquals("application/json", effective3.get("Content-Type"));
        assertEquals("tenant-alpha", effective3.get("X-Tenant-Id"));
        assertEquals("read:orders", effective3.get("X-Custom-Scope"));

        // Case 4: Override Global Header with different step value
        Map<String, Object> stepConfig4 = new HashMap<>();
        stepConfig4.put("inheritGlobalHeaders", true);
        stepConfig4.put("headers", Map.of("Authorization", "Bearer custom-admin-jwt"));

        Map<String, Object> effective4 = DynamicKarateBridge.computeEffectiveHeaders(flowHeaders, stepConfig4);
        assertEquals("Bearer custom-admin-jwt", effective4.get("Authorization"), "Step header must override global header");
        assertEquals("application/json", effective4.get("Content-Type"));
        assertEquals("tenant-alpha", effective4.get("X-Tenant-Id"));
    }

    @Test
    @DisplayName("Verify DB Poll halts after maxAttempts when condition is not satisfied")
    public void testDbPollMaxAttemptsEnforced() {
        HikariConnectionPoolManager poolManager = new HikariConnectionPoolManager();
        DbPollExecutor executor = new DbPollExecutor(poolManager);

        DbPollStepConfig config = new DbPollStepConfig();
        config.setJdbcUrl("jdbc:h2:mem:polltest_max_attempts;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setQuery("SELECT 1 AS num");
        config.setCondition("row.num == '999'"); // Unsatisfiable
        config.setMaxAttempts(3);
        config.setPollIntervalMs(50L);
        config.setTimeoutMs(30000L); // High timeout to ensure attempt limit triggers first

        DbPollExecutor.DbPollResult result = executor.execute(config);

        assertFalse(result.isMatched(), "Condition should not match");
        assertEquals(3, result.getAttempts(), "Attempts must equal maxAttempts (3)");
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("reached maximum retry attempts (3/3)"),
                "Error message must specify reached maximum retry attempts: " + result.getErrorMessage());
    }

    @Test
    @DisplayName("Verify DB Poll single-shot execution with maxAttempts=1 (no retries)")
    public void testDbPollSingleAttemptNoRetry() {
        HikariConnectionPoolManager poolManager = new HikariConnectionPoolManager();
        DbPollExecutor executor = new DbPollExecutor(poolManager);

        DbPollStepConfig config = new DbPollStepConfig();
        config.setJdbcUrl("jdbc:h2:mem:polltest_single_attempt;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setQuery("SELECT 42 AS answer");
        config.setCondition("row.answer == '42'");
        config.setMaxAttempts(1);
        config.setPollIntervalMs(500L);
        config.setTimeoutMs(10000L);

        DbPollExecutor.DbPollResult result = executor.execute(config);

        assertTrue(result.isMatched(), "Condition should match immediately");
        assertEquals(1, result.getAttempts(), "Single attempt must execute");
        assertNotNull(result.getLatestRow());
        assertEquals(42, ((Number) result.getLatestRow().get("ANSWER")).intValue());
    }

    @Test
    @DisplayName("Verify SQL value normalization converts Timestamps and Blobs to JSON-safe primitives")
    public void testNormalizeSqlValuesWithTimestampsAndBlobs() throws Exception {
        // 1. Timestamp to ISO String
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2026-11-05 16:40:52.123");
        Object normTs = HikariConnectionPoolManager.normalizeSqlValue(ts);
        assertTrue(normTs instanceof String);
        assertTrue(((String) normTs).contains("2026-11-05T"));

        // 2. Date to ISO Date String
        java.sql.Date d = java.sql.Date.valueOf("2026-11-05");
        assertEquals("2026-11-05", HikariConnectionPoolManager.normalizeSqlValue(d));

        // 3. Time to LocalTime String
        java.sql.Time t = java.sql.Time.valueOf("16:40:52");
        assertEquals("16:40:52", HikariConnectionPoolManager.normalizeSqlValue(t));

        // 4. byte[] to Base64 String
        byte[] bytes = "Hello Oracle".getBytes();
        Object normBytes = HikariConnectionPoolManager.normalizeSqlValue(bytes);
        assertEquals(Base64.getEncoder().encodeToString(bytes), normBytes);

        // 5. Test Jackson serializability of normalized map
        Map<String, Object> testRow = new HashMap<>();
        testRow.put("ID", 101);
        testRow.put("CREATED_TIME", normTs);
        testRow.put("PAYLOAD", normBytes);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(testRow);
        assertNotNull(json);
        assertTrue(json.contains("CREATED_TIME"));
        assertTrue(json.contains("2026-11-05T"));
    }

    @Test
    @DisplayName("Verify testConnection successfully connects to database and queries sample row")
    public void testTestDbConnectionSuccess() {
        HikariConnectionPoolManager poolManager = new HikariConnectionPoolManager();

        DbConnectionTestResult res = poolManager.testConnection(
                "jdbc:h2:mem:testconn_success;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "org.h2.Driver",
                "SELECT 999 AS code, 'ONLINE' AS health"
        );

        assertTrue(res.isSuccess(), "Connection should succeed");
        assertEquals("H2", res.getDatabaseProductName());
        assertNotNull(res.getLatencyMs());
        assertTrue(res.getLatencyMs() >= 0);
        assertNotNull(res.getSampleRow());
        assertEquals(999, ((Number) res.getSampleRow().get("CODE")).intValue());
        assertEquals("ONLINE", res.getSampleRow().get("HEALTH"));
    }

    @Test
    @DisplayName("Verify testConnection fails gracefully on invalid credentials without throwing unhandled exceptions")
    public void testTestDbConnectionInvalidUrl() {
        HikariConnectionPoolManager poolManager = new HikariConnectionPoolManager();

        // Initialize a secure database with credentials
        poolManager.testConnection(
                "jdbc:h2:mem:auth_test_db;USER=admin;PASSWORD=secret;DB_CLOSE_DELAY=-1",
                "admin",
                "secret",
                "org.h2.Driver",
                "SELECT 1"
        );

        // Attempt connection with invalid password
        DbConnectionTestResult res = poolManager.testConnection(
                "jdbc:h2:mem:auth_test_db;DB_CLOSE_DELAY=-1",
                "admin",
                "wrong_password",
                "org.h2.Driver",
                null
        );

        assertFalse(res.isSuccess(), "Connection with wrong password should fail");
        assertNotNull(res.getErrorMessage());
        assertTrue(res.getErrorMessage().length() > 0);
    }

    @Test
    @DisplayName("Verify DB Poll variable extraction handles case insensitivity, camelCase/snake_case, and row prefixes")
    public void testDbPollVariableExtractionCaseInsensitiveAndPrefixes() {
        Map<String, Object> oracleRow = new LinkedHashMap<>();
        oracleRow.put("REF_ID", "SR_2026113091911889_0452");
        oracleRow.put("STATUS", "COMPLETED");
        oracleRow.put("ORDER_ID", 101);
        oracleRow.put("TOTAL_AMOUNT", 199.98);

        // 1. Exact uppercase match
        assertEquals("SR_2026113091911889_0452",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "REF_ID"));

        // 2. Lowercase match against uppercase Oracle column
        assertEquals("SR_2026113091911889_0452",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "ref_id"));

        // 3. camelCase match against uppercase snake_case Oracle column
        assertEquals("SR_2026113091911889_0452",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "refId"));
        assertEquals(199.98,
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "totalAmount"));

        // 4. Prefixed with row. or latestRow. or $.
        assertEquals("SR_2026113091911889_0452",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "row.ref_id"));
        assertEquals("COMPLETED",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "row.STATUS"));
        assertEquals("COMPLETED",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "latestRow.status"));
        assertEquals("COMPLETED",
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "$.STATUS"));
        assertEquals(101,
                DynamicKarateBridge.extractDbOrMapValue(oracleRow, "row.orderId"));

        // 5. Composite payload (as merged in execute-db.feature)
        Map<String, Object> composite = new LinkedHashMap<>(oracleRow);
        composite.put("row", oracleRow);
        composite.put("latestRow", oracleRow);
        composite.put("rows", List.of(oracleRow));
        composite.put("attempts", 1);
        composite.put("matched", true);

        assertEquals("COMPLETED", DynamicKarateBridge.extractDbOrMapValue(composite, "STATUS"));
        assertEquals("COMPLETED", DynamicKarateBridge.extractDbOrMapValue(composite, "status"));
        assertEquals("COMPLETED", DynamicKarateBridge.extractDbOrMapValue(composite, "row.status"));
        assertEquals("COMPLETED", DynamicKarateBridge.extractDbOrMapValue(composite, "latestRow.STATUS"));
    }

    @Test
    @DisplayName("Verify end-to-end DB Poll execution, variable extraction, and downstream variable chaining")
    public void testDbPollExecutionAndVariableExtractionChaining() {
        HikariConnectionPoolManager poolManager = new HikariConnectionPoolManager();
        DbPollExecutor executor = new DbPollExecutor(poolManager);

        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowContextEngine contextEngine = new WorkflowContextEngine(objectMapper);
        ExecutionLogBroadcaster logBroadcaster = new ExecutionLogBroadcaster(Optional.empty());
        new DynamicKarateBridge(contextEngine, executor, null, logBroadcaster, objectMapper);

        DbPollStepConfig config = new DbPollStepConfig();
        config.setJdbcUrl("jdbc:h2:mem:chain_test_db;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setQuery("SELECT 555 AS order_id, 'CONFIRMED' AS status, 'TRK-987654' AS tracking_code");
        config.setCondition("row.status == 'CONFIRMED'");
        config.setMaxAttempts(3);
        config.setPollIntervalMs(50L);
        config.setTimeoutMs(5000L);

        DbPollExecutor.DbPollResult result = executor.execute(config);
        assertTrue(result.isMatched(), "DB poll condition must match");
        assertNotNull(result.getLatestRow());

        // Composite payload matching execute-db.feature
        Map<String, Object> dbPayload = new LinkedHashMap<>(result.getLatestRow());
        dbPayload.put("row", result.getLatestRow());
        dbPayload.put("latestRow", result.getLatestRow());
        dbPayload.put("rows", result.getRows());
        dbPayload.put("attempts", result.getAttempts());
        dbPayload.put("matched", result.isMatched());

        String execId = UUID.randomUUID().toString();
        Map<String, Object> step = new HashMap<>();
        step.put("id", "step_db_poll");
        step.put("name", "Poll Database for Order");
        step.put("type", "DB_POLL");
        step.put("extractions", List.of(
                Map.of("variableName", "dbOrderStatus", "source", "DB_ROW_COLUMN", "jsonPath", "STATUS"),
                Map.of("variableName", "trackingCode", "source", "DB_ROW_COLUMN", "jsonPath", "tracking_code"),
                Map.of("variableName", "orderId", "source", "RESPONSE_BODY", "jsonPath", "row.order_id")
        ));

        Map<String, Object> extracted = DynamicKarateBridge.extractVariables(execId, step, dbPayload, 200, Collections.emptyMap());

        assertEquals("CONFIRMED", extracted.get("dbOrderStatus"));
        assertEquals("TRK-987654", extracted.get("trackingCode"));
        assertEquals(555, ((Number) extracted.get("orderId")).intValue());

        // Downstream step consumes extracted variables
        Map<String, Object> downstreamStep = new HashMap<>();
        downstreamStep.put("id", "step_verify");
        downstreamStep.put("name", "Verify Downstream");
        downstreamStep.put("type", "HTTP_REQUEST");
        downstreamStep.put("httpConfig", Map.of(
                "url", "http://localhost:8080/api/v1/mock/orders/${orderId}",
                "headers", Map.of("X-Tracking-Code", "${trackingCode}", "X-Status", "${dbOrderStatus}")
        ));

        Map<String, Object> resolved = DynamicKarateBridge.resolveStepVariables(execId, downstreamStep, extracted);
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedHttpCfg = (Map<String, Object>) resolved.get("httpConfig");
        assertEquals("http://localhost:8080/api/v1/mock/orders/555", resolvedHttpCfg.get("url"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedHeaders = (Map<String, Object>) resolvedHttpCfg.get("headers");
        assertEquals("TRK-987654", resolvedHeaders.get("X-Tracking-Code"));
        assertEquals("CONFIRMED", resolvedHeaders.get("X-Status"));

        // Cleanup
        DynamicKarateBridge.cleanupExecution(execId);
    }
}
