package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.config.HikariConnectionPoolManager;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import com.orchestrator.platform.service.execution.DynamicKarateBridge;
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
}
