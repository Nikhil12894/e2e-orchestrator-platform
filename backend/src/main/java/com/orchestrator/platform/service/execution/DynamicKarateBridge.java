package com.orchestrator.platform.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.DbPollStepConfig;
import com.orchestrator.platform.domain.model.ast.KafkaAssertStepConfig;
import com.orchestrator.platform.domain.model.ast.SubWorkflowStepConfig;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.dto.ExecutionEventDto;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.service.WorkflowContextEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicKarateBridge {

    private static final Logger log = LoggerFactory.getLogger(DynamicKarateBridge.class);

    private static DynamicKarateBridge instance;

    private final WorkflowContextEngine contextEngine;
    private final DbPollExecutor dbPollExecutor;
    private final KafkaAssertExecutor kafkaAssertExecutor;
    private final ExecutionLogBroadcaster logBroadcaster;
    private final ObjectMapper objectMapper;
    private final TestWorkflowRepository workflowRepository;

    // Per-execution live step results and contexts
    private static final Map<String, Map<String, Object>> activePayloads = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> activeContexts = new ConcurrentHashMap<>();
    private static final Map<String, List<ExecutionStepResult>> activeStepResults = new ConcurrentHashMap<>();
    private static final Map<String, Long> stepStartTimes = new ConcurrentHashMap<>();
    private static final Set<String> cancelledExecutions = ConcurrentHashMap.newKeySet();

    public DynamicKarateBridge(WorkflowContextEngine contextEngine,
                               DbPollExecutor dbPollExecutor,
                               KafkaAssertExecutor kafkaAssertExecutor,
                               ExecutionLogBroadcaster logBroadcaster,
                               ObjectMapper objectMapper) {
        this(contextEngine, dbPollExecutor, kafkaAssertExecutor, logBroadcaster, objectMapper, null);
    }

    @Autowired
    public DynamicKarateBridge(WorkflowContextEngine contextEngine,
                               DbPollExecutor dbPollExecutor,
                               KafkaAssertExecutor kafkaAssertExecutor,
                               ExecutionLogBroadcaster logBroadcaster,
                               ObjectMapper objectMapper,
                               @Autowired(required = false) TestWorkflowRepository workflowRepository) {
        this.contextEngine = contextEngine;
        this.dbPollExecutor = dbPollExecutor;
        this.kafkaAssertExecutor = kafkaAssertExecutor;
        this.logBroadcaster = logBroadcaster;
        this.objectMapper = objectMapper;
        this.workflowRepository = workflowRepository;
        DynamicKarateBridge.instance = this;
    }

    public static DynamicKarateBridge getInstance() {
        return instance;
    }

    // --- Static methods invoked directly by Karate Generic Features ---

    @SuppressWarnings("unchecked")
    public static void initExecution(String executionId, Map<String, Object> payload) {
        activePayloads.put(executionId, payload != null ? payload : Collections.emptyMap());
        Map<String, Object> initVars = payload != null && payload.get("initialVariables") instanceof Map ?
                (Map<String, Object>) payload.get("initialVariables") : Collections.emptyMap();
        activeContexts.put(executionId, new ConcurrentHashMap<>(initVars));
        activeStepResults.put(executionId, Collections.synchronizedList(new ArrayList<>()));
    }

    public static Map<String, Object> getActiveWorkflowPayload(String executionId) {
        return activePayloads.getOrDefault(executionId, Collections.emptyMap());
    }

    public static Map<String, Object> getExecutionContext(String executionId) {
        return activeContexts.getOrDefault(executionId, Collections.emptyMap());
    }

    public static List<ExecutionStepResult> getExecutionStepResults(String executionId) {
        return activeStepResults.getOrDefault(executionId, Collections.emptyList());
    }

    public static void markCancelled(String executionId) {
        if (executionId != null) {
            cancelledExecutions.add(executionId);
        }
    }

    public static boolean isExecutionCancelled(String executionId) {
        if (executionId != null && cancelledExecutions.contains(executionId)) {
            return true;
        }
        return false;
    }

    public static Map<String, Object> executeDelay(String executionId, Object pauseMsObj) {
        long pauseMs = 1000L;
        if (pauseMsObj instanceof Number num) {
            pauseMs = num.longValue();
        } else if (pauseMsObj != null) {
            try {
                pauseMs = Long.parseLong(String.valueOf(pauseMsObj).trim());
            } catch (Exception ignored) {}
        }

        if (isExecutionCancelled(executionId)) {
            Thread.interrupted();
            return Map.of("interrupted", true, "completed", false);
        }

        try {
            long remaining = pauseMs;
            long slice = 50L;
            while (remaining > 0) {
                if (isExecutionCancelled(executionId)) {
                    Thread.interrupted();
                    return Map.of("interrupted", true, "completed", false);
                }
                long sleepTime = Math.min(remaining, slice);
                Thread.sleep(sleepTime);
                remaining -= sleepTime;
            }
            return Map.of("interrupted", false, "completed", true);
        } catch (InterruptedException e) {
            // Clear interrupt status so Karate scenario does not crash with unhandled exception
            Thread.interrupted();
            log.info("Delay step interrupted for execution [{}] (cancelled by user)", executionId);
            return Map.of("interrupted", true, "completed", false);
        }
    }

    public static void cleanupExecution(String executionId) {
        if (executionId != null) {
            cancelledExecutions.remove(executionId);
            stepStartTimes.remove(executionId);
        }
    }

    public static void onWorkflowStart(String executionId, String workflowId, int totalSteps) {
        if (instance == null) return;
        UUID execId = parseUuid(executionId);
        UUID wfId = parseUuid(workflowId);
        Map<String, Object> payload = activePayloads.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
        if (workflowId != null) {
            payload.put("workflowId", workflowId);
        }
        instance.logBroadcaster.broadcast(new ExecutionEventDto(
                execId, wfId, "WORKFLOW_STARTED", null, null,
                ExecutionStatus.RUNNING, "Starting workflow execution (" + totalSteps + " steps queued)",
                Map.of("totalSteps", totalSteps)
        ));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveStepVariables(String executionId, Map<String, Object> rawStep, Map<String, Object> globalContext) {
        if (instance == null || rawStep == null) return rawStep;

        Map<String, Object> context = activeContexts.getOrDefault(executionId, new ConcurrentHashMap<>());
        if (globalContext != null) {
            context.putAll(globalContext);
        }

        try {
            Object resolved = instance.contextEngine.resolveObject(rawStep, context);
            if (resolved instanceof Map) {
                return (Map<String, Object>) resolved;
            }
        } catch (Exception e) {
            log.error("Failed to resolve variables for step: {}", e.getMessage(), e);
        }
        return rawStep;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> computeEffectiveHeaders(String executionId,
                                                              Map<String, Object> flowHeaders,
                                                              Map<String, Object> stepConfig,
                                                              Map<String, Object> globalContext) {
        Map<String, Object> activeContext = new ConcurrentHashMap<>();
        if (executionId != null && activeContexts.containsKey(executionId)) {
            activeContext.putAll(activeContexts.get(executionId));
        }
        if (globalContext != null) {
            activeContext.putAll(globalContext);
        }

        Map<String, Object> resolvedFlowHeaders = new LinkedHashMap<>();
        if (flowHeaders != null) {
            for (Map.Entry<String, Object> entry : flowHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    String valStr = entry.getValue().toString();
                    if (valStr.contains("${") && instance != null && instance.contextEngine != null) {
                        try {
                            String resolved = instance.contextEngine.resolveString(valStr, activeContext);
                            resolvedFlowHeaders.put(entry.getKey(), resolved);
                        } catch (Exception e) {
                            log.warn("Failed to resolve dynamic header [{}]: {}", entry.getKey(), e.getMessage());
                            resolvedFlowHeaders.put(entry.getKey(), valStr);
                        }
                    } else {
                        resolvedFlowHeaders.put(entry.getKey(), entry.getValue());
                    }
                } else {
                    resolvedFlowHeaders.put(entry.getKey(), null);
                }
            }
        }

        return computeEffectiveHeaders(resolvedFlowHeaders, stepConfig);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> computeEffectiveHeaders(Map<String, Object> flowHeaders, Map<String, Object> stepConfig) {
        if (stepConfig == null) {
            return flowHeaders != null ? new LinkedHashMap<>(flowHeaders) : new LinkedHashMap<>();
        }

        boolean inherit = true;
        if (stepConfig.containsKey("inheritGlobalHeaders")) {
            Object val = stepConfig.get("inheritGlobalHeaders");
            inherit = (val instanceof Boolean b) ? b : !"false".equalsIgnoreCase(String.valueOf(val));
        }

        Map<String, Object> effective = new LinkedHashMap<>();
        if (inherit && flowHeaders != null) {
            effective.putAll(flowHeaders);
        }

        // Case-insensitive removal of excluded global headers
        Object excludedObj = stepConfig.get("excludedGlobalHeaders");
        if (excludedObj instanceof List<?> excludedList) {
            for (Object item : excludedList) {
                if (item != null) {
                    String toRemove = item.toString().trim();
                    effective.keySet().removeIf(k -> k.equalsIgnoreCase(toRemove));
                }
            }
        }

        // Merge step custom headers (adding or overriding)
        Object stepHeadersObj = stepConfig.get("headers");
        if (stepHeadersObj instanceof Map<?, ?> stepHeaders) {
            for (Map.Entry<?, ?> entry : stepHeaders.entrySet()) {
                if (entry.getKey() != null) {
                    String key = entry.getKey().toString().trim();
                    Object val = entry.getValue();
                    if (val == null || val.toString().trim().isEmpty()) {
                        // Empty value explicitly suppresses this header
                        effective.remove(key);
                    } else {
                        effective.put(key, val);
                    }
                }
            }
        }

        return effective;
    }

    public static void onStepStart(String executionId, String stepId, String stepName, String stepType) {
        if (instance == null) return;
        UUID execId = parseUuid(executionId);
        stepStartTimes.put(executionId + "_" + stepId, System.currentTimeMillis());

        instance.logBroadcaster.broadcast(new ExecutionEventDto(
                execId, null, "STEP_STARTED", stepId, stepName,
                ExecutionStatus.RUNNING, "Executing " + stepType + " step: " + stepName,
                Map.of("stepType", stepType)
        ));
    }

    public static Map<String, Object> executeDbPoll(String executionId, Map<String, Object> step) {
        if (instance == null) return Map.of("matched", false, "errorMessage", "Bridge not initialized");
        try {
            Object configObj = step.get("dbPollConfig");
            DbPollStepConfig config = instance.objectMapper.convertValue(configObj, DbPollStepConfig.class);

            // Resilient variable interpolation safeguard for DB connections
            Map<String, Object> ctx = activeContexts.getOrDefault(executionId, Collections.emptyMap());
            if (config.getJdbcUrl() != null && config.getJdbcUrl().contains("${")) {
                config.setJdbcUrl(instance.contextEngine.resolveString(config.getJdbcUrl(), ctx));
            }
            if (config.getUsername() != null && config.getUsername().contains("${")) {
                config.setUsername(instance.contextEngine.resolveString(config.getUsername(), ctx));
            }
            if (config.getPassword() != null && config.getPassword().contains("${")) {
                config.setPassword(instance.contextEngine.resolveString(config.getPassword(), ctx));
            }
            if (config.getQuery() != null && config.getQuery().contains("${")) {
                config.setQuery(instance.contextEngine.resolveString(config.getQuery(), ctx));
            }

            DbPollExecutor.DbPollResult result = instance.dbPollExecutor.execute(config);

            Map<String, Object> resMap = new HashMap<>();
            resMap.put("matched", result.isMatched());
            resMap.put("attempts", result.getAttempts());
            resMap.put("durationMs", result.getDurationMs());
            resMap.put("rows", result.getRows());
            resMap.put("latestRow", result.getLatestRow());
            resMap.put("errorMessage", result.getErrorMessage());

            instance.logBroadcaster.emitLog(parseUuid(executionId), null,
                    String.valueOf(step.get("id")), String.valueOf(step.get("name")),
                    "DB Poll complete. Matched: " + result.isMatched() + ", attempts: " + result.getAttempts() +
                            (result.getErrorMessage() != null ? ", error: " + result.getErrorMessage() : ""));

            return resMap;
        } catch (Exception e) {
            log.error("Error executing DB poll: {}", e.getMessage(), e);
            return Map.of("matched", false, "errorMessage", e.getMessage());
        }
    }

    public static Map<String, Object> executeKafkaAssert(String executionId, Map<String, Object> step) {
        if (instance == null) return Map.of("matched", false, "errorMessage", "Bridge not initialized");
        try {
            Object configObj = step.get("kafkaConfig");
            KafkaAssertStepConfig config = instance.objectMapper.convertValue(configObj, KafkaAssertStepConfig.class);
            KafkaAssertExecutor.KafkaAssertResult result = instance.kafkaAssertExecutor.execute(config);

            Map<String, Object> resMap = new HashMap<>();
            resMap.put("matched", result.isMatched());
            resMap.put("messagesScanned", result.getMessagesScanned());
            resMap.put("matchedPayload", result.getMatchedPayload());
            resMap.put("errorMessage", result.getErrorMessage());

            instance.logBroadcaster.emitLog(parseUuid(executionId), null,
                    String.valueOf(step.get("id")), String.valueOf(step.get("name")),
                    "Kafka Assert complete. Matched: " + result.isMatched() +
                            (result.getErrorMessage() != null ? ", error: " + result.getErrorMessage() : ""));

            return resMap;
        } catch (Exception e) {
            log.error("Error executing Kafka assert: {}", e.getMessage(), e);
            return Map.of("matched", false, "errorMessage", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> prepareSubWorkflow(String executionId, Map<String, Object> step, Map<String, Object> globalContext) {
        if (instance == null) {
            return Map.of("success", false, "errorMessage", "Bridge not initialized", "steps", Collections.emptyList());
        }

        try {
            Object subCfgObj = step.get("subWorkflowConfig");
            SubWorkflowStepConfig config = instance.objectMapper.convertValue(subCfgObj, SubWorkflowStepConfig.class);
            if (config == null || config.getWorkflowId() == null || config.getWorkflowId().isBlank()) {
                return Map.of("success", false, "errorMessage", "No sub-workflow ID configured", "steps", Collections.emptyList());
            }

            UUID targetWfId = UUID.fromString(config.getWorkflowId().trim());

            // Recursion & Circular dependency guard: check call stack in parent context
            String callStackKey = "_subWorkflowCallStack";
            Map<String, Object> parentCtx = activeContexts.getOrDefault(executionId, Collections.emptyMap());
            Set<UUID> callStack = new HashSet<>();
            if (parentCtx.get(callStackKey) instanceof Collection<?> col) {
                for (Object item : col) {
                    if (item instanceof UUID u) callStack.add(u);
                    else if (item != null) {
                        try { callStack.add(UUID.fromString(item.toString().trim())); } catch (Exception ignored) {}
                    }
                }
            }

            // Also include current workflow ID from active payload
            Map<String, Object> activePayload = activePayloads.getOrDefault(executionId, Collections.emptyMap());
            if (activePayload.get("workflowId") != null) {
                try { callStack.add(UUID.fromString(activePayload.get("workflowId").toString().trim())); } catch (Exception ignored) {}
            }

            if (callStack.contains(targetWfId)) {
                String errorMsg = "Circular sub-workflow call detected for workflow ID: " + targetWfId;
                log.error(errorMsg);
                return Map.of("success", false, "errorMessage", errorMsg, "steps", Collections.emptyList());
            }

            if (instance.workflowRepository == null) {
                return Map.of("success", false, "errorMessage", "Workflow repository not available in bridge", "steps", Collections.emptyList());
            }

            Optional<TestWorkflowEntity> subOpt = instance.workflowRepository.findById(targetWfId);
            if (subOpt.isEmpty()) {
                String errorMsg = "Target sub-workflow not found: " + targetWfId;
                log.error(errorMsg);
                return Map.of("success", false, "errorMessage", errorMsg, "steps", Collections.emptyList());
            }

            TestWorkflowEntity subWf = subOpt.get();
            WorkflowAst ast = subWf.getWorkflowAst();
            List<?> subSteps = Collections.emptyList();
            if (ast != null && ast.getSteps() != null) {
                subSteps = instance.objectMapper.convertValue(ast.getSteps(), List.class);
            }

            // Build child execution context
            Map<String, Object> childContext = new LinkedHashMap<>();

            // 1. Inherit parent runtime variables (from previous steps)
            if (config.isInheritParentContext()) {
                if (globalContext != null) {
                    childContext.putAll(globalContext);
                }
                childContext.putAll(parentCtx);
            }

            // 2. Inherit environment properties and secrets
            if (config.isInheritEnvAndSecrets()) {
                if (parentCtx.containsKey("env")) {
                    childContext.put("env", parentCtx.get("env"));
                }
                if (parentCtx.containsKey("secrets")) {
                    childContext.put("secrets", parentCtx.get("secrets"));
                    if (parentCtx.get("secrets") instanceof Map<?, ?> secMap) {
                        for (Map.Entry<?, ?> se : secMap.entrySet()) {
                            if (se.getKey() != null && se.getValue() != null) {
                                childContext.put(se.getKey().toString(), se.getValue());
                            }
                        }
                    }
                }
            }

            // 3. Child workflow's own static variables
            if (ast != null && ast.getVariables() != null) {
                childContext.putAll(ast.getVariables());
            }

            // 4. Resolve and apply explicit input variables passed by parent step
            if (config.getInputVariables() != null && !config.getInputVariables().isEmpty()) {
                Object resolvedInputs = instance.contextEngine.resolveObject(config.getInputVariables(), parentCtx);
                if (resolvedInputs instanceof Map<?, ?> inMap) {
                    for (Map.Entry<?, ?> entry : inMap.entrySet()) {
                        if (entry.getKey() != null) {
                            childContext.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                }
            }

            // 5. Update call stack in child context to prevent child from calling any parent in stack
            Set<UUID> nextCallStack = new HashSet<>(callStack);
            nextCallStack.add(targetWfId);
            childContext.put(callStackKey, nextCallStack);

            // 6. Inherit / compute flow headers
            Map<String, Object> childFlowHeaders = new LinkedHashMap<>();
            if (config.isInheritFlowHeaders()) {
                Object parentHeadersObj = activePayload.get("flowHeaders");
                if (parentHeadersObj instanceof Map<?, ?> parentH) {
                    for (Map.Entry<?, ?> entry : parentH.entrySet()) {
                        if (entry.getKey() != null) {
                            childFlowHeaders.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                }
            }
            if (ast != null && ast.getDefaultHeaders() != null) {
                for (Map.Entry<String, String> e : ast.getDefaultHeaders().entrySet()) {
                    childFlowHeaders.put(e.getKey(), instance.contextEngine.resolveString(e.getValue(), childContext));
                }
            }

            instance.logBroadcaster.emitLog(parseUuid(executionId), null,
                    String.valueOf(step.get("id")), String.valueOf(step.get("name")),
                    "Executing SUB_WORKFLOW step: Calling workflow '" + subWf.getName() + "' [" + targetWfId + "] with " + subSteps.size() + " steps");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("workflowId", targetWfId.toString());
            result.put("workflowName", subWf.getName());
            result.put("steps", subSteps);
            result.put("initialContext", childContext);
            result.put("flowHeaders", childFlowHeaders);
            return result;

        } catch (Exception e) {
            log.error("Failed to prepare sub-workflow: {}", e.getMessage(), e);
            return Map.of("success", false, "errorMessage", e.getMessage(), "steps", Collections.emptyList());
        }
    }

    public static Map<String, Object> finishSubWorkflow(String executionId, Map<String, Object> step, Map<String, Object> subContext) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("outputVariables", subContext != null ? subContext : Collections.emptyMap());

        if (instance != null) {
            instance.logBroadcaster.emitLog(parseUuid(executionId), null,
                    String.valueOf(step.get("id")), String.valueOf(step.get("name")),
                    "Sub-Workflow execution completed. Captured " + (subContext != null ? subContext.size() : 0) + " output context variables");
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractVariables(String executionId, Map<String, Object> step,
                                                        Object responseBody, int httpStatus,
                                                        Map<String, Object> headers) {
        Map<String, Object> extracted = new HashMap<>();
        if (instance == null || step == null) return extracted;

        Object extractionsObj = step.get("extractions");
        if (!(extractionsObj instanceof List)) return extracted;

        List<Map<String, Object>> extractions = (List<Map<String, Object>>) extractionsObj;
        Map<String, Object> context = activeContexts.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
        boolean isDbStep = "DB_POLL".equalsIgnoreCase(String.valueOf(step.get("type")));
        boolean isSubWfStep = "SUB_WORKFLOW".equalsIgnoreCase(String.valueOf(step.get("type")));

        for (Map<String, Object> ext : extractions) {
            String varName = (String) ext.get("variableName");
            String source = (String) ext.get("source");
            String jsonPath = (String) ext.get("jsonPath");
            String defaultValue = (String) ext.get("defaultValue");

            Object value = null;
            if ("RESPONSE_STATUS".equalsIgnoreCase(source)) {
                value = httpStatus;
            } else if ("RESPONSE_HEADER".equalsIgnoreCase(source) && headers != null) {
                value = headers.get(jsonPath);
            } else if ("DB_ROW_COLUMN".equalsIgnoreCase(source) || "DB_COLUMN".equalsIgnoreCase(source)
                    || "SUB_WORKFLOW_VARIABLE".equalsIgnoreCase(source) || "SUB_WORKFLOW".equalsIgnoreCase(source)) {
                value = extractDbOrMapValue(responseBody, jsonPath);
            } else if (responseBody != null) {
                if (isDbStep || isSubWfStep || responseBody instanceof Map) {
                    value = extractDbOrMapValue(responseBody, jsonPath);
                } else {
                    value = instance.contextEngine.evaluateJsonPath(responseBody, jsonPath);
                }
            }

            if (value == null && defaultValue != null) {
                value = defaultValue;
            }

            if (value != null) {
                extracted.put(varName, value);
                context.put(varName, value);
                // Also alias by step: stepId.varName
                String stepId = (String) step.get("id");
                if (stepId != null) {
                    context.put(stepId + "." + varName, value);
                }
                instance.logBroadcaster.emitLog(parseUuid(executionId), null, stepId,
                        String.valueOf(step.get("name")),
                        "Extracted variable: " + varName + " = " + value);
            }
        }

        // Cache the raw step response object in context as stepId.response
        String stepId = (String) step.get("id");
        if (stepId != null) {
            Map<String, Object> stepResp = new HashMap<>();
            stepResp.put("status", httpStatus);
            stepResp.put("body", responseBody);
            stepResp.put("headers", headers);
            context.put(stepId + ".response", stepResp);
            context.put(stepId + ".status", httpStatus);
            context.put(stepId + ".body", responseBody);
        }

        return extracted;
    }

    public static Object extractDbOrMapValue(Object responseBody, String jsonPath) {
        if (responseBody == null || jsonPath == null || jsonPath.isBlank()) {
            return null;
        }

        // 1. Try direct JSONPath evaluation first (preserving Jayway JSONPath behavior)
        if (instance != null && instance.contextEngine != null) {
            try {
                Object directVal = instance.contextEngine.evaluateJsonPath(responseBody, jsonPath);
                if (directVal != null) {
                    return directVal;
                }
            } catch (Exception ignored) {}
        }

        // 2. Strip leading prefixes: $, $., row., latestRow.
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

        // 3. Direct / Case-insensitive / Underscore-insensitive lookup in Map
        if (responseBody instanceof Map<?, ?> map) {
            Object val = findInMapCaseInsensitive(map, cleanPath);
            if (val != null) return val;

            // Check if map contains nested "row" or "latestRow"
            if (map.get("row") instanceof Map<?, ?> rowMap) {
                val = findInMapCaseInsensitive(rowMap, cleanPath);
                if (val != null) return val;
            }
            if (map.get("latestRow") instanceof Map<?, ?> latestMap) {
                val = findInMapCaseInsensitive(latestMap, cleanPath);
                if (val != null) return val;
            }

            // Check if map contains "rows" list
            if (map.get("rows") instanceof List<?> rowsList && !rowsList.isEmpty()) {
                Object firstRow = rowsList.get(0);
                if (firstRow instanceof Map<?, ?> firstRowMap) {
                    val = findInMapCaseInsensitive(firstRowMap, cleanPath);
                    if (val != null) return val;
                }
            }
        }

        // 4. Try JSONPath with "$." prefix if not present
        if (instance != null && instance.contextEngine != null) {
            if (!jsonPath.startsWith("$")) {
                try {
                    Object val = instance.contextEngine.evaluateJsonPath(responseBody, "$." + jsonPath);
                    if (val != null) return val;
                } catch (Exception ignored) {}
            }
            try {
                Object val = instance.contextEngine.evaluateJsonPath(responseBody, "$.row." + cleanPath);
                if (val != null) return val;
            } catch (Exception ignored) {}
        }

        return null;
    }

    public static Object findInMapCaseInsensitive(Map<?, ?> map, String targetKey) {
        if (map == null || targetKey == null || targetKey.isBlank()) return null;

        // A. Direct exact match
        if (map.containsKey(targetKey)) {
            return map.get(targetKey);
        }

        // B. Case-insensitive match (e.g. "status" matches "STATUS")
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toString().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
        }

        // C. Underscore-insensitive & case-insensitive match (e.g. "refId" or "ref_id" matches "REF_ID")
        String normalizedTarget = targetKey.replace("_", "").toLowerCase();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                String normalizedKey = entry.getKey().toString().replace("_", "").toLowerCase();
                if (normalizedKey.equals(normalizedTarget)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> evaluateAssertions(String executionId, Map<String, Object> step,
                                                          int httpStatus, Object responseBody) {
        Map<String, Object> result = new HashMap<>();
        List<String> details = new ArrayList<>();
        boolean allPassed = true;

        if (step.get("assertions") instanceof List) {
            List<Map<String, Object>> assertions = (List<Map<String, Object>>) step.get("assertions");
            for (Map<String, Object> assertion : assertions) {
                String field = (String) assertion.get("field");
                String operator = (String) assertion.get("operator");
                Object expected = assertion.get("expectedValue");

                Object actual = null;
                if ("status".equalsIgnoreCase(field)) {
                    actual = httpStatus;
                } else if (field != null && field.startsWith("body")) {
                    String path = field.startsWith("body.") ? "$." + field.substring(5) : (field.equals("body") ? "$" : field);
                    actual = instance.contextEngine.evaluateJsonPath(responseBody, path);
                }

                boolean pass = checkOperator(actual, operator, expected);
                String detail = String.format("[%s] Field '%s': actual='%s', expected='%s' (%s)",
                        pass ? "PASS" : "FAIL", field, actual, expected, operator);
                details.add(detail);
                if (!pass) {
                    allPassed = false;
                }
            }
        }

        result.put("passed", allPassed);
        result.put("details", details);
        return result;
    }

    private static boolean checkOperator(Object actual, String operator, Object expected) {
        if ("IS_NOT_NULL".equalsIgnoreCase(operator)) {
            return actual != null;
        }
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;

        String actStr = String.valueOf(actual);
        String expStr = String.valueOf(expected);

        return switch (operator != null ? operator.toUpperCase() : "EQUALS") {
            case "EQUALS" -> actStr.equalsIgnoreCase(expStr);
            case "NOT_EQUALS" -> !actStr.equalsIgnoreCase(expStr);
            case "CONTAINS" -> actStr.contains(expStr);
            case "MATCHES_REGEX" -> actStr.matches(expStr);
            case "GREATER_THAN" -> {
                try {
                    yield Double.parseDouble(actStr) > Double.parseDouble(expStr);
                } catch (Exception e) {
                    yield false;
                }
            }
            case "LESS_THAN" -> {
                try {
                    yield Double.parseDouble(actStr) < Double.parseDouble(expStr);
                } catch (Exception e) {
                    yield false;
                }
            }
            default -> actStr.equalsIgnoreCase(expStr);
        };
    }

    public static void onStepFinished(String executionId, Map<String, Object> step,
                                       boolean success, int httpStatus,
                                       Object reqBody, Object respBody,
                                       Map<String, Object> extractedVars,
                                       List<String> assertionDetails,
                                       String errorMessage) {
        onStepFinished(executionId, step, success, httpStatus, reqBody, respBody, extractedVars, assertionDetails, errorMessage, null);
    }

    @SuppressWarnings("unchecked")
    public static void onStepFinished(String executionId, Map<String, Object> step,
                                       boolean success, int httpStatus,
                                       Object reqBody, Object respBody,
                                       Map<String, Object> extractedVars,
                                       List<String> assertionDetails,
                                       String errorMessage,
                                       Map<String, Object> reqHeaders) {
        if (instance == null || step == null) return;
        UUID execId = parseUuid(executionId);
        String stepId = String.valueOf(step.get("id"));
        String stepName = String.valueOf(step.get("name"));
        String stepTypeStr = String.valueOf(step.get("type"));

        Long startTime = stepStartTimes.getOrDefault(executionId + "_" + stepId, System.currentTimeMillis());
        long duration = System.currentTimeMillis() - startTime;

        ExecutionStepResult stepResult = new ExecutionStepResult();
        stepResult.setStepId(stepId);
        stepResult.setStepName(stepName);
        try {
            stepResult.setStepType(StepType.valueOf(stepTypeStr));
        } catch (Exception e) {
            stepResult.setStepType(StepType.HTTP_REQUEST);
        }
        stepResult.setStatus(success ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED);
        stepResult.setDurationMs(duration);
        stepResult.setRequestPayload(reqBody);
        if (reqHeaders != null) {
            stepResult.setRequestHeaders(reqHeaders);
        }
        stepResult.setResponsePayload(respBody);
        stepResult.setHttpStatus(httpStatus > 0 ? httpStatus : null);
        stepResult.setAssertionsPassed(success);
        stepResult.setAssertionDetails(assertionDetails != null ? assertionDetails : Collections.emptyList());
        stepResult.setExtractedVariables(extractedVars != null ? extractedVars : Collections.emptyMap());
        stepResult.setErrorMessage(errorMessage);
        boolean exclude = Boolean.TRUE.equals(step.get("excludeFromReport")) || "true".equalsIgnoreCase(String.valueOf(step.get("excludeFromReport")));
        stepResult.setExcludeFromReport(exclude);

        if (extractedVars != null && !extractedVars.isEmpty()) {
            Map<String, Object> ctx = activeContexts.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
            ctx.putAll(extractedVars);
        }

        List<ExecutionStepResult> list = activeStepResults.computeIfAbsent(executionId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(stepResult);

        instance.logBroadcaster.broadcast(new ExecutionEventDto(
                execId, null, "STEP_FINISHED", stepId, stepName,
                stepResult.getStatus(),
                "Step " + stepName + " " + (success ? "succeeded" : "failed") + " in " + duration + "ms",
                stepResult
        ));
    }

    public static void onWorkflowComplete(String executionId, Map<String, Object> finalContext) {
        if (finalContext != null) {
            Map<String, Object> ctx = activeContexts.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
            ctx.putAll(finalContext);
        }
        if (instance == null) return;
        UUID execId = parseUuid(executionId);
        instance.logBroadcaster.broadcast(new ExecutionEventDto(
                execId, null, "WORKFLOW_FINISHED", null, null,
                ExecutionStatus.COMPLETED, "Workflow execution finished",
                finalContext
        ));
    }

    private static UUID parseUuid(String uuidStr) {
        if (uuidStr == null) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (Exception e) {
            return null;
        }
    }
}
