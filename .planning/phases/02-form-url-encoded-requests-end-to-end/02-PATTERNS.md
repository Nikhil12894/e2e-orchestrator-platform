# Phase 02: Form URL-Encoded Requests End-to-End - Pattern Map

**Mapped:** 2026-11-07  
**Files analyzed:** 7  
**Analogs found:** 7 / 7 (100% exact match quality)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `frontend/src/types/workflow.ts` | model | transform | `frontend/src/types/workflow.ts` (lines 43-55, 126-166) | exact |
| `frontend/src/components/drawer/NodeConfigDrawer.tsx` | component | request-response | `frontend/src/components/drawer/NodeConfigDrawer.tsx` (lines 680-730, 774-801, 1660-1748) | exact |
| `backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java` | model | request-response | `backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java` (lines 9-56) | exact |
| `backend/src/main/resources/karate/execute-http.feature` | utility | request-response | `backend/src/main/resources/karate/execute-http.feature` (lines 13-44) | exact |
| `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` | service | transform | `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` (lines 344-375, 531-561, 668-703) | exact |
| `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java` | controller | request-response | `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java` (lines 20-33) | exact |
| `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` | test | request-response | `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` (lines 72-100, 210-275, 524-535) | exact |

---

## Pattern Assignments

### 1. `frontend/src/types/workflow.ts` (model, transform)

**Analog:** `frontend/src/types/workflow.ts`

**Imports Pattern:**
Type-only definitions, zero runtime dependencies. Standard TypeScript interfaces and union literals.

**Core Pattern (lines 43-55 & 126-166):**
```typescript
// frontend/src/types/workflow.ts:43-55
export interface HttpRequestStepConfig {
  url: string;
  method: HttpMethod;
  headers?: Record<string, string>;
  inheritGlobalHeaders?: boolean;
  excludedGlobalHeaders?: string[];
  params?: Record<string, string>;
  body?: any;
  bodyType?: 'JSON' | 'FORM_URL_ENCODED';
  formData?: Record<string, any>;
  expectedStatus?: number;
  timeoutMs?: number;
  retryCount?: number;
  retryIntervalMs?: number;
}
```

**Canvas Node Data Extension (lines 126-136):**
```typescript
// frontend/src/types/workflow.ts:126-136
export interface CanvasNodeData {
  [key: string]: any;
  name: string;
  method?: HttpMethod;
  url?: string;
  headers?: Record<string, string>;
  inheritGlobalHeaders?: boolean;
  excludedGlobalHeaders?: string[];
  params?: Record<string, string>;
  body?: any;
  bodyType?: 'JSON' | 'FORM_URL_ENCODED';
  formData?: Record<string, any>;
  expectedStatus?: number;
  // ...
}
```

---

### 2. `frontend/src/components/drawer/NodeConfigDrawer.tsx` (component, request-response)

**Analog:** `frontend/src/components/drawer/NodeConfigDrawer.tsx`

**Imports Pattern (lines 1-30):**
```typescript
import React, { useState, useEffect, Suspense } from 'react';
import {
  X,
  Settings,
  Globe,
  Plus,
  Trash2,
  Variable,
  CheckCircle,
  AlertCircle
} from 'lucide-react';
import { CanvasNode, CanvasNodeData, HttpMethod, StepAssertion, VariableExtraction } from '../../types/workflow';
```

**Variable Insertion Hook Pattern (lines 211-248):**
```typescript
// NodeConfigDrawer.tsx:211-248
const handleInsertVariable = (varExpr: string) => {
  if (!varTargetField) return;

  if (varTargetField.startsWith('formParam_')) {
    const idx = parseInt(varTargetField.substring(10), 10);
    handleUpdateFormRow(idx, 'value', (formRows[idx]?.value || '') + varExpr);
  } else if (varTargetField === 'url') {
    update({ url: (data.url || '') + varExpr });
  } else if (varTargetField === 'body') {
    // ...
  }
};
```

**Body Type Toggle & Header Synchronization Pattern (lines 680-735, 774-801):**
```typescript
// State synchronization pattern
const [formRows, setFormRows] = useState<Array<{ key: string; value: string }>>(() => {
  if (data.formData && typeof data.formData === 'object') {
    return Object.entries(data.formData).map(([k, v]) => ({ key: k, value: String(v ?? '') }));
  }
  return [];
});

const handleSelectBodyType = (type: 'JSON' | 'FORM_URL_ENCODED') => {
  const currentHeaders = { ...(data.headers || {}) };
  if (type === 'FORM_URL_ENCODED') {
    currentHeaders['Content-Type'] = 'application/x-www-form-urlencoded';
  } else {
    if (currentHeaders['Content-Type'] === 'application/x-www-form-urlencoded') {
      currentHeaders['Content-Type'] = 'application/json';
    }
  }
  update({
    bodyType: type,
    headers: currentHeaders,
  });
};

const handleUpdateFormRow = (index: number, field: 'key' | 'value', val: string) => {
  const updated = [...formRows];
  updated[index] = { ...updated[index], [field]: val };
  setFormRows(updated);

  const mapped: Record<string, string> = {};
  updated.forEach((r) => {
    if (r.key.trim()) mapped[r.key.trim()] = r.value;
  });
  update({ formData: mapped });
};
```

**Form Table UI Template Pattern (conforming to `02-UI-SPEC.md`):**
```tsx
{/* Body Type Segmented Control */}
{(data.method === 'POST' || data.method === 'PUT' || data.method === 'PATCH') && (
  <div className="space-y-3">
    <div>
      <label className="block text-[11px] font-semibold text-slate-300 mb-1.5">
        Request Body Format
      </label>
      <div className="grid grid-cols-2 gap-1 p-1 bg-slate-900 border border-slate-800 rounded-lg">
        <button
          type="button"
          onClick={() => handleSelectBodyType('JSON')}
          className={`py-1.5 px-3 rounded-md text-xs transition-all ${
            (!data.bodyType || data.bodyType === 'JSON')
              ? 'bg-blue-600/20 text-blue-300 border border-blue-500 font-semibold'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          JSON Body
        </button>
        <button
          type="button"
          onClick={() => handleSelectBodyType('FORM_URL_ENCODED')}
          className={`py-1.5 px-3 rounded-md text-xs transition-all ${
            data.bodyType === 'FORM_URL_ENCODED'
              ? 'bg-blue-600/20 text-blue-300 border border-blue-500 font-semibold'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          URL-Encoded Form
        </button>
      </div>
    </div>
  </div>
)}
```

---

### 3. `backend/src/main/java/com/orchestrator/platform/domain/model/ast/HttpRequestStepConfig.java` (model, request-response)

**Analog:** `HttpRequestStepConfig.java` (lines 9-56)

**Imports & Class Structure Pattern:**
```java
package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpRequestStepConfig implements Serializable {
    private String url;
    private String method = "GET";
    private Map<String, String> headers = new HashMap<>();
    private boolean inheritGlobalHeaders = true;
    private List<String> excludedGlobalHeaders = new ArrayList<>();
    private Map<String, String> params = new HashMap<>();
    private Object body;
    private String bodyType = "JSON"; // "JSON" or "FORM_URL_ENCODED"
    private Map<String, Object> formData = new LinkedHashMap<>();
    private Integer expectedStatus = 200;
    private Integer timeoutMs = 15000;
    private Integer retryCount = 1;
    private Integer retryIntervalMs = 500;

    public HttpRequestStepConfig() {}

    public String getBodyType() { return bodyType; }
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }

    public Map<String, Object> getFormData() { return formData; }
    public void setFormData(Map<String, Object> formData) { this.formData = formData; }

    public boolean isFormEncoded() {
        return "FORM_URL_ENCODED".equalsIgnoreCase(bodyType) ||
                (headers != null && "application/x-www-form-urlencoded".equalsIgnoreCase(headers.get("Content-Type")));
    }
}
```

---

### 4. `backend/src/main/resources/karate/execute-http.feature` (utility, request-response)

**Analog:** `backend/src/main/resources/karate/execute-http.feature` (lines 13-44)

**Core Execution Branching Pattern:**
```karate
  Scenario: Run HTTP Step
    * if (continueOnFailure) karate.configure('continueOnStepFailure', true)
    * url httpCfg.url
    * headers mergedHeaders
    * params httpCfg.params || {}

    # Evaluate body type: Form URL-Encoded vs JSON
    * def isForm = (httpCfg.bodyType == 'FORM_URL_ENCODED') || (mergedHeaders && (mergedHeaders['Content-Type'] == 'application/x-www-form-urlencoded' || mergedHeaders['content-type'] == 'application/x-www-form-urlencoded'))
    * def formPayload = isForm ? (httpCfg.formData || httpCfg.body || {}) : null
    * def reqBody = (!isForm && httpMethod != 'get' && httpMethod != 'delete') ? httpCfg.body : null

    # Apply Karate keywords conditionally - never call both
    * if (isForm) form fields formPayload
    * if (!isForm && reqBody != null) request reqBody

    # Configure Karate v2 Polling & Retry
    * def retryAttempts = httpCfg.retryCount || 1
    * def retryInterval = httpCfg.retryIntervalMs || 500
    * eval karate.configure('retry', { count: retryAttempts, interval: retryInterval })
    * retry until responseStatus == expectedStatus

    # Perform Native HTTP Call
    * method httpMethod

    # Evaluate Assertions & Status
    * def assertionResult = Bridge.evaluateAssertions(executionId, resolvedStep, responseStatus, response)
    * def assertionDetails = assertionResult.details
    * def assertionPassed = assertionResult.passed

    # Extract Variables
    * def extractedVars = Bridge.extractVariables(executionId, resolvedStep, response, responseStatus, responseHeaders)

    # Karate Assertions for Report Visibility
    * match responseStatus == expectedStatus
    * match assertionPassed == true

    # Record Success to Bridge with effective request payload (formPayload or reqBody)
    * eval Bridge.onStepFinished(executionId, resolvedStep, true, responseStatus, isForm ? formPayload : httpCfg.body, response, extractedVars, assertionDetails, null, mergedHeaders)
```

---

### 5. `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` (service, transform)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java`

**Canvas-to-AST Derivation Pattern (lines 668-703):**
```java
// TestWorkflowService.java:668-703
if (nodeType.contains("http")) {
    step.setType(StepType.HTTP_REQUEST);
    HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
    httpConfig.setUrl((String) data.getOrDefault("url", "http://localhost:8080/api/v1/mock/orders"));
    httpConfig.setMethod((String) data.getOrDefault("method", "GET"));

    if (data.get("bodyType") != null) {
        httpConfig.setBodyType(String.valueOf(data.get("bodyType")));
    }
    if (data.get("formData") instanceof Map) {
        httpConfig.setFormData((Map<String, Object>) data.get("formData"));
    } else if (data.get("formData") instanceof List<?> list) {
        Map<String, Object> formMap = new LinkedHashMap<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m && m.get("key") != null) {
                formMap.put(String.valueOf(m.get("key")), m.get("value"));
            }
        }
        httpConfig.setFormData(formMap);
    }
    // ... headers, params, retry configuration ...
    step.setHttpConfig(httpConfig);
}
```

**Standalone Karate Export Generation Pattern (lines 344-375):**
```java
// TestWorkflowService.java:368-375
boolean isForm = (cfg.getBodyType() != null && "FORM_URL_ENCODED".equalsIgnoreCase(cfg.getBodyType()))
        || (cfg.getHeaders() != null && "application/x-www-form-urlencoded".equalsIgnoreCase(cfg.getHeaders().get("Content-Type")));

if (isForm) {
    Map<String, Object> formMap = cfg.getFormData() != null && !cfg.getFormData().isEmpty()
            ? cfg.getFormData()
            : (cfg.getBody() instanceof Map ? (Map<String, Object>) cfg.getBody() : Collections.emptyMap());

    sb.append("    And form fields ").append(formatFormFieldsExpression(formMap)).append("\n");
} else if (cfg.getBody() != null) {
    try {
        String bodyJson = objectMapper.writeValueAsString(cfg.getBody());
        sb.append("    And request ").append(bodyJson).append("\n");
    } catch (Exception e) {
        sb.append("    And request '").append(cfg.getBody()).append("'\n");
    }
}
```

**Expression Formatter Pattern (lines 531-561):**
```java
// TestWorkflowService.java:531-561
private String formatFormFieldsExpression(Map<String, Object> formMap) {
    if (formMap == null || formMap.isEmpty()) {
        return "{}";
    }
    boolean hasVariables = formMap.values().stream()
            .anyMatch(v -> v != null && v.toString().contains("${"));

    if (!hasVariables) {
        try {
            return objectMapper.writeValueAsString(formMap);
        } catch (Exception ignored) {}
    }

    StringBuilder sb = new StringBuilder("{ ");
    int idx = 0;
    for (Map.Entry<String, Object> entry : formMap.entrySet()) {
        if (idx++ > 0) sb.append(", ");
        String key = entry.getKey().replace("'", "\\'");
        String val = entry.getValue() != null ? entry.getValue().toString() : "";
        sb.append("'").append(key).append("': ");
        if (val.contains("${")) {
            sb.append(formatKarateExpression(val));
        } else {
            sb.append("'").append(val.replace("'", "\\'")).append("'");
        }
    }
    sb.append(" }");
    return sb.toString();
}
```

---

### 6. `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java` (lines 20-33)

**Imports Pattern (lines 1-15):**
```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
```

**Form-Encoded Endpoint Pattern:**
```java
// MockTargetApiController.java
@PostMapping(value = "/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
@Operation(summary = "OAuth2 / Form URL-Encoded token login", description = "Authenticates using application/x-www-form-urlencoded parameters and returns access token")
public ResponseEntity<Map<String, Object>> formLogin(@RequestParam Map<String, String> formParams) {
    String username = formParams != null && formParams.containsKey("username") ?
            formParams.get("username") : "guest";
    String grantType = formParams != null ? formParams.getOrDefault("grant_type", "password") : "password";

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("access_token", "mock-token-" + UUID.randomUUID().toString());
    resp.put("token_type", "Bearer");
    resp.put("expires_in", 3600);
    resp.put("username", username);
    resp.put("grant_type", grantType);
    resp.put("received_fields", formParams != null ? formParams : Collections.emptyMap());
    return ResponseEntity.ok(resp);
}
```

---

### 7. `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java`

**Imports Pattern (lines 1-43):**
```java
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
```

**Portability Test Pattern (lines 260-275):**
```java
@Test
@DisplayName("Verify Standalone Karate .feature export generates and executes form fields")
public void testStandaloneKarateFeatureExportFormUrlEncoded() throws Exception {
    WorkflowCreateRequest req = new WorkflowCreateRequest();
    req.setName("Form Encoded Export Test");

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
    data.put("formData", Map.of("username", "admin", "grant_type", "password"));
    data.put("expectedStatus", 200);
    node.setData(data);

    state.setNodes(List.of(node));
    req.setCanvasState(state);

    TestWorkflowEntity wf = workflowService.createWorkflow(req);
    String feature = workflowService.generateStandaloneKarateFeature(wf.getId());

    assertTrue(feature.contains("And form fields"), "Export must contain 'form fields' keyword");
    assertFalse(feature.contains("And request"), "Export must NOT emit 'request' for form-encoded steps");

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
```

**Dynamic Runner Test Pattern (lines 524-535):**
```java
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
    httpConfig.setFormData(Map.of("username", "testuser", "grant_type", "client_credentials"));
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
```

---

## Shared Patterns

### 1. UI Segmented Control & Key-Value Table Pattern
**Source:** `frontend/src/components/drawer/NodeConfigDrawer.tsx` (lines 680-730, 810-825)  
**Apply to:** `NodeConfigDrawer.tsx` Request Body Format selector and form fields table.  
- Toggle button active state: `bg-blue-600/20 text-blue-300 border border-blue-500 font-semibold`
- Inactive state: `text-slate-400 hover:text-slate-200`
- Table rows stored in local state as array `{ key: string, value: string }[]` to avoid key collision
- Monospace font `font-mono` on inputs with `focus:border-blue-500 focus:outline-none`
- Delete icon button: `Trash2` with `hover:text-rose-400`
- Variable trigger button: `Variable` with `hover:text-emerald-400` triggering `openVarSelector('formParam_${idx}')`

### 2. Standalone Karate Expression Formatting Pattern
**Source:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` (lines 531-561)  
**Apply to:** Form field values in `formatFormFieldsExpression`.  
- Any string containing `${var}` or `${$env:VAR}` is formatted using JavaScript concatenation (`'prefix ' + varName`)
- Environment variable lookups emitted as `(karate.properties['x'] || java.lang.System.getenv('x'))`

### 3. Spring MVC RequestParam Form Binding Pattern
**Source:** `backend/src/main/java/com/orchestrator/platform/web/MockTargetApiController.java`  
**Apply to:** Form-encoded controller endpoints.  
- Explicit annotation: `@PostMapping(value = "...", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)`
- Parameter extraction: `@RequestParam Map<String, String> formParams`
- Return type: `ResponseEntity<Map<String, Object>>`

### 4. Integration Test Verification Pattern (Dynamic vs Standalone)
**Source:** `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java`  
**Apply to:** Automated test validation in Wave 0 and Wave 2.  
- Dynamic in-memory testing: Save entity to repository -> `karateRunner.executeWorkflow(...)` -> verify `ExecutionStepResult`
- Standalone feature export testing: `workflowService.generateStandaloneKarateFeature(...)` -> write to `Files.createTempFile(...)` -> execute via `Runner.path(...).parallel(1)` -> verify `SuiteResult.isFailed() == false`

---

## No Analog Found

All 7 target files have existing implementations or exact pattern analogs within the project repository. None require third-party or off-pattern design.

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| *(None)* | — | — | All files map to established repository patterns. |

---

## Metadata

**Analog search scope:**
- `frontend/src/types/`
- `frontend/src/components/drawer/`
- `backend/src/main/java/com/orchestrator/platform/`
- `backend/src/main/resources/karate/`
- `backend/src/test/java/com/orchestrator/platform/`

**Files scanned:** 12  
**Pattern extraction date:** 2026-11-07  
