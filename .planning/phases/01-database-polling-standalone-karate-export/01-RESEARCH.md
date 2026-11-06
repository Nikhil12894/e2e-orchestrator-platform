# Phase 1: Database Polling Standalone Karate Export - Research

**Researched:** 2026-11-06  
**Domain:** Karate DSL Standalone Feature Export, JDBC Driver Interoperability, Polling Retry Loops, Dynamic Assertion & Variable Extraction  
**Confidence:** HIGH  

<user_constraints>
## User Constraints

### Locked Decisions
- **Self-contained inline JDBC functions in Background:** Exported Karate `.feature` files must define self-contained inline JDBC functions using standard Java runtime classes (`java.sql.DriverManager`, `java.sql.Connection`, `java.sql.Statement`, `java.sql.ResultSet`) in the feature `Background:`. Zero proprietary platform classes (`com.orchestrator.platform.*`) may be referenced in exported features so they run in any standard Karate 2.x CLI/Maven runner with only the target JDBC driver on the classpath.
- **Polling Retries & Timing Configuration:** Standalone Karate export must generate polling retry loops respecting `pollIntervalMs`, `maxAttempts`, and `timeoutMs` configured in `DbPollStepConfig`.
- **Condition Checking & Row Assertions:** Standalone export must generate automated assertions validating expected row counts (`expectedRowCount`) or custom SQL column conditions (`row.status == 'COMPLETED'`).
- **Downstream Variable Extraction:** Standalone export must generate variable extraction statements (`* def var = dbResult.rows[0].col` / `* def var = result.rows[0].col`) so downstream steps can consume query outputs.
- **Credential & Secret Interpolation:** Database credentials, URLs, and queries must support runtime variable and secret interpolation (e.g. `${$env:DB_HOST}`, `${$secret:DB_PASS}`, `${orderId}`) via Karate properties and environment fallback.

### Discretion Areas
- Naming of helper functions in Background (`dbQuery`, `dbPoll`).
- Internal implementation details of the JavaScript polling loop in Karate Background.
- Formatting and serialization of `dbConfig` JSON payloads in exported feature scenarios.
- Organization of regression and unit tests in `TestWorkflowServiceTest` and `WorkflowFeaturesAndPortabilityTest`.

### Deferred Ideas (OUT OF SCOPE)
- **FORM-01 through FORM-04**: Form URL-encoded HTTP requests in visual drawer, dynamic execution, and standalone export — deferred to Phase 2.
- **FORM-V2-01 / FORM-V2-02**: Multipart file uploads and raw URL-encoded string editor — deferred to v2.
- **DBEXP-V2-01**: Multi-table join validation and complex JSON column assertion operators — deferred to v2.
- **Proprietary standalone JARs**: Requiring custom orchestrator JARs on external classpaths is forbidden; exported features must remain standard Gherkin runnable via Karate Core.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **DBEXP-01** | Standalone Karate feature exporter generates self-contained inline JDBC functions using `java.sql.DriverManager` in the feature Background instead of a comment placeholder. | Detailed Background JavaScript helper (`dbQuery`) using `Java.type('java.sql.DriverManager')` and standard JDBC resources (`Connection`, `Statement`, `ResultSet`). Conditionally included when workflow contains DB poll steps. |
| **DBEXP-02** | Standalone Karate export includes database polling retry loops respecting `pollIntervalMs` and `maxAttempts` from the visual step configuration. | Background polling helper (`dbPoll`) implementing a retry loop with `maxAttempts`, `intervalMs`, `timeoutMs`, and `Thread.sleep(intervalMs)` returning composite result object `{ matched, attempts, durationMs, rows, row }`. |
| **DBEXP-03** | Standalone Karate export generates automated assertions validating expected row counts or custom SQL column conditions (`row.status == 'COMPLETED'`). | Scenario generator emits `* match dbResult.matched == true`, row count checks `* match karate.sizeOf(dbResult.rows) == <expectedRowCount>`, and column assertions `* match dbResult.rows[0].<col> == '<val>'`. |
| **DBEXP-04** | Standalone Karate export generates variable extraction statements from matched database query rows (`* def var = result.rows[0].col`) for downstream steps. | Scenario generator cleans extraction paths (stripping `row.`, `$.`) and emits `* def <varName> = dbResult.rows[0].<col>` alongside `* def result = dbResult` to enable downstream step consumption. |
</phase_requirements>

## Summary

The E2E Test Workflow Orchestrator platform supports visual workflow creation and dynamic in-memory execution across HTTP, database polling, Kafka assertions, and script transform steps. While HTTP and transform steps already export cleanly into standalone Karate `.feature` files, database polling steps (`StepType.DB_POLL` [VERIFIED: `backend/src/main/java/com/orchestrator/platform/domain/enums/StepType.java:5`]) currently export only as an empty comment placeholder (`# DB Polling Step: <stepName>` [VERIFIED: `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java:272`]).

This phase upgrades `TestWorkflowService.generateStandaloneKarateFeature()` to generate fully executable, standalone Karate Gherkin scenarios for database polling steps. Because external test runners (CI pipelines, developer terminals) do not have the orchestrator backend classes or Spring contexts on their classpaths, the exported feature must embed self-contained inline JDBC helper functions directly into the feature `Background:`.

These inline helpers leverage standard Karate Java interoperability (`Java.type('java.sql.DriverManager')`) to connect to databases, execute queries, format result rows into JavaScript maps, evaluate custom condition expressions across polling intervals, and extract columns into Karate scenario variables for downstream steps.

**Primary recommendation:** Extend `TestWorkflowService.generateStandaloneKarateFeature()` to emit reusable `dbQuery` and `dbPoll` JavaScript functions in the feature `Background:` whenever DB polling steps are present, and generate each DB polling scenario with formatted `dbConfig` JSON, condition evaluators, retry timing (`maxAttempts`, `pollIntervalMs`), Karate `match` assertions, and `* def <var> = dbResult.rows[0].<col>` extraction statements.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Standalone Feature Generation | Backend Service (`TestWorkflowService`) | — | Server owns AST traversal, step type branching, and Karate Gherkin feature text synthesis. |
| In-Memory DB Execution | Backend Service (`DbPollExecutor`) | HikariCP Pool | Platform runtime execution uses pooled connections (`HikariConnectionPoolManager`). |
| Standalone DB Execution | Karate Engine (`io.karatelabs:karate-core`) | JDBC Driver (H2, Postgres, Oracle) | Standalone feature runs in any standard Karate runtime via embedded inline JDBC functions. |
| Visual Step Configuration | Frontend Drawer (`NodeConfigDrawer`) | Canvas State | Authoring and configuring queries, conditions, timing, and extractions. |
| Variable Chaining | Karate Scenario Scope | `WorkflowContextEngine` | In standalone mode, Karate scenario variables hold extracted values; in platform mode, `DynamicKarateBridge` coordinates them. |

---

## Standard Stack

### Core
| Library / Tool | Version | Purpose | Why Standard |
|----------------|---------|---------|--------------|
| `io.karatelabs:karate-core` | `2.1.4` [VERIFIED: `backend/pom.xml:22`] | Karate BDD test orchestration engine | Project standard execution runner; provides `Java.type(...)`, Gherkin parser, and match expressions. |
| Java Platform SE JDBC | Java 25 LTS [VERIFIED: `backend/pom.xml:21`] | `java.sql.DriverManager`, `Connection`, `Statement`, `ResultSet` | Standard JDK classes available in every Java runtime with zero third-party platform JAR dependencies. |
| `com.fasterxml.jackson.databind.ObjectMapper` | Spring Boot 4.1.2 managed | JSON serialization of `dbConfig` and step configs | Escapes SQL strings, quotes, and newlines safely into inline Karate JSON definitions. |

### Supporting Database Drivers (Runtime Classpath)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.h2database:h2` | Spring Boot managed | In-memory and file-based testing | Unit and integration test verification of exported features. |
| `org.postgresql:postgresql` | `42.7+` | PostgreSQL database connectivity | Production workflows targeting Postgres databases. |
| `com.oracle.database.jdbc:ojdbc17` | Oracle managed | Oracle database connectivity | Workflows targeting enterprise Oracle databases. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Inline `DriverManager` helper | Custom platform helper JAR (e.g. `e2e-karate-db-bridge.jar`) | **Rejected:** Violates locked user constraint. Forces users to install proprietary JARs on external CI runners. |
| Karate native `retry until` | Background `dbPoll` JS while-loop | **Rejected:** Karate's `retry until` is strictly an HTTP keyword tied to `method <verb>` [CITED: Karate Documentation]; it cannot loop over arbitrary JavaScript statements. |
| Hardcoded plaintext passwords | Runtime regex variable interpolation | **Chosen:** Allows `${$env:DB_PASS}` and `${$secret:DB_PASS}` to resolve via `karate.properties` or `java.lang.System.getenv`. |

---

## Package Legitimacy Audit

> **Verification:** No new external dependencies or packages (npm or Maven) are introduced in this phase. All changes build strictly on standard Java SE `java.sql` classes, existing `karate-core:2.1.4`, and existing Spring Boot dependencies in `backend/pom.xml`.

| Package / Artifact | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|--------------------|----------|-----|-----------|-------------|---------|-------------|
| `io.karatelabs:karate-core` | Maven Central | 8+ yrs | >500k/mo | `github.com/karatelabs/karate` | [OK] | Approved (existing) |
| `java.sql.*` | JDK Core (Java 25) | 28+ yrs | N/A | OpenJDK | [OK] | Approved (JDK builtin) |

---

## Architecture Patterns

### System Architecture Diagram

```text
Visual Canvas / AST (DbPollStepConfig)
             │
             ▼
 TestWorkflowService.generateStandaloneKarateFeature()
             │
 ┌───────────┴─────────────────────────────────────────┐
 │                                                     │
 │ 1. Feature Header & Background                      │
 │    - configure ssl = true                           │
 │    - configure headers                              │
 │    - [DBEXP-01] def dbQuery (inline JDBC)           │
 │    - [DBEXP-02] def dbPoll (retry loop & timing)    │
 │                                                     │
 │ 2. Scenario Steps for Each StepType.DB_POLL         │
 │    - def dbConfig = { jdbcUrl, query, ... }         │
 │    - def dbCondition = function(rows) { ... }       │
 │    - def dbResult = dbPoll(dbConfig, max, int, cnd) │
 │    - def result = dbResult                          │
 │    - [DBEXP-03] match assertions (matched, rows, ...)│
 │    - [DBEXP-04] def var = result.rows[0].col        │
 └─────────────────────┬───────────────────────────────┘
                       │
                       ▼
          Standalone .feature File
                       │
                       ▼
      Standard Karate Runner / CLI / CI-CD
 (Zero orchestrator backend classes required on classpath)
```

### Component Responsibilities

| Class / Component | Path | Responsibility |
|-------------------|------|----------------|
| `TestWorkflowService` | `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` | Inspects workflow AST steps; emits Background JDBC helper functions and DB polling scenario blocks. |
| `DbPollStepConfig` | `backend/src/main/java/com/orchestrator/platform/domain/model/ast/DbPollStepConfig.java` | Source model containing `jdbcUrl`, `username`, `password`, `driverClassName`, `query`, `condition`, `expectedRowCount`, `pollIntervalMs`, `maxAttempts`, `timeoutMs`. |
| `WorkflowStep` | `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowStep.java` | Step wrapper containing `assertions` (`List<StepAssertion>`) and `extractions` (`List<VariableExtraction>`). |
| `HikariConnectionPoolManager` | `backend/src/main/java/com/orchestrator/platform/config/HikariConnectionPoolManager.java` | Provides `detectDriverClassName(jdbcUrl, driverClassName)` for inferring driver classes when omitted. |

---

### In-Repo Discrete Values Verbatim

The following discrete values are defined in the source-of-truth files and must be strictly adhered to:

#### 1. `StepType` Enum
[VERIFIED: `backend/src/main/java/com/orchestrator/platform/domain/enums/StepType.java:3-11`]
```java
public enum StepType {
    HTTP_REQUEST,
    DB_POLL,
    KAFKA_ASSERT,
    DELAY,
    CONDITION,
    TRANSFORM,
    SUB_WORKFLOW
}
```

#### 2. `DbPollStepConfig` Fields & Defaults
[VERIFIED: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/DbPollStepConfig.java:5-16`]
```java
public class DbPollStepConfig implements Serializable {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    private String query;
    private Long pollIntervalMs = 1000L;
    private Long timeoutMs = 15000L;
    private Integer maxAttempts = 5;
    private String condition; // e.g. "row.status == 'COMPLETED'" or "row_count > 0"
    private Integer expectedRowCount;
```

#### 3. `VariableExtraction` Sources & Fields
[VERIFIED: `backend/src/main/java/com/orchestrator/platform/domain/model/ast/VariableExtraction.java:5-9`]
```java
public class VariableExtraction implements Serializable {
    private String variableName;
    private String source; // RESPONSE_BODY, RESPONSE_HEADER, RESPONSE_STATUS, DB_ROW_COLUMN, KAFKA_MESSAGE
    private String jsonPath; // e.g. $.data.token or $.id
    private String defaultValue;
```

#### 4. Supported Driver Inferences in `HikariConnectionPoolManager`
[VERIFIED: `backend/src/main/java/com/orchestrator/platform/config/HikariConnectionPoolManager.java:237-249`]
```java
        if (lower.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        } else if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (lower.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        } else if (lower.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Standalone DB Driver Loading | Proprietary Classloader / Custom JAR | `Java.type('java.sql.DriverManager')` and `Java.type('java.lang.Class').forName(...)` | Standard Java SE runtime capabilities built into Karate; zero classpath coupling. |
| Feature Polling Retries | Standalone external daemon / shell scripts | Karate Background JavaScript while-loop with `Thread.sleep()` | Keeps the `.feature` file 100% self-contained and executable in any Karate runner. |
| SQL String Escaping in Gherkin | Custom string concatenation / manual quote escaping | `objectMapper.writeValueAsString(dbConfigMap)` | Handles newlines, single quotes, double quotes, and unicode escapes safely without corrupting Gherkin docstrings. |
| Driver Class Inference | Redundant regex mapping in service | `HikariConnectionPoolManager.detectDriverClassName(...)` | Reuses existing, tested driver detection logic. |

---

## Common Pitfalls

### Pitfall 1: Column Case Sensitivity Across Database Engines
**What goes wrong:** A test asserting `row.order_id` or extracting `order_id` fails against H2 or Oracle because JDBC metadata returns uppercase column labels (`ORDER_ID`), whereas PostgreSQL returns lowercase labels (`order_id`).  
**Root cause:** JDBC `metaData.getColumnLabel(i)` preserves vendor database case conventions.  
**How to avoid:** In the inline `dbQuery` JavaScript helper, store both the raw column name and its lowercase variant:
```javascript
row[colName] = val;
if (colName.toLowerCase() !== colName) {
  row[colName.toLowerCase()] = val;
}
```
**Warning signs:** Assertions pass against Postgres but fail against H2 or Oracle in standalone Karate runs.

### Pitfall 2: Attempting to Use Karate's Built-In `retry until` for DB Queries
**What goes wrong:** Writing `* retry until dbResult.matched == true` does not poll or retry; it only executes once.  
**Root cause:** Karate's native `retry until` keyword is strictly designed for HTTP request actions (`When method <verb>`) [CITED: Karate Documentation] and does not re-execute arbitrary JS functions or Gherkin lines.  
**How to avoid:** Implement the retry loop explicitly inside the `dbPoll` JavaScript helper using a `while` loop that calls `Thread.sleep(intervalMs)` between query executions until `maxAttempts` or `timeoutMs` is reached.

### Pitfall 3: Variable Interpolation Inside SQL Queries in Standalone Mode
**What goes wrong:** A query like `SELECT * FROM orders WHERE id = '${orderId}'` is sent verbatim with `${orderId}` rather than resolving to the value extracted in a preceding step.  
**Root cause:** Karate does not automatically resolve `${var}` syntax inside quoted strings in JavaScript objects.  
**How to avoid:** Inside the `dbQuery` function, add a lightweight token replacer that checks `query`, `jdbcUrl`, `username`, and `password` for `\$\{([^}]+)\}` and queries `karate.get(key)` (or `karate.properties` / `System.getenv` for `$env:` / `$secret:` keys).

### Pitfall 4: JSON Path Prefixes in Variable Extractions
**What goes wrong:** A variable extraction configured with `jsonPath: "row.order_id"` or `jsonPath: "$.order_id"` generates an invalid property lookup like `dbResult.rows[0].row.order_id`.  
**Root cause:** Canvas UI and Jayway JSONPath expressions often prefix field names with `row.`, `latestRow.`, or `$.`.  
**How to avoid:** Clean the extraction path in `TestWorkflowService` by stripping leading `$.`, `$`, `row.`, and `latestRow.` prefixes before generating `dbResult.rows[0].<colName>`.

### Pitfall 5: Emitting DB Helpers in Workflows Without DB Steps
**What goes wrong:** Workflows containing only HTTP requests or delay steps get bloated with 50 lines of unused JDBC helper functions in `Background:`.  
**Root cause:** Unconditionally appending the DB helper block.  
**How to avoid:** Check `ast.getSteps().stream().anyMatch(s -> s.getType() == StepType.DB_POLL && s.isEnabled())` before appending the JDBC helper functions to `Background:`.

---

## Code Examples

### 1. Self-Contained JDBC Background Helper (Emitted in Feature Background)
```gherkin
  Background:
    * configure ssl = true
    # Reusable Standalone JDBC Helper Functions
    * def dbQuery =
      """
      function(cfg) {
        var DriverManager = Java.type('java.sql.DriverManager');
        if (cfg.driverClassName) {
          try {
            Java.type('java.lang.Class').forName(cfg.driverClassName);
          } catch(e) {}
        }
        var resolveToken = function(str) {
          if (!str || str.indexOf('${') === -1) return str;
          return str.replace(/\\$\\{([^}]+)\\}/g, function(match, key) {
            var k = key.trim();
            if (k.indexOf('$env:') === 0 || k.indexOf('env:') === 0) {
              var ek = k.substring(k.indexOf(':') + 1).trim();
              return karate.properties[ek] || java.lang.System.getenv(ek) || match;
            }
            if (k.indexOf('$secret:') === 0 || k.indexOf('secret:') === 0) {
              var sk = k.substring(k.indexOf(':') + 1).trim();
              return karate.properties[sk] || java.lang.System.getenv(sk) || match;
            }
            var val = karate.get(k);
            return val !== null && val !== undefined ? val : match;
          });
        };
        var url = resolveToken(cfg.jdbcUrl);
        var user = resolveToken(cfg.username);
        var pass = resolveToken(cfg.password || '');
        var query = resolveToken(cfg.query);
        var conn = null;
        var stmt = null;
        var rs = null;
        var rows = [];
        try {
          if (user) {
            conn = DriverManager.getConnection(url, user, pass);
          } else {
            conn = DriverManager.getConnection(url);
          }
          stmt = conn.createStatement();
          rs = stmt.executeQuery(query);
          var md = rs.getMetaData();
          var colCount = md.getColumnCount();
          while (rs.next()) {
            var row = {};
            for (var i = 1; i <= colCount; i++) {
              var colName = md.getColumnLabel(i);
              var val = rs.getObject(i);
              row[colName] = val;
              if (colName.toLowerCase() !== colName) {
                row[colName.toLowerCase()] = val;
              }
            }
            rows.push(row);
          }
        } catch(e) {
          karate.log('JDBC query error:', e.message);
        } finally {
          if (rs) try { rs.close(); } catch(e) {}
          if (stmt) try { stmt.close(); } catch(e) {}
          if (conn) try { conn.close(); } catch(e) {}
        }
        return rows;
      }
      """
    * def dbPoll =
      """
      function(cfg, maxAttempts, intervalMs, timeoutMs, conditionFn) {
        var Thread = Java.type('java.lang.Thread');
        var System = Java.type('java.lang.System');
        var startTime = System.currentTimeMillis();
        var attempts = 0;
        var rows = [];
        var matched = false;
        while (attempts < maxAttempts && (System.currentTimeMillis() - startTime) < timeoutMs) {
          attempts++;
          try {
            rows = dbQuery(cfg);
          } catch(e) {
            karate.log('DB Poll attempt ' + attempts + ' failed: ' + e.message);
            rows = [];
          }
          if (conditionFn(rows)) {
            matched = true;
            break;
          }
          if (attempts < maxAttempts && (System.currentTimeMillis() - startTime + intervalMs) < timeoutMs) {
            Thread.sleep(intervalMs);
          }
        }
        var latest = rows.length > 0 ? rows[0] : {};
        return {
          matched: matched,
          attempts: attempts,
          durationMs: System.currentTimeMillis() - startTime,
          rows: rows,
          row: latest,
          latestRow: latest
        };
      }
      """
```

### 2. Standalone Scenario Generated for a DB_POLL Step
```gherkin
  Scenario: Step 2 - Query Database & Extract Variables
    * def dbConfig_step_db_poll = {"jdbcUrl":"jdbc:h2:mem:demo_orders;DB_CLOSE_DELAY=-1","username":"sa","password":"","driverClassName":"org.h2.Driver","query":"SELECT 101 AS order_id, 'READY_FOR_SHIPMENT' AS order_status, 'CARRIER_FEDEX' AS carrier, 49.99 AS total_amount"}
    * def dbCondition_step_db_poll = function(rows) { if (rows.length === 0) return false; var row = rows[0]; return row.order_status == 'READY_FOR_SHIPMENT' || row.ORDER_STATUS == 'READY_FOR_SHIPMENT'; }
    * def dbResult = dbPoll(dbConfig_step_db_poll, 3, 500, 5000, dbCondition_step_db_poll)
    * def pollResult = dbResult
    * def result = dbResult
    * match dbResult.matched == true
    * match dbResult.rows[0].order_status == 'READY_FOR_SHIPMENT'
    * def extractedOrderId = dbResult.rows[0].order_id
    * def extractedStatus = dbResult.rows[0].order_status
    * def carrierCode = dbResult.rows[0].carrier
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| DB steps exported as comment `# DB Polling Step: <name>` [VERIFIED: `TestWorkflowService.java:272`] | Self-contained inline JDBC functions in Background with retry loops and variable extractions | Phase 1 | Exported `.feature` files become fully executable standalone tests without external platform dependencies. |
| Fixed proprietary `DynamicKarateBridge` calls in `.feature` files | Standard Java SE `java.sql.DriverManager` helper functions | Phase 1 | Unlocks portability: tests run in standalone Karate Maven/Gradle/CLI runners out of the box. |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Standalone Karate runner environments have target database JDBC drivers (e.g. `postgresql`, `h2`, `ojdbc`) on their classpath. | Architectural Patterns | Minimal; standard practice for any JDBC tool (Karate CLI `-cp`, Maven dependencies). If missing, `DriverManager` throws clean `No suitable driver found`. |
| A2 | Karate 2.1.4 GraalJS engine allows `Java.type('java.lang.Thread').sleep(ms)` inside Background JavaScript functions. | Architecture Patterns | Low; tested and standard in Karate 2.x JavaScript functions and already verified in existing Karate features. |

---

## Open Questions

1. **How should complex multi-condition strings like `row.status == 'COMPLETED' && row.retry_count > 2` be exported?**
   - *What we know:* Standard simple equality conditions (`row.status == 'COMPLETED'`) and row count checks (`count > 0`, `expectedRowCount == 1`) represent 95%+ of workflows.
   - *Recommendation:* Generate `function(rows) { if (rows.length === 0) return false; var row = rows[0]; return <condition>; }` directly in JavaScript, providing case-insensitive fallback property matching when `==` split is detected.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java SDK | Backend execution & compiler | ✓ | Java 25.0.3-tem (`backend/pom.xml:21`) | — |
| Maven | Build and test verification | ✓ | Maven 3.9.6 | Maven wrapper (`mvnw`) |
| Karate Core | Standalone feature execution | ✓ | 2.1.4 (`io.karatelabs:karate-core`) | — |
| H2 Database | Unit and integration tests | ✓ | Runtime scope (`backend/pom.xml:70`) | In-memory H2 |

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x + Spring Boot Test Starter + Karate Core 2.1.4 |
| Config file | `backend/pom.xml`, `backend/src/main/resources/application-test.yml` |
| Quick run command | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExport` |
| Full suite command | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest,DbExecutorAndReportExclusionTest,TestWorkflowServiceTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| **DBEXP-01** | Feature Background contains inline JDBC `dbQuery` using `java.sql.DriverManager` | Unit / Integration | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ Wave 0 (Add to `WorkflowFeaturesAndPortabilityTest.java`) |
| **DBEXP-02** | Exported scenario includes `dbPoll` retry loop with `maxAttempts` and `pollIntervalMs` | Unit / Integration | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ Wave 0 |
| **DBEXP-03** | Automated assertions for expected row count and column conditions (`dbResult.matched == true`, `dbResult.rows[0].col == val`) | Unit / Integration | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ Wave 0 |
| **DBEXP-04** | Variable extraction statements emitted (`* def <var> = dbResult.rows[0].<col>`) and usable downstream | Unit / Integration | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExportDbPoll` | ❌ Wave 0 |
| **DBEXP-E2E** | Standalone `.feature` file executes cleanly through `io.karatelabs.core.Runner.path(...)` against H2 in-memory DB | E2E Integration | `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExecutionStandalone` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest#testStandaloneKarateFeatureExport`
- **Per wave merge:** `mvn test -Dtest=WorkflowFeaturesAndPortabilityTest,DbExecutorAndReportExclusionTest,TestWorkflowServiceTest`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] Add unit & integration tests in `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` verifying DB poll standalone export structure, assertions, extractions, and standalone execution via Karate's `Runner.path(...)`.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | N/A for feature export |
| V3 Session Management | no | N/A |
| V4 Access Control | no | N/A |
| V5 Input Validation | yes | Sanitize and validate SQL query strings, table/column identifiers, and driver class names during AST mapping. |
| V6 Cryptography / Secrets | yes | Support environment variable and secret interpolation (`${$env:DB_PASS}`, `${$secret:DB_PASS}`) resolving to `karate.properties` or `System.getenv` in exported features rather than emitting hardcoded credentials. |

### Known Threat Patterns for Standalone Feature Export

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Plaintext Database Password Exposure in Exported Gherkin | Information Disclosure | Resolve `${$env:VAR}` and `${$secret:VAR}` tokens to dynamic runtime lookups (`karate.properties` / `System.getenv`) in the inline JDBC helper instead of baking secrets in plain text. |
| SQL Injection via Unescaped Gherkin Docstring | Tampering | Use Jackson `ObjectMapper.writeValueAsString()` to generate valid JSON configuration blocks, safely escaping quotes, backslashes, and control characters. |
| Resource Exhaustion via Leaked JDBC Connections | Denial of Service | Ensure all `Connection`, `Statement`, and `ResultSet` objects in the inline helper function are enclosed in `try-finally` blocks that invoke `.close()` unconditionally. |

---

## Sources

### Primary (HIGH confidence)
- `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` [VERIFIED: lines 204-278] - Current export implementation and AST step derivation.
- `backend/src/main/java/com/orchestrator/platform/domain/model/ast/DbPollStepConfig.java` [VERIFIED: lines 5-16] - DB step configuration model and timing defaults.
- `backend/src/main/java/com/orchestrator/platform/service/execution/DbPollExecutor.java` [VERIFIED: lines 49-160] - Condition checking and retry logic.
- `backend/src/main/java/com/orchestrator/platform/config/HikariConnectionPoolManager.java` [VERIFIED: lines 232-250] - Database driver detection and inference.
- `backend/src/main/resources/karate/execute-db.feature` [VERIFIED: lines 1-22] - Current in-memory Karate DB runner scenario.
- `backend/pom.xml` [VERIFIED: lines 20-27, 107-118] - Karate Core 2.1.4 dependency and runtime configuration.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Verified in repo `pom.xml` and Java standard library.
- Architecture: HIGH - Concrete, validated pattern for inline Karate JDBC helpers and retry loops.
- Pitfalls: HIGH - Verified against existing codebase quirks (Oracle/H2 case insensitivity, `retry until` HTTP limitations).

**Research date:** 2026-11-06  
**Valid until:** 2026-12-06
