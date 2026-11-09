# Phase 05: Sub-Workflow Variable Promotion & Global Flow Headers - Pattern Map

**Mapped:** 2026-11-09  
**Files analyzed:** 13  
**Analogs found:** 13 / 13  

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/src/main/java/com/orchestrator/platform/domain/model/ast/SetupWorkflowConfig.java` | model | CRUD | `backend/src/main/java/com/orchestrator/platform/domain/model/ast/SubWorkflowStepConfig.java` | exact |
| `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java` | model | CRUD | `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java` | exact (self) |
| `backend/src/main/java/com/orchestrator/platform/domain/model/canvas/CanvasState.java` | model | CRUD | `backend/src/main/java/com/orchestrator/platform/domain/model/canvas/CanvasState.java` | exact (self) |
| `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java` | service / utility | request-response / transform | `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java` | exact (self) |
| `backend/src/main/resources/karate/execute-step.feature` | config / script | event-driven | `backend/src/main/resources/karate/execute-step.feature` | exact (self) |
| `backend/src/main/resources/karate/execute-http.feature` | config / script | request-response | `backend/src/main/resources/karate/execute-http.feature` | exact (self) |
| `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java` | service | batch / transform | `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java` | exact (self) |
| `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` | service | transform / CRUD | `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` | exact (self) |
| `backend/src/test/java/com/orchestrator/platform/service/execution/SubWorkflowCompositionTest.java` | test | request-response | `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java` | exact |
| `frontend/src/types/workflow.ts` | model / utility | CRUD | `frontend/src/types/workflow.ts` | exact (self) |
| `frontend/src/components/navbar/WorkflowSettingsModal.tsx` | component | request-response | `frontend/src/components/navbar/WorkflowSettingsModal.tsx` | exact (self) |
| `frontend/src/components/canvas/nodes/SubWorkflowNode.tsx` | component | request-response | `frontend/src/components/canvas/nodes/SubWorkflowNode.tsx` | exact (self) |
| `frontend/src/components/drawer/NodeConfigDrawer.tsx` | component | request-response | `frontend/src/components/drawer/NodeConfigDrawer.tsx` | exact (self) |

---

## Pattern Assignments

### 1. `backend/src/main/java/com/orchestrator/platform/domain/model/ast/SetupWorkflowConfig.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/orchestrator/platform/domain/model/ast/SubWorkflowStepConfig.java`

**Imports Pattern** (`SubWorkflowStepConfig.java:1-6`):
```java
package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
```

**Core Model Pattern** (`SubWorkflowStepConfig.java:7-40`):
```java
public class SetupWorkflowConfig implements Serializable {
    private String workflowId;
    private String workflowName;
    private boolean enabled = true;
    private boolean inheritEnvAndSecrets = true;
    private List<VariableExtraction> extractions = new ArrayList<>();

    public SetupWorkflowConfig() {}

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isInheritEnvAndSecrets() { return inheritEnvAndSecrets; }
    public void setInheritEnvAndSecrets(boolean inheritEnvAndSecrets) { this.inheritEnvAndSecrets = inheritEnvAndSecrets; }

    public List<VariableExtraction> getExtractions() { return extractions; }
    public void setExtractions(List<VariableExtraction> extractions) {
        this.extractions = extractions != null ? extractions : new ArrayList<>();
    }
}
```

---

### 2. `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java`

**Core Pattern** (`WorkflowAst.java:10-45`):
POJO with initialized defaults and explicit getters/setters:
```java
    // New field to add
    private SetupWorkflowConfig setupWorkflow;

    // Getter & Setter
    public SetupWorkflowConfig getSetupWorkflow() { return setupWorkflow; }
    public void setSetupWorkflow(SetupWorkflowConfig setupWorkflow) { this.setupWorkflow = setupWorkflow; }
```

---

### 3. `backend/src/main/java/com/orchestrator/platform/domain/model/canvas/CanvasState.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/orchestrator/platform/domain/model/canvas/CanvasState.java`

**Core Pattern** (`CanvasState.java:10-49`):
POJO matching JSONB serialized state from React UI:
```java
    // New field to add
    private SetupWorkflowConfig setupWorkflow;

    // Getter & Setter
    public SetupWorkflowConfig getSetupWorkflow() { return setupWorkflow; }
    public void setSetupWorkflow(SetupWorkflowConfig setupWorkflow) { this.setupWorkflow = setupWorkflow; }
```

---

### 4. `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java` (service / utility, request-response / transform)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`

**Variable Resolution & Context Merging Pattern** (`DynamicKarateBridge.java:172-186`):
```java
        Map<String, Object> context = activeContexts.getOrDefault(executionId, new ConcurrentHashMap<>());
        if (globalContext != null) {
            context.putAll(globalContext);
        }
```

**Header Merging & Dynamic String Resolution Pattern** (`DynamicKarateBridge.java:189-234`):
```java
    // Overloaded method supporting dynamic context interpolation (SUBWF-01)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> computeEffectiveHeaders(
            String executionId,
            Map<String, Object> flowHeaders,
            Map<String, Object> stepConfig,
            Map<String, Object> globalContext) {

        Map<String, Object> resolvedFlowHeaders = new LinkedHashMap<>();
        if (flowHeaders != null) {
            Map<String, Object> activeContext = new HashMap<>();
            if (executionId != null) {
                activeContext.putAll(activeContexts.getOrDefault(executionId, Collections.emptyMap()));
            }
            if (globalContext != null) {
                activeContext.putAll(globalContext);
            }

            for (Map.Entry<String, Object> entry : flowHeaders.entrySet()) {
                if (entry.getValue() != null && entry.getValue().toString().contains("${")) {
                    if (instance != null && instance.contextEngine != null) {
                        String resolved = instance.contextEngine.resolveString(entry.getValue().toString(), activeContext);
                        resolvedFlowHeaders.put(entry.getKey(), resolved);
                    } else {
                        resolvedFlowHeaders.put(entry.getKey(), entry.getValue());
                    }
                } else {
                    resolvedFlowHeaders.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Delegate to existing inheritance & exclusion filter logic
        return computeEffectiveHeaders(resolvedFlowHeaders, stepConfig);
    }

    // Existing signature retained for backward compatibility
    public static Map<String, Object> computeEffectiveHeaders(Map<String, Object> flowHeaders, Map<String, Object> stepConfig) {
        // ... (lines 190-234)
    }
```

---

### 5. `backend/src/main/resources/karate/execute-step.feature` (config / script, event-driven)

**Analog:** `backend/src/main/resources/karate/execute-step.feature`

**Step Dispatch Pattern** (`execute-step.feature:14-31`):
Branch 1 HTTP invocation updated to pass `globalContext: globalContext`:
```gherkin
    # Branch 1: HTTP API Request (updated with dynamic globalContext for reactive header evaluation)
    * if (stepType == 'HTTP_REQUEST') karate.set('stepRes', karate.call('classpath:karate/execute-http.feature', { resolvedStep: resolvedStep, executionId: executionId, flowHeaders: flowHeaders, globalContext: globalContext }))

    # Branch 2: Database Polling via HikariCP
    * if (stepType == 'DB_POLL') karate.set('stepRes', karate.call('classpath:karate/execute-db.feature', { resolvedStep: resolvedStep, executionId: executionId }))

    # Branch 6: Sub-Workflow Execution
    * if (stepType == 'SUB_WORKFLOW') karate.set('stepRes', karate.call('classpath:karate/execute-subworkflow.feature', { resolvedStep: resolvedStep, executionId: executionId, globalContext: globalContext }))
```

---

### 6. `backend/src/main/resources/karate/execute-http.feature` (config / script, request-response)

**Analog:** `backend/src/main/resources/karate/execute-http.feature`

**Dynamic Header Computation Pattern** (`execute-http.feature:4-12`):
```gherkin
  Background:
    * def Bridge = Java.type('com.orchestrator.platform.service.execution.DynamicKarateBridge')
    * def httpCfg = resolvedStep.httpConfig || {}
    * def httpMethod = (httpCfg.method || 'GET').toLowerCase()
    * def expectedStatus = httpCfg.expectedStatus || 200
    * def continueOnFailure = resolvedStep.continueOnFailure || false
    * def fHeaders = karate.get('flowHeaders') || {}
    * def gContext = karate.get('globalContext') || {}
    * def mergedHeaders = Bridge.computeEffectiveHeaders(executionId, fHeaders, httpCfg, gContext)
```

---

### 7. `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java` (service, batch / transform)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java`

**Setup Step Prepending Pattern** (`DynamicKarateRunner.java:141-149`):
```java
        List<WorkflowStep> stepList = new ArrayList<>();

        // SUBWF-02: Synthesize and prepend setup workflow step if configured and enabled
        if (ast != null && ast.getSetupWorkflow() != null && ast.getSetupWorkflow().isEnabled()) {
            SetupWorkflowConfig setupCfg = ast.getSetupWorkflow();
            if (setupCfg.getWorkflowId() != null && !setupCfg.getWorkflowId().isBlank()) {
                WorkflowStep setupStep = new WorkflowStep();
                setupStep.setId("step_setup_" + UUID.randomUUID().toString().substring(0, 8));
                setupStep.setName(setupCfg.getWorkflowName() != null ? setupCfg.getWorkflowName() : "Setup Workflow");
                setupStep.setType(StepType.SUB_WORKFLOW);
                setupStep.setEnabled(true);

                SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
                subConfig.setWorkflowId(setupCfg.getWorkflowId());
                subConfig.setWorkflowName(setupCfg.getWorkflowName());
                subConfig.setInheritEnvAndSecrets(setupCfg.isInheritEnvAndSecrets());
                subConfig.setInheritParentContext(true);
                subConfig.setInheritFlowHeaders(true);
                setupStep.setSubWorkflowConfig(subConfig);

                if (setupCfg.getExtractions() != null && !setupCfg.getExtractions().isEmpty()) {
                    setupStep.setExtractions(new ArrayList<>(setupCfg.getExtractions()));
                }

                stepList.add(setupStep);
            }
        }

        if (ast != null && ast.getSteps() != null) {
            stepList.addAll(ast.getSteps());
        }

        workflowPayload.put("steps", objectMapper.convertValue(stepList, List.class));
        workflowPayload.put("initialVariables", mergedVariables);
        DynamicKarateBridge.initExecution(executionId.toString(), workflowPayload);
```

---

### 8. `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` (service, transform / CRUD)

**Analog:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java`

**Canvas to AST Transfer Pattern** (`TestWorkflowService.java:773-782`):
```java
        if (canvasState != null) {
            if (canvasState.getDefaultHeaders() != null) ast.setDefaultHeaders(new LinkedHashMap<>(canvasState.getDefaultHeaders()));
            if (canvasState.getDynamicHeaders() != null) ast.setDynamicHeaders(new LinkedHashMap<>(canvasState.getDynamicHeaders()));
            if (canvasState.getEnvProperties() != null) ast.setEnvProperties(new LinkedHashMap<>(canvasState.getEnvProperties()));
            if (canvasState.getEnvSecrets() != null) ast.setEnvSecrets(new LinkedHashMap<>(canvasState.getEnvSecrets()));
            if (canvasState.getSecrets() != null) ast.setSecrets(new LinkedHashMap<>(canvasState.getSecrets()));
            if (canvasState.getVariables() != null) ast.setVariables(new HashMap<>(canvasState.getVariables()));
            if (canvasState.getSetupWorkflow() != null) ast.setSetupWorkflow(canvasState.getSetupWorkflow());
        }
```

**Standalone Karate Dynamic Header Function Pattern** (`TestWorkflowService.java:220-227`):
```java
        WorkflowAst ast = workflow.getWorkflowAst();
        boolean hasDynamicDefaultHeaders = ast != null && ast.getDefaultHeaders() != null &&
                ast.getDefaultHeaders().values().stream().anyMatch(v -> v != null && v.contains("${"));

        if (hasDynamicDefaultHeaders) {
            sb.append("    # SUBWF-04: Dynamic Header resolution function for runtime variables\n");
            sb.append("    * def resolveDynamicHeader = function(tmpl) {\n");
            sb.append("        if (!tmpl || tmpl.indexOf('${') === -1) return tmpl;\n");
            sb.append("        return tmpl.replace(/\\$\\{([^}]+)\\}/g, function(match, key) {\n");
            sb.append("          var k = key.trim();\n");
            sb.append("          var val = karate.get(k) || (typeof context !== 'undefined' ? context[k] : null);\n");
            sb.append("          return val !== null && val !== undefined ? val : match;\n");
            sb.append("        });\n");
            sb.append("      }\n");
            sb.append("    * configure headers = function() {\n");
            sb.append("        var h = karate.get('defaultHeaders', {});\n");
            for (Map.Entry<String, String> h : ast.getDefaultHeaders().entrySet()) {
                sb.append("        h['").append(h.getKey()).append("'] = resolveDynamicHeader('")
                  .append(h.getValue().replace("'", "\\'")).append("');\n");
            }
            sb.append("        return h;\n");
            sb.append("      }\n\n");
        } else if (ast != null && ast.getDefaultHeaders() != null && !ast.getDefaultHeaders().isEmpty()) {
            for (Map.Entry<String, String> h : ast.getDefaultHeaders().entrySet()) {
                sb.append("    * configure headers = karate.merge(karate.get('headers', {}), { '")
                        .append(h.getKey()).append("': '").append(h.getValue()).append("' })\n");
            }
            sb.append("\n");
        }
```

---

### 9. `backend/src/test/java/com/orchestrator/platform/service/execution/SubWorkflowCompositionTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java`

**Imports Pattern** (`WorkflowExecutionSyncModeTest.java:1-38`):
```java
package com.orchestrator.platform.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.*;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.service.TestWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
```

**Core Test Structure Pattern** (`WorkflowExecutionSyncModeTest.java:41-90`):
```java
@SpringBootTest
@ActiveProfiles("test")
public class SubWorkflowCompositionTest {

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private TestWorkflowService workflowService;

    @Autowired
    private DynamicKarateRunner karateRunner;

    @Test
    @DisplayName("SUBWF-01: Extracted authToken dynamically resolves in global flow headers for downstream HTTP steps")
    void testDynamicGlobalHeaderVariableResolution() {
        // Setup execution context, assert computeEffectiveHeaders resolves ${authToken}
    }

    @Test
    @DisplayName("SUBWF-02: Pre-requisite Setup Workflow prepends synthetic SUB_WORKFLOW step and promotes variables")
    void testSetupWorkflowExecutionAndPromotion() {
        // Setup workflow with setupWorkflow config, assert step prepending & promotion
    }

    @Test
    @DisplayName("SUBWF-04: Standalone Karate feature export emits dynamic header function for ${...} placeholders")
    void testStandaloneKarateDynamicHeaderExport() {
        // Create workflow with Authorization: Bearer ${authToken}, export feature, verify configure headers = function()
    }
}
```

---

### 10. `frontend/src/types/workflow.ts` (model / utility, CRUD)

**Analog:** `frontend/src/types/workflow.ts`

**Interface Declaration Pattern** (`workflow.ts:63-70, 112-121`):
```typescript
export interface SetupWorkflowConfig {
  workflowId: string;
  workflowName?: string;
  enabled?: boolean;
  inheritEnvAndSecrets?: boolean;
  extractions?: VariableExtraction[];
}

export interface WorkflowAst {
  steps: WorkflowStep[];
  variables?: Record<string, any>;
  defaultHeaders?: Record<string, string>;
  dynamicHeaders?: Record<string, string>;
  envProperties?: Record<string, Record<string, any>>;
  envSecrets?: Record<string, Record<string, string>>;
  secrets?: Record<string, string>;
  timeoutMs?: number;
  setupWorkflow?: SetupWorkflowConfig; // Added
}

export interface CanvasState {
  nodes: CanvasNode[];
  edges: CanvasEdge[];
  viewport: { x: number; y: number; zoom: number };
  variables?: Record<string, any>;
  defaultHeaders?: Record<string, string>;
  dynamicHeaders?: Record<string, string>;
  envProperties?: Record<string, Record<string, any>>;
  envSecrets?: Record<string, Record<string, string>>;
  secrets?: Record<string, string>;
  setupWorkflow?: SetupWorkflowConfig; // Added
}
```

---

### 11. `frontend/src/components/navbar/WorkflowSettingsModal.tsx` (component, request-response)

**Analog:** `frontend/src/components/navbar/WorkflowSettingsModal.tsx`

**Tab State & Props Pattern** (`WorkflowSettingsModal.tsx:42-65`):
```typescript
  const [activeTab, setActiveTab] = useState<'general' | 'setupWorkflow' | 'defaultHeaders' | 'dynamicHeaders' | 'envProperties' | 'secrets'>('general');
  const [setupConfig, setSetupConfig] = useState<SetupWorkflowConfig>({
    workflowId: '',
    workflowName: '',
    enabled: true,
    inheritEnvAndSecrets: true,
    extractions: [],
  });
```

**Tab Button UI Pattern** (`WorkflowSettingsModal.tsx:215-247`):
```tsx
  <button
    onClick={() => setActiveTab('setupWorkflow')}
    className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
      activeTab === 'setupWorkflow'
        ? 'border-indigo-500 text-indigo-400 font-semibold'
        : 'border-transparent text-slate-400 hover:text-slate-200'
    }`}
  >
    <Workflow className="w-3.5 h-3.5" />
    Setup Workflow
    {setupConfig.workflowId && (
      <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
    )}
  </button>
```

**Quick-Bind Token Toggle Pattern** (`WorkflowSettingsModal.tsx:161-171`):
Auto-populating `Authorization: Bearer ${authToken}` into `defaultHeaders` when quick-bind is checked.

---

### 12. `frontend/src/components/canvas/nodes/SubWorkflowNode.tsx` (component, request-response)

**Analog:** `frontend/src/components/canvas/nodes/SubWorkflowNode.tsx`

**Chip & Badge UI Pattern** (`SubWorkflowNode.tsx:78-95`):
```tsx
      {/* Visual Badge for Exported Variables (SUBWF-03) */}
      {(nodeData.extractions || []).length > 0 && (
        <div className="mt-2 pt-2 border-t border-slate-100 dark:border-slate-800/60">
          <div className="flex items-center gap-1 text-[10px] text-amber-600 dark:text-amber-400 font-medium mb-1">
            <Zap className="w-3 h-3" />
            <span>Exports ({nodeData.extractions!.length}):</span>
          </div>
          <div className="flex flex-wrap gap-1">
            {nodeData.extractions!.map((ext, idx) => (
              <span
                key={idx}
                className="px-1.5 py-0.5 rounded bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-300 font-mono text-[9px] truncate max-w-[140px]"
                title={`\${${ext.variableName}} <- ${ext.jsonPath || ext.source}`}
              >
                ${'{'}{ext.variableName}{'}'}
              </span>
            ))}
          </div>
        </div>
      )}
```

---

### 13. `frontend/src/components/drawer/NodeConfigDrawer.tsx` (component, request-response)

**Analog:** `frontend/src/components/drawer/NodeConfigDrawer.tsx`

**Upstream Variable Autocomplete & Insertion Pattern** (`NodeConfigDrawer.tsx:278-320, 703-734`):
```tsx
  // Extract all available upstream variable extractions from preceding canvas nodes
  const upstreamExportedVars = React.useMemo(() => {
    const vars: Array<{ variableName: string; sourceNodeName: string }> = [];
    allNodes.forEach((node) => {
      if (node.id !== selectedNode?.id && node.data?.extractions) {
        node.data.extractions.forEach((ext: VariableExtraction) => {
          if (ext.variableName) {
            vars.push({ variableName: ext.variableName, sourceNodeName: node.data.name || node.id });
          }
        });
      }
    });
    return vars;
  }, [allNodes, selectedNode?.id]);

  // Render suggestion pill block directly above URL and Header inputs
  const renderVariableSuggestions = (targetField: string) => {
    if (upstreamExportedVars.length === 0) return null;
    return (
      <div className="flex items-center gap-1.5 flex-wrap mb-1">
        <span className="text-[9px] text-slate-500 font-medium flex items-center gap-0.5">
          <Sparkles className="w-2.5 h-2.5 text-amber-400" /> Insert:
        </span>
        {upstreamExportedVars.map((v, i) => (
          <button
            key={i}
            type="button"
            onClick={() => {
              setVarTargetField(targetField);
              handleInsertVariable(`\${${v.variableName}}`);
            }}
            className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 border border-indigo-500/20 transition-colors"
            title={`From step "${v.sourceNodeName}"`}
          >
            + ${'{'}{v.variableName}{'}'}
          </button>
        ))}
      </div>
    );
  };
```

---

## Shared Patterns

### 1. Context Resolution & String Interpolation
**Source:** `backend/src/main/java/com/orchestrator/platform/service/WorkflowContextEngine.java` lines 37-53  
**Apply to:** `DynamicKarateBridge.java`, `DynamicKarateRunner.java`, `TestWorkflowService.java`
```java
// Interpolates ${varName} expressions while leaving un-resolvable tokens intact
String resolved = contextEngine.resolveString(inputTemplate, activeContext);
```

### 2. Null-Safe Collection Handling in AST / DTOs
**Source:** `backend/src/main/java/com/orchestrator/platform/domain/model/ast/WorkflowAst.java` lines 11-18  
**Apply to:** `SetupWorkflowConfig.java`, `WorkflowAst.java`, `CanvasState.java`
```java
// Inline default initialization prevents null pointer exceptions in serialization & deserialization
private List<VariableExtraction> extractions = new ArrayList<>();
```

### 3. Dynamic Karate Header JavaScript Function
**Source:** `backend/src/main/java/com/orchestrator/platform/service/TestWorkflowService.java` lines 245-260  
**Apply to:** Standalone Karate feature export (`TestWorkflowService.java`)
```gherkin
* configure headers = function() {
    var h = karate.get('defaultHeaders', {});
    // Dynamically evaluate expressions per HTTP call
    return h;
  }
```

### 4. Interactive Chip Pill Design System (Tailwind)
**Source:** `frontend/src/components/drawer/NodeConfigDrawer.tsx` lines 719-731  
**Apply to:** `SubWorkflowNode.tsx`, `NodeConfigDrawer.tsx`, `WorkflowSettingsModal.tsx`
```tsx
className="px-2 py-1 rounded-md text-[10px] font-mono font-medium border flex items-center gap-1 transition-all bg-indigo-500/15 border-indigo-500/30 text-indigo-300"
```

---

## No Analog Found

All 13 new and modified files have direct, high-quality analogs in the existing repository. No un-matched files exist for Phase 05.

---

## Metadata

**Analog search scope:** `backend/src/main/java/`, `backend/src/main/resources/karate/`, `backend/src/test/java/`, `frontend/src/`  
**Files scanned:** 18  
**Pattern extraction date:** 2026-11-09  
