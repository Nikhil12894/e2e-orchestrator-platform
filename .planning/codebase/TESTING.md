---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
# Testing Patterns

**Analysis Date:** 2026-11-06

## Test Framework

**Runner:**
- Backend: JUnit Jupiter (JUnit 5.11+ / JUnit 6 via Karate) + Spring Boot Test (`spring-boot-starter-test:4.1.2`)
- Config: `backend/pom.xml` (Maven Surefire Plugin with `--enable-native-access=ALL-UNNAMED`) and `backend/src/main/resources/application-test.yml`
- Frontend: None currently configured in `frontend/package.json`

**Assertion Library:**
- JUnit 5 Assertions (`org.junit.jupiter.api.Assertions.*`)
- Karate Core Assertion Engine (Gherkin `match`, `status`, `retry until`)

**Run Commands:**
```bash
cd backend && mvn test                                      # Run all backend automated tests (72 tests)
cd backend && mvn test -Dtest=DbHelperTest                  # Run standalone DbHelper unit tests
cd backend && mvn test -Dtest=WorkflowFeaturesAndPortabilityTest # Run feature export & portability tests
cd backend && mvn test -Dtest=DynamicKarateRunnerTest       # Run specific dynamic execution test suite
```

## Test File Organization

**Location:**
- Separate test directory: `backend/src/test/java/com/orchestrator/platform/`

**Naming:**
- Test classes end with `Test`: `DynamicKarateRunnerTest.java`, `WorkflowFeaturesAndPortabilityTest.java`, `DbHelperTest.java`

**Structure:**
```
backend/src/test/
├── java/com/orchestrator/platform/
│   ├── DynamicKarateRunnerTest.java                   # End-to-end dynamic Karate workflow tests
│   ├── SubWorkflowCompositionTest.java                # Parent/child sub-workflow composition tests
│   ├── WorkflowFeaturesAndPortabilityTest.java        # Feature/JSON export, nested sub-workflow export, circular guards
│   ├── WorkflowExecutionNavigationUrlTest.java        # Location header, status/report/stream navigation URLs
│   ├── WorkflowExecutionSyncModeTest.java             # Synchronous wait execution mode (?wait=true)
│   ├── WorkflowContextEngineTest.java                 # Jayway JsonPath and macro variable tests
│   ├── DbExecutorAndReportExclusionTest.java          # Multi-database query execution tests
│   ├── ExecutionCleanupAndStorageReclamationTest.java # Report directory deletion & disk cleanup tests
│   ├── FlywayReferenceWorkflowsTest.java              # Verification of Flyway seed workflows V1-V9
│   ├── TestWorkflowServiceTest.java                   # Service layer CRUD and AST compilation tests
│   ├── OpenApiCatalogServiceTest.java                 # Swagger spec parsing and payload generation
│   ├── E2EOrchestratorApplicationTests.java           # Spring application context load test
│   └── karate/
│       └── DbHelperTest.java                          # Standalone DbHelper polling, query, update, driver detection tests
└── resources/
    └── application-test.yml                           # In-memory H2 configuration for tests
```

## Test Structure

**Suite Organization:**
```java
package com.orchestrator.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
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

    @Test
    @DisplayName("Verify standalone Karate feature file export produces valid Gherkin")
    void testFeatureExport() {
        // Arrange
        TestWorkflowEntity workflow = createTestWorkflow();

        // Act
        String featureContent = workflowService.exportFeatureFile(workflow.getId());

        // Assert
        assertNotNull(featureContent);
        assertTrue(featureContent.contains("Feature: Dynamic Test Workflow"));
        assertTrue(featureContent.contains("Scenario: Execute"));
    }
}
```

**Patterns:**
- Setup: `@ActiveProfiles("test")` binds to `application-test.yml` using in-memory H2 database (`jdbc:h2:mem:testdb;MODE=PostgreSQL`)
- Teardown: Spring TestContext framework automatically manages transaction rollbacks or in-memory DB destruction
- Assertion: Combinations of `assertEquals`, `assertTrue`, `assertNotNull`, and `assertThrows`

## Mocking

**Framework:**
- Mockito (via `spring-boot-starter-test`)
- In-process Mock Endpoints: `MockTargetApiController.java` provides mock APIs for authentication, orders, inventory, and payment simulation

**Patterns:**
```java
// Testing using live local web environment with real in-process mock controller
String targetUrl = "http://localhost:" + port + "/api/v1/mock/auth/login";
stepConfig.setUrl(targetUrl);
```

**What to Mock:**
- External third-party networks (simulated via `MockTargetApiController` or mock endpoints)
- Slow external databases (simulated using H2 in PostgreSQL compatibility mode)

**What NOT to Mock:**
- Embedded Karate DSL execution engine (`DynamicKarateRunner`, `DynamicKarateBridge`)
- JSONPath resolution and AST compilation engines (`WorkflowContextEngine`)
- Database migrations (`FlywayConfig` tests run against real H2/Postgres schemas)

## Fixtures and Factories

**Test Data:**
```java
private TestWorkflowEntity createSampleWorkflow() {
    WorkflowCreateRequest req = new WorkflowCreateRequest();
    req.setName("Test Checkout Flow");
    req.setDescription("Sample automated checkout suite");
    CanvasState canvas = new CanvasState();
    // Populate sample nodes and edges
    req.setCanvasState(canvas);
    return workflowService.createWorkflow(req);
}
```

**Location:**
- Helper factory methods inside test classes (`createSampleWorkflow()`)
- Flyway migration seed scripts (`backend/src/main/resources/db/migration/V2__...` through `V9__...`)

## Coverage

**Requirements:**
- High coverage on backend execution engine, portability export/import, storage reclamation, and Karate bridge interop
- No coverage enforced for frontend React layer currently

**View Coverage:**
```bash
cd backend && mvn test jacoco:report 2>/dev/null || mvn test
```

## Test Types

**Unit Tests:**
- Scope: `WorkflowContextEngineTest`, testing individual token extraction, variable substitution, and `${$randomUuid}` macros in isolation

**Integration Tests:**
- Scope: `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` testing full Spring application context, Flyway migration execution, HTTP controller endpoints, and JPA queries

**E2E Tests:**
- Scope: `DynamicKarateRunnerTest` and `SubWorkflowCompositionTest` trigger full test workflows through Karate 2.1.4, executing real HTTP requests against `MockTargetApiController`, validating database queries, chaining tokens, and verifying generated HTML reports on disk

## Common Patterns

**Async Testing (Virtual Threads):**
```java
// Trigger asynchronous execution (returns 202 Accepted)
WorkflowExecutionEntity execution = executionService.triggerExecution(workflow.getId(), request);

// Await completion with timeout loop
long maxWait = 15000L;
long start = System.currentTimeMillis();
while (execution.getStatus() == ExecutionStatus.PENDING || execution.getStatus() == ExecutionStatus.RUNNING) {
    Thread.sleep(200);
    execution = executionService.getExecutionById(execution.getId()).orElseThrow();
    if (System.currentTimeMillis() - start > maxWait) {
        fail("Execution timed out");
    }
}
assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
```

**Error Testing:**
```java
@Test
@DisplayName("Executing invalid workflow returns appropriate error status")
void testInvalidWorkflowExecution() {
    UUID nonExistentId = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> {
        executionService.triggerExecution(nonExistentId, new WorkflowExecutionRequest());
    });
}
```

---

*Testing analysis: 2026-11-06*
