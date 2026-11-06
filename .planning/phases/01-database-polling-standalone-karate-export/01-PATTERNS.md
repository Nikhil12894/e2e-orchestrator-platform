# Phase 1: Database Polling Standalone Karate Export - Pattern Map

**Mapped:** 2026-11-06  
**Files analyzed:** 2  
**Analogs found:** 2 / 2 (100% exact analog coverage)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` | service | transform | `TestWorkflowService.java` (lines 204-278) & `DynamicKarateRunner.java` (lines 341-385) | exact |
| `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` | test | batch | `WorkflowFeaturesAndPortabilityTest.java` (lines 65-106) & `DynamicKarateRunnerTest.java` (lines 41-90) | exact |

---

## Pattern Assignments

### 1. `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` (service, transform)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` (lines 204-278)  
**Supporting Analogs:**
- Feature templating & JS Background helpers: `DynamicKarateRunner.java` (lines 341-385)
- Driver class name detection: `HikariConnectionPoolManager.detectDriverClassName(...)` (`HikariConnectionPoolManager.java:232-250`)
- JSON Path prefix cleaning: `DynamicKarateBridge.extractDbOrMapValue(...)` (`DynamicKarateBridge.java:559-571`)
- DB polling execution & condition checking: `DbPollExecutor.java` (lines 49-160)

#### Imports Pattern
Existing imports in `TestWorkflowService.java` (lines 3-45):
```java
package com.orchestrator.platform.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.config.HikariConnectionPoolManager;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.DbPollStepConfig;
import com.orchestrator.platform.domain.model.ast.HttpRequestStepConfig;
import com.orchestrator.platform.domain.model.ast.StepAssertion;
import com.orchestrator.platform.domain.model.ast.VariableExtraction;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
```

#### Core Generation Pattern (generateStandaloneKarateFeature)
Existing standalone exporter structure in `TestWorkflowService.java` (lines 204-232, 266-277):
```java
public String generateStandaloneKarateFeature(UUID id) {
    TestWorkflowEntity workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + id));

    StringBuilder sb = new StringBuilder();
    String safeName = workflow.getName() != null ? workflow.getName().replace("\n", " ").trim() : "Workflow";
    sb.append("@e2e-workflow\n");
    sb.append("Feature: ").append(safeName).append(" (v").append(workflow.getVersion()).append(")\n\n");

    sb.append("  Background:\n");
    sb.append("    * configure ssl = true\n");

    WorkflowAst ast = workflow.getWorkflowAst();
    if (ast != null && ast.getDefaultHeaders() != null && !ast.getDefaultHeaders().isEmpty()) {
        for (Map.Entry<String, String> h : ast.getDefaultHeaders().entrySet()) {
            sb.append("    * configure headers = karate.merge(karate.get('headers', {}), { '")
                    .append(h.getKey()).append("': '").append(h.getValue()).append("' })\n");
        }
    }
    sb.append("\n");

    List<WorkflowStep> steps = ast != null && ast.getSteps() != null ? ast.getSteps() : Collections.emptyList();
    int stepIdx = 1;
    for (WorkflowStep step : steps) {
        if (!step.isEnabled()) {
            sb.append("  # [MUTED / DISABLED] Scenario: ").append(step.getName()).append("\n\n");
            continue;
        }
        sb.append("  Scenario: Step ").append(stepIdx++).append(" - ").append(step.getName()).append("\n");

        if (step.getType() == StepType.HTTP_REQUEST && step.getHttpConfig() != null) {
            // ... HTTP step generation ...
        } else if (step.getType() == StepType.DELAY) {
            sb.append("    * karate.pause(").append(step.getDelayMs() != null ? step.getDelayMs() : 1000).append(")\n");
        } else if (step.getType() == StepType.TRANSFORM && step.getTransformConfig() != null) {
            sb.append("    * def transformFn = ").append(step.getTransformConfig().getScript()).append("\n");
            sb.append("    * def transformResult = transformFn(karate.get('context', {}))\n");
        } else if (step.getType() == StepType.DB_POLL) {
            // [REPLACE CURRENT PLACEHOLDER WITH DB STEP GENERATION]
        }
        sb.append("\n");
    }

    return sb.toString();
}
```

#### Conditional Background Helper Emission Pattern
Analog from `DynamicKarateRunner.java` (lines 346-385) for JS helper blocks in feature Background:
```java
// Check whether any enabled step requires JDBC helpers
boolean hasDbSteps = steps.stream()
        .anyMatch(s -> s.getType() == StepType.DB_POLL && s.isEnabled());

if (hasDbSteps) {
    sb.append("    # Reusable Standalone JDBC Helper Functions\n");
    sb.append("    * def dbQuery =\n");
    sb.append("      \"\"\"\n");
    sb.append("      function(cfg) {\n");
    sb.append("        var DriverManager = Java.type('java.sql.DriverManager');\n");
    sb.append("        if (cfg.driverClassName) {\n");
    sb.append("          try { Java.type('java.lang.Class').forName(cfg.driverClassName); } catch(e) {}\n");
    sb.append("        }\n");
    sb.append("        var resolveToken = function(str) {\n");
    sb.append("          if (!str || str.indexOf('${') === -1) return str;\n");
    sb.append("          return str.replace(/\\\\$\\\\{([^}]+)\\\\}/g, function(match, key) {\n");
    sb.append("            var k = key.trim();\n");
    sb.append("            if (k.indexOf('$env:') === 0 || k.indexOf('env:') === 0) {\n");
    sb.append("              var ek = k.substring(k.indexOf(':') + 1).trim();\n");
    sb.append("              return karate.properties[ek] || java.lang.System.getenv(ek) || match;\n");
    sb.append("            }\n");
    sb.append("            if (k.indexOf('$secret:') === 0 || k.indexOf('secret:') === 0) {\n");
    sb.append("              var sk = k.substring(k.indexOf(':') + 1).trim();\n");
    sb.append("              return karate.properties[sk] || java.lang.System.getenv(sk) || match;\n");
    sb.append("            }\n");
    sb.append("            var val = karate.get(k);\n");
    sb.append("            return val !== null && val !== undefined ? val : match;\n");
    sb.append("          });\n");
    sb.append("        };\n");
    sb.append("        var url = resolveToken(cfg.jdbcUrl);\n");
    sb.append("        var user = resolveToken(cfg.username);\n");
    sb.append("        var pass = resolveToken(cfg.password || '');\n");
    sb.append("        var query = resolveToken(cfg.query);\n");
    sb.append("        var conn = null, stmt = null, rs = null, rows = [];\n");
    sb.append("        try {\n");
    sb.append("          conn = user ? DriverManager.getConnection(url, user, pass) : DriverManager.getConnection(url);\n");
    sb.append("          stmt = conn.createStatement();\n");
    sb.append("          rs = stmt.executeQuery(query);\n");
    sb.append("          var md = rs.getMetaData();\n");
    sb.append("          var colCount = md.getColumnCount();\n");
    sb.append("          while (rs.next()) {\n");
    sb.append("            var row = {};\n");
    sb.append("            for (var i = 1; i <= colCount; i++) {\n");
    sb.append("              var colName = md.getColumnLabel(i);\n");
    sb.append("              var val = rs.getObject(i);\n");
    sb.append("              row[colName] = val;\n");
    sb.append("              if (colName.toLowerCase() !== colName) {\n");
    sb.append("                row[colName.toLowerCase()] = val;\n");
    sb.append("              }\n");
    sb.append("            }\n");
    sb.append("            rows.push(row);\n");
    sb.append("          }\n");
    sb.append("        } catch(e) {\n");
    sb.append("          karate.log('JDBC query error:', e.message);\n");
    sb.append("        } finally {\n");
    sb.append("          if (rs) try { rs.close(); } catch(e) {}\n");
    sb.append("          if (stmt) try { stmt.close(); } catch(e) {}\n");
    sb.append("          if (conn) try { conn.close(); } catch(e) {}\n");
    sb.append("        }\n");
    sb.append("        return rows;\n");
    sb.append("      }\n");
    sb.append("      \"\"\"\n");
    sb.append("    * def dbPoll =\n");
    sb.append("      \"\"\"\n");
    sb.append("      function(cfg, maxAttempts, intervalMs, timeoutMs, conditionFn) {\n");
    sb.append("        var Thread = Java.type('java.lang.Thread');\n");
    sb.append("        var System = Java.type('java.lang.System');\n");
    sb.append("        var startTime = System.currentTimeMillis();\n");
    sb.append("        var attempts = 0;\n");
    sb.append("        var rows = [];\n");
    sb.append("        var matched = false;\n");
    sb.append("        while (attempts < maxAttempts && (System.currentTimeMillis() - startTime) < timeoutMs) {\n");
    sb.append("          attempts++;\n");
    sb.append("          try {\n");
    sb.append("            rows = dbQuery(cfg);\n");
    sb.append("          } catch(e) {\n");
    sb.append("            karate.log('DB Poll attempt ' + attempts + ' failed: ' + e.message);\n");
    sb.append("            rows = [];\n");
    sb.append("          }\n");
    sb.append("          if (conditionFn(rows)) {\n");
    sb.append("            matched = true;\n");
    sb.append("            break;\n");
    sb.append("          }\n");
    sb.append("          if (attempts < maxAttempts && (System.currentTimeMillis() - startTime + intervalMs) < timeoutMs) {\n");
    sb.append("            Thread.sleep(intervalMs);\n");
    sb.append("          }\n");
    sb.append("        }\n");
    sb.append("        var latest = rows.length > 0 ? rows[0] : {};\n");
    sb.append("        return {\n");
    sb.append("          matched: matched,\n");
    sb.append("          attempts: attempts,\n");
    sb.append("          durationMs: System.currentTimeMillis() - startTime,\n");
    sb.append("          rows: rows,\n");
    sb.append("          row: latest,\n");
    sb.append("          latestRow: latest\n");
    sb.append("        };\n");
    sb.append("      }\n");
    sb.append("      \"\"\"\n\n");
}
```

#### Driver Detection Pattern
Copy directly from `HikariConnectionPoolManager.java:232-250`:
```java
String driverClassName = HikariConnectionPoolManager.detectDriverClassName(
        dbConfig.getJdbcUrl(), dbConfig.getDriverClassName());
```

#### Variable Extraction Path Normalization Pattern
Copy directly from `DynamicKarateBridge.java:559-571`:
```java
private String cleanDbColumnPath(String jsonPath) {
    if (jsonPath == null) return "";
    String cleanPath = jsonPath.trim();
    if (cleanPath.startsWith("$.")) {
        cleanPath = cleanPath.substring(2);
    } else if (cleanPath.startsWith("$")) {
        cleanPath = cleanPath.substring(1);
    }
    if (cleanPath.startsWith("row.")) {
        cleanPath = cleanPath.substring(4);
    } else if (cleanPath.startsWith("latestRow.")) {
        cleanPath = cleanPath.substring(10);
    }
    return cleanPath;
}
```

#### DB Step Scenario Generation Pattern
Synthesizes `dbConfig`, condition JS, execution call, assertions, and extractions:
```java
} else if (step.getType() == StepType.DB_POLL && step.getDbPollConfig() != null) {
    DbPollStepConfig dbConfig = step.getDbPollConfig();
    String safeStepId = step.getId() != null ? step.getId().replaceAll("[^a-zA-Z0-9_]", "_") : "db_poll";

    Map<String, Object> cfgMap = new LinkedHashMap<>();
    cfgMap.put("jdbcUrl", dbConfig.getJdbcUrl() != null ? dbConfig.getJdbcUrl() : "");
    cfgMap.put("username", dbConfig.getUsername() != null ? dbConfig.getUsername() : "");
    cfgMap.put("password", dbConfig.getPassword() != null ? dbConfig.getPassword() : "");
    cfgMap.put("driverClassName", HikariConnectionPoolManager.detectDriverClassName(dbConfig.getJdbcUrl(), dbConfig.getDriverClassName()));
    cfgMap.put("query", dbConfig.getQuery() != null ? dbConfig.getQuery() : "");

    String cfgJson;
    try {
        cfgJson = objectMapper.writeValueAsString(cfgMap);
    } catch (Exception e) {
        cfgJson = "{}";
    }

    sb.append("    * def dbConfig_").append(safeStepId).append(" = ").append(cfgJson).append("\n");

    // Condition function emission
    String condition = dbConfig.getCondition();
    Integer expectedRowCount = dbConfig.getExpectedRowCount();
    if (expectedRowCount != null) {
        sb.append("    * def dbCondition_").append(safeStepId)
                .append(" = function(rows) { return rows.length === ").append(expectedRowCount).append("; }\n");
    } else if (condition != null && !condition.isBlank()) {
        String condTrimmed = condition.trim();
        if (condTrimmed.contains("==")) {
            String[] parts = condTrimmed.split("==");
            String field = cleanDbColumnPath(parts[0].trim());
            String expected = parts[1].trim();
            sb.append("    * def dbCondition_").append(safeStepId)
                    .append(" = function(rows) { if (rows.length === 0) return false; var r = rows[0]; return (r['")
                    .append(field).append("'] == ").append(expected).append(" || r['")
                    .append(field.toLowerCase()).append("'] == ").append(expected).append("); }\n");
        } else {
            sb.append("    * def dbCondition_").append(safeStepId)
                    .append(" = function(rows) { if (rows.length === 0) return false; var row = rows[0]; return ")
                    .append(condTrimmed).append("; }\n");
        }
    } else {
        sb.append("    * def dbCondition_").append(safeStepId)
                .append(" = function(rows) { return rows.length > 0; }\n");
    }

    int maxAttempts = dbConfig.getMaxAttempts() != null && dbConfig.getMaxAttempts() > 0 ? dbConfig.getMaxAttempts() : 5;
    long intervalMs = dbConfig.getPollIntervalMs() != null ? dbConfig.getPollIntervalMs() : 1000L;
    long timeoutMs = dbConfig.getTimeoutMs() != null ? dbConfig.getTimeoutMs() : 15000L;

    sb.append("    * def dbResult = dbPoll(dbConfig_").append(safeStepId).append(", ")
            .append(maxAttempts).append(", ").append(intervalMs).append(", ")
            .append(timeoutMs).append(", dbCondition_").append(safeStepId).append(")\n");
    sb.append("    * def pollResult = dbResult\n");
    sb.append("    * def result = dbResult\n");
    sb.append("    * match dbResult.matched == true\n");

    if (expectedRowCount != null) {
        sb.append("    * match karate.sizeOf(dbResult.rows) == ").append(expectedRowCount).append("\n");
    }

    // Step-level Assertions
    if (step.getAssertions() != null) {
        for (StepAssertion as : step.getAssertions()) {
            String f = as.getField() != null ? cleanDbColumnPath(as.getField()) : "";
            if (!f.isEmpty() && "EQUALS".equalsIgnoreCase(as.getOperator())) {
                Object exp = as.getExpectedValue();
                if (exp instanceof Number || exp instanceof Boolean) {
                    sb.append("    * match dbResult.rows[0].").append(f).append(" == ").append(exp).append("\n");
                } else {
                    sb.append("    * match dbResult.rows[0].").append(f).append(" == '").append(exp).append("'\n");
                }
            }
        }
    }

    // Variable Extractions
    if (step.getExtractions() != null) {
        for (VariableExtraction ext : step.getExtractions()) {
            String varName = ext.getVariableName();
            String path = cleanDbColumnPath(ext.getJsonPath());
            if (varName != null && !varName.isBlank() && !path.isBlank()) {
                sb.append("    * def ").append(varName).append(" = dbResult.rows[0].").append(path).append("\n");
            }
        }
    }
}
```

---

### 2. `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` (test, batch)

**Analog:** `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` (lines 65-106, `testStandaloneKarateFeatureExport`)  
**Supporting Analog:** `backend/src/test/java/com/orchestrator/platform/DynamicKarateRunnerTest.java` (lines 155-167, executing Karate feature file via `Runner.path(...)`)

#### Test Setup & Spring Boot Context
Analog from `WorkflowFeaturesAndPortabilityTest.java` (lines 24-46):
```java
package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.DbPollStepConfig;
import com.orchestrator.platform.domain.model.ast.StepAssertion;
import com.orchestrator.platform.domain.model.ast.VariableExtraction;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasNode;
import com.orchestrator.platform.domain.model.canvas.CanvasPosition;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.service.TestWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
    private TestWorkflowRepository workflowRepository;
```

#### Core Test 1 Pattern (Syntax & Structure Export Verification)
Analog from `testStandaloneKarateFeatureExport` (lines 65-106):
```java
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

    state.setNodes(List.of(dbNode));
    req.setCanvasState(state);

    TestWorkflowEntity wf = workflowService.createWorkflow(req);

    String feature = workflowService.generateStandaloneKarateFeature(wf.getId());
    assertNotNull(feature);

    // DBEXP-01: Background contains self-contained JDBC functions
    assertTrue(feature.contains("Java.type('java.sql.DriverManager')"), "Must use standard DriverManager");
    assertTrue(feature.contains("def dbQuery ="), "Must define dbQuery helper");
    assertTrue(feature.contains("def dbPoll ="), "Must define dbPoll helper");

    // DBEXP-02: Retry loop respecting maxAttempts and pollIntervalMs
    assertTrue(feature.contains("dbPoll(dbConfig_step_db, 3, 250, 3000"), "Must pass retry parameters to dbPoll");

    // DBEXP-03: Row match assertions
    assertTrue(feature.contains("match dbResult.matched == true"), "Must assert matched == true");
    assertTrue(feature.contains("match dbResult.rows[0].status == 'ACTIVE'"), "Must assert column equality");

    // DBEXP-04: Variable extractions
    assertTrue(feature.contains("def userStatus = dbResult.rows[0].status"), "Must extract status column");
    assertTrue(feature.contains("def userId = dbResult.rows[0].id"), "Must extract stripped id column");
}
```

#### Core Test 2 Pattern (Standalone Karate Core Execution with Runner.path)
Analog from `DynamicKarateRunner.java:160-167` and `DynamicKarateRunnerTest.java:41-90`:
```java
@Test
@DisplayName("Verify Standalone exported Karate feature executes cleanly through Karate Runner.path")
public void testStandaloneKarateFeatureExecutionStandalone() throws Exception {
    // 1. Prepare in-memory H2 table
    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
            "jdbc:h2:mem:standalone_exec_db;DB_CLOSE_DELAY=-1", "sa", "")) {
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, status VARCHAR(50), total DECIMAL(10,2))");
            stmt.execute("DELETE FROM orders");
            stmt.execute("INSERT INTO orders VALUES (501, 'COMPLETED', 129.50)");
        }
    }

    // 2. Create Workflow with DB Poll Step and downstream verification
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
    dbNode.setData(data);
    state.setNodes(List.of(dbNode));
    req.setCanvasState(state);

    TestWorkflowEntity wf = workflowService.createWorkflow(req);

    // 3. Export standalone Karate feature text
    String featureContent = workflowService.generateStandaloneKarateFeature(wf.getId());
    assertNotNull(featureContent);

    // 4. Save to temporary feature file
    Path tempFeature = Files.createTempFile("standalone-db-test-", ".feature");
    try {
        Files.writeString(tempFeature, featureContent, StandardCharsets.UTF_8);

        // 5. Execute via standard Karate Runner (simulating external CI runner)
        io.karatelabs.core.SuiteResult results = io.karatelabs.core.Runner.path(tempFeature.toAbsolutePath().toString())
                .outputHtmlReport(false)
                .parallel(1);

        assertFalse(results.isFailed(), "Standalone feature execution failed: " + results.getErrorsText());
        assertEquals(0, results.getFailCount(), "There should be zero failed scenarios");
    } finally {
        Files.deleteIfExists(tempFeature);
    }
}
```

---

## Shared Patterns

### 1. Zero External Dependencies for Standalone Background Helpers
**Source:** `01-RESEARCH.md` (lines 11, 263-364) & `backend/pom.xml` (lines 20-27)  
**Apply to:** `TestWorkflowService.generateStandaloneKarateFeature`  
All helper functions emitted in `Background:` must only use `Java.type('java.sql.DriverManager')`, `Java.type('java.lang.Thread')`, `Java.type('java.lang.System')`, and `Java.type('java.lang.Class')`. Exported features must never reference `com.orchestrator.platform.*` so they run independently in any standard Karate 2.x CLI runner.

### 2. Column Identifier & Path Normalization
**Source:** `DynamicKarateBridge.java:559-571` & `01-RESEARCH.md:244-247`  
**Apply to:** Step assertions and variable extractions in `generateStandaloneKarateFeature`  
User configurations in canvas commonly include Jayway JSONPath or row prefixes:
- Prefix stripping: `cleanPath = cleanPath.replaceAll("^(\\$\\.?|row\\.|latestRow\\.)", "")`
- Dual-case key storage in `dbQuery` rows (`row[colName]` and `row[colName.toLowerCase()]`) ensures cross-database compatibility across Oracle/H2 (uppercase) and PostgreSQL (lowercase).

### 3. Safe Gherkin JSON Serialization
**Source:** `TestWorkflowService.java:250` & `01-RESEARCH.md:215`  
**Apply to:** `dbConfig` dictionary emission in scenarios  
Always pass Java configuration maps through `objectMapper.writeValueAsString(cfgMap)` rather than string formatting. This automatically escapes special characters, SQL quotes, backslashes, and multiline queries into valid JSON literals.

### 4. Dynamic Token & Secret Resolution in Standalone Features
**Source:** `01-RESEARCH.md:272-286` & `WorkflowFeaturesAndPortabilityTest.java:360-365`  
**Apply to:** Emitted `dbQuery` JavaScript helper  
Exported inline JS resolves tokens of the form `${var}`, `${$env:VAR}`, and `${$secret:VAR}` by querying `karate.get(key)`, `karate.properties[key]`, and `java.lang.System.getenv(key)`, ensuring credentials are not hardcoded in plain text.

---

## No Analog Found

All files to be created or modified have exact existing analogs in the codebase.

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| *(None)* | — | — | Full analog coverage achieved. |

---

## Metadata

**Analog search scope:**
- `backend/src/main/java/com/orchestrator/platform/service/`
- `backend/src/main/java/com/orchestrator/platform/service/execution/`
- `backend/src/main/java/com/orchestrator/platform/config/`
- `backend/src/main/resources/karate/`
- `backend/src/test/java/com/orchestrator/platform/`

**Files scanned:** 12  
**Pattern extraction date:** 2026-11-06
