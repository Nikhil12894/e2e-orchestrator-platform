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
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.DbPollStepConfig;
import com.orchestrator.platform.domain.model.ast.HttpRequestStepConfig;
import com.orchestrator.platform.domain.model.ast.KafkaAssertStepConfig;
import com.orchestrator.platform.domain.model.ast.StepAssertion;
import com.orchestrator.platform.domain.model.ast.SubWorkflowStepConfig;
import com.orchestrator.platform.domain.model.ast.TransformStepConfig;
import com.orchestrator.platform.domain.model.ast.VariableExtraction;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasEdge;
import com.orchestrator.platform.domain.model.canvas.CanvasNode;
import com.orchestrator.platform.domain.model.canvas.CanvasPosition;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.DbConnectionTestRequest;
import com.orchestrator.platform.domain.model.dto.DbConnectionTestResult;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;

import jakarta.annotation.PostConstruct;

@Service
public class TestWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(TestWorkflowService.class);

    private final TestWorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final DynamicKarateRunner karateRunner;
    private final ObjectMapper objectMapper;
    private final HikariConnectionPoolManager connectionPoolManager;
    private final WorkflowContextEngine contextEngine;

    public TestWorkflowService(TestWorkflowRepository workflowRepository,
                               WorkflowExecutionRepository executionRepository,
                               DynamicKarateRunner karateRunner,
                               ObjectMapper objectMapper,
                               HikariConnectionPoolManager connectionPoolManager,
                               WorkflowContextEngine contextEngine) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.karateRunner = karateRunner;
        this.objectMapper = objectMapper;
        this.connectionPoolManager = connectionPoolManager;
        this.contextEngine = contextEngine;
    }

    public DbConnectionTestResult testDbConnection(DbConnectionTestRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        if (request.getEnvProperties() != null) {
            ctx.putAll(request.getEnvProperties());
        }
        if (request.getEnvSecrets() != null) {
            ctx.putAll(request.getEnvSecrets());
        }
        if (request.getSecrets() != null) {
            ctx.putAll(request.getSecrets());
        }

        String url = request.getJdbcUrl();
        if (url != null && url.contains("${")) {
            url = contextEngine.resolveString(url, ctx);
        }

        String username = request.getUsername();
        if (username != null && username.contains("${")) {
            username = contextEngine.resolveString(username, ctx);
        }

        String password = request.getPassword();
        if (password != null && password.contains("${")) {
            password = contextEngine.resolveString(password, ctx);
        }

        String query = request.getQuery();
        if (query != null && query.contains("${")) {
            query = contextEngine.resolveString(query, ctx);
        }

        return connectionPoolManager.testConnection(url, username, password, request.getDriverClassName(), query);
    }

    @Transactional(readOnly = true)
    public List<TestWorkflowEntity> getAllWorkflows() {
        return workflowRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<TestWorkflowEntity> getWorkflowById(UUID id) {
        return workflowRepository.findById(id);
    }

    @Transactional
    public TestWorkflowEntity createWorkflow(WorkflowCreateRequest request) {
        TestWorkflowEntity entity = new TestWorkflowEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setCanvasState(request.getCanvasState() != null ? request.getCanvasState() : new CanvasState());

        // Derive AST from canvas state if not explicitly supplied
        if (request.getWorkflowAst() != null && request.getWorkflowAst().getSteps() != null && !request.getWorkflowAst().getSteps().isEmpty()) {
            WorkflowAst ast = request.getWorkflowAst();
            if (request.getCanvasState() != null) {
                if (ast.getDefaultHeaders().isEmpty() && !request.getCanvasState().getDefaultHeaders().isEmpty()) {
                    ast.setDefaultHeaders(new LinkedHashMap<>(request.getCanvasState().getDefaultHeaders()));
                }
                if (ast.getDynamicHeaders().isEmpty() && !request.getCanvasState().getDynamicHeaders().isEmpty()) {
                    ast.setDynamicHeaders(new LinkedHashMap<>(request.getCanvasState().getDynamicHeaders()));
                }
                if (ast.getEnvProperties().isEmpty() && !request.getCanvasState().getEnvProperties().isEmpty()) {
                    ast.setEnvProperties(new LinkedHashMap<>(request.getCanvasState().getEnvProperties()));
                }
                if (ast.getEnvSecrets().isEmpty() && !request.getCanvasState().getEnvSecrets().isEmpty()) {
                    ast.setEnvSecrets(new LinkedHashMap<>(request.getCanvasState().getEnvSecrets()));
                }
                if (ast.getSecrets().isEmpty() && !request.getCanvasState().getSecrets().isEmpty()) {
                    ast.setSecrets(new LinkedHashMap<>(request.getCanvasState().getSecrets()));
                }
            }
            entity.setWorkflowAst(ast);
        } else if (request.getCanvasState() != null) {
            entity.setWorkflowAst(deriveAstFromCanvas(request.getCanvasState()));
        } else {
            entity.setWorkflowAst(new WorkflowAst());
        }

        return workflowRepository.save(entity);
    }

    @Transactional
    public TestWorkflowEntity updateWorkflow(UUID id, WorkflowCreateRequest request) {
        TestWorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + id));

        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getCanvasState() != null) {
            entity.setCanvasState(request.getCanvasState());
            // Sync AST from Canvas State
            entity.setWorkflowAst(deriveAstFromCanvas(request.getCanvasState()));
        }
        if (request.getWorkflowAst() != null && request.getWorkflowAst().getSteps() != null && !request.getWorkflowAst().getSteps().isEmpty()) {
            WorkflowAst ast = request.getWorkflowAst();
            if (request.getCanvasState() != null) {
                if (ast.getDefaultHeaders().isEmpty() && !request.getCanvasState().getDefaultHeaders().isEmpty()) {
                    ast.setDefaultHeaders(new LinkedHashMap<>(request.getCanvasState().getDefaultHeaders()));
                }
                if (ast.getDynamicHeaders().isEmpty() && !request.getCanvasState().getDynamicHeaders().isEmpty()) {
                    ast.setDynamicHeaders(new LinkedHashMap<>(request.getCanvasState().getDynamicHeaders()));
                }
                if (ast.getEnvProperties().isEmpty() && !request.getCanvasState().getEnvProperties().isEmpty()) {
                    ast.setEnvProperties(new LinkedHashMap<>(request.getCanvasState().getEnvProperties()));
                }
                if (ast.getEnvSecrets().isEmpty() && !request.getCanvasState().getEnvSecrets().isEmpty()) {
                    ast.setEnvSecrets(new LinkedHashMap<>(request.getCanvasState().getEnvSecrets()));
                }
                if (ast.getSecrets().isEmpty() && !request.getCanvasState().getSecrets().isEmpty()) {
                    ast.setSecrets(new LinkedHashMap<>(request.getCanvasState().getSecrets()));
                }
            }
            entity.setWorkflowAst(ast);
        }
        entity.setVersion(entity.getVersion() + 1);

        return workflowRepository.save(entity);
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        List<WorkflowExecutionEntity> executions = executionRepository.findByWorkflowIdOrderByCreatedAtDesc(id);
        for (WorkflowExecutionEntity ex : executions) {
            karateRunner.deleteExecutionFiles(ex.getId());
        }
        executionRepository.deleteByWorkflowId(id);
        workflowRepository.deleteById(id);
        log.info("Deleted workflow {} and its associated executions ({} runs purged from DB and disk)", id, executions.size());
    }

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
        boolean hasDbSteps = steps.stream()
                .anyMatch(s -> s.getType() == StepType.DB_POLL && s.isEnabled());

        if (hasDbSteps) {
            sb.append("    # Reusable Standalone JDBC Helper Functions\n");
            sb.append("    * def dbQuery =\n");
            sb.append("      \"\"\"\n");
            sb.append("      function(cfg) {\n");
            sb.append("        var DriverManager = Java.type('java.sql.DriverManager');\n");
            sb.append("        if (cfg.driverClassName) {\n");
            sb.append("          try {\n");
            sb.append("            Java.type('java.lang.Class').forName(cfg.driverClassName);\n");
            sb.append("          } catch(e) {}\n");
            sb.append("        }\n");
            sb.append("        var resolveToken = function(str) {\n");
            sb.append("          if (!str || str.indexOf('${') === -1) return str;\n");
            sb.append("          return str.replace(/\\$\\{([^}]+)\\}/g, function(match, key) {\n");
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
            sb.append("        var conn = null;\n");
            sb.append("        var stmt = null;\n");
            sb.append("        var rs = null;\n");
            sb.append("        var rows = [];\n");
            sb.append("        try {\n");
            sb.append("          if (user) {\n");
            sb.append("            conn = DriverManager.getConnection(url, user, pass);\n");
            sb.append("          } else {\n");
            sb.append("            conn = DriverManager.getConnection(url);\n");
            sb.append("          }\n");
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
            sb.append("      \"\"\"\n");
        }
        sb.append("\n");

        int stepIdx = 1;
        for (WorkflowStep step : steps) {
            if (!step.isEnabled()) {
                sb.append("  # [MUTED / DISABLED] Scenario: ").append(step.getName()).append("\n\n");
                continue;
            }
            sb.append("  Scenario: Step ").append(stepIdx++).append(" - ").append(step.getName()).append("\n");

            if (step.getType() == StepType.HTTP_REQUEST && step.getHttpConfig() != null) {
                HttpRequestStepConfig cfg = step.getHttpConfig();
                if (cfg.getRetryCount() > 1) {
                    sb.append("    * configure retry = { count: ").append(cfg.getRetryCount())
                            .append(", interval: ").append(cfg.getRetryIntervalMs()).append(" }\n");
                    sb.append("    * retry until responseStatus == ")
                            .append(cfg.getExpectedStatus() != null ? cfg.getExpectedStatus() : 200).append("\n");
                }
                sb.append("    Given url '").append(cfg.getUrl() != null ? cfg.getUrl() : "").append("'\n");
                if (cfg.getHeaders() != null && !cfg.getHeaders().isEmpty()) {
                    for (Map.Entry<String, String> h : cfg.getHeaders().entrySet()) {
                        sb.append("    And header ").append(h.getKey()).append(" = '").append(h.getValue()).append("'\n");
                    }
                }
                if (cfg.getBody() != null) {
                    try {
                        String bodyJson = objectMapper.writeValueAsString(cfg.getBody());
                        sb.append("    And request ").append(bodyJson).append("\n");
                    } catch (Exception e) {
                        sb.append("    And request '").append(cfg.getBody()).append("'\n");
                    }
                }
                sb.append("    When method ").append(cfg.getMethod() != null ? cfg.getMethod().toLowerCase() : "get").append("\n");
                sb.append("    Then status ").append(cfg.getExpectedStatus() != null ? cfg.getExpectedStatus() : 200).append("\n");

                if (step.getExtractions() != null) {
                    for (VariableExtraction ext : step.getExtractions()) {
                        if ("RESPONSE_BODY".equalsIgnoreCase(ext.getSource())) {
                            sb.append("    * def ").append(ext.getVariableName()).append(" = karate.jsonPath(response, '").append(ext.getJsonPath()).append("')\n");
                        }
                    }
                }
            } else if (step.getType() == StepType.DELAY) {
                sb.append("    * karate.pause(").append(step.getDelayMs() != null ? step.getDelayMs() : 1000).append(")\n");
            } else if (step.getType() == StepType.TRANSFORM && step.getTransformConfig() != null) {
                sb.append("    * def transformFn = ").append(step.getTransformConfig().getScript()).append("\n");
                sb.append("    * def transformResult = transformFn(karate.get('context', {}))\n");
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
                sb.append("    * def dbCondition_").append(safeStepId).append(" = function(rows) { return rows.length > 0; }\n");

                int maxAttempts = dbConfig.getMaxAttempts() != null && dbConfig.getMaxAttempts() > 0 ? dbConfig.getMaxAttempts() : 5;
                long intervalMs = dbConfig.getPollIntervalMs() != null ? dbConfig.getPollIntervalMs() : 1000L;
                long timeoutMs = dbConfig.getTimeoutMs() != null ? dbConfig.getTimeoutMs() : 15000L;

                sb.append("    * def dbResult = dbPoll(dbConfig_").append(safeStepId).append(", ")
                        .append(maxAttempts).append(", ").append(intervalMs).append(", ")
                        .append(timeoutMs).append(", dbCondition_").append(safeStepId).append(")\n");
                sb.append("    * def pollResult = dbResult\n");
                sb.append("    * def result = dbResult\n");
                sb.append("    * match dbResult.matched == true\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Transactional
    public TestWorkflowEntity importWorkflow(WorkflowCreateRequest request) {
        String originalName = request.getName() != null ? request.getName() : "Imported Workflow";
        if (!originalName.contains("(Imported)")) {
            request.setName(originalName + " (Imported)");
        }
        return createWorkflow(request);
    }

    /**
     * Converts visual React Flow nodes and edges into an executable WorkflowAst in topological sequence.
     */
    public WorkflowAst deriveAstFromCanvas(CanvasState canvasState) {
        WorkflowAst ast = new WorkflowAst();
        if (canvasState == null || canvasState.getNodes() == null || canvasState.getNodes().isEmpty()) {
            return ast;
        }

        List<CanvasNode> nodes = canvasState.getNodes();
        List<CanvasEdge> edges = canvasState.getEdges() != null ? canvasState.getEdges() : Collections.emptyList();

        // Build adjacency and in-degree maps
        Map<String, CanvasNode> nodeMap = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (CanvasNode node : nodes) {
            nodeMap.put(node.getId(), node);
            adj.put(node.getId(), new ArrayList<>());
            inDegree.put(node.getId(), 0);
        }

        for (CanvasEdge edge : edges) {
            if (nodeMap.containsKey(edge.getSource()) && nodeMap.containsKey(edge.getTarget())) {
                adj.get(edge.getSource()).add(edge.getTarget());
                inDegree.put(edge.getTarget(), inDegree.get(edge.getTarget()) + 1);
            }
        }

        // Topological Sort (Kahn's Algorithm)
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sortedNodeIds = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            sortedNodeIds.add(curr);

            for (String neighbor : adj.getOrDefault(curr, Collections.emptyList())) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // If any disconnected nodes remain, sort them by vertical Y position and append
        for (CanvasNode node : nodes) {
            if (!sortedNodeIds.contains(node.getId())) {
                sortedNodeIds.add(node.getId());
            }
        }

        // Map sorted CanvasNodes into executable WorkflowSteps
        List<WorkflowStep> steps = new ArrayList<>();
        for (String nodeId : sortedNodeIds) {
            CanvasNode node = nodeMap.get(nodeId);
            if (node != null) {
                steps.add(convertNodeToStep(node));
            }
        }

        ast.setSteps(steps);
        if (canvasState != null) {
            if (canvasState.getDefaultHeaders() != null) ast.setDefaultHeaders(new LinkedHashMap<>(canvasState.getDefaultHeaders()));
            if (canvasState.getDynamicHeaders() != null) ast.setDynamicHeaders(new LinkedHashMap<>(canvasState.getDynamicHeaders()));
            if (canvasState.getEnvProperties() != null) ast.setEnvProperties(new LinkedHashMap<>(canvasState.getEnvProperties()));
            if (canvasState.getEnvSecrets() != null) ast.setEnvSecrets(new LinkedHashMap<>(canvasState.getEnvSecrets()));
            if (canvasState.getSecrets() != null) ast.setSecrets(new LinkedHashMap<>(canvasState.getSecrets()));
            if (canvasState.getVariables() != null) ast.setVariables(new HashMap<>(canvasState.getVariables()));
        }
        return ast;
    }

    @SuppressWarnings("unchecked")
    private WorkflowStep convertNodeToStep(CanvasNode node) {
        WorkflowStep step = new WorkflowStep();
        step.setId(node.getId());

        Map<String, Object> data = node.getData() != null ? node.getData() : Collections.emptyMap();
        step.setName((String) data.getOrDefault("name", node.getId()));
        step.setContinueOnFailure(Boolean.TRUE.equals(data.get("continueOnFailure")) || "true".equalsIgnoreCase(String.valueOf(data.get("continueOnFailure"))));
        step.setExcludeFromReport(Boolean.TRUE.equals(data.get("excludeFromReport")) || "true".equalsIgnoreCase(String.valueOf(data.get("excludeFromReport"))));
        if (data.get("enabled") != null) {
            step.setEnabled(Boolean.TRUE.equals(data.get("enabled")) || !"false".equalsIgnoreCase(String.valueOf(data.get("enabled"))));
        }

        String nodeType = node.getType() != null ? node.getType().toLowerCase() : "httprequest";

        if (nodeType.contains("http")) {
            step.setType(StepType.HTTP_REQUEST);
            HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
            httpConfig.setUrl((String) data.getOrDefault("url", "http://localhost:8080/api/v1/mock/orders"));
            httpConfig.setMethod((String) data.getOrDefault("method", "GET"));

            if (data.get("headers") instanceof Map) {
                httpConfig.setHeaders((Map<String, String>) data.get("headers"));
            }
            if (data.get("inheritGlobalHeaders") != null) {
                httpConfig.setInheritGlobalHeaders(Boolean.TRUE.equals(data.get("inheritGlobalHeaders"))
                        || !"false".equalsIgnoreCase(String.valueOf(data.get("inheritGlobalHeaders"))));
            }
            if (data.get("excludedGlobalHeaders") instanceof List<?> list) {
                List<String> excluded = list.stream().filter(Objects::nonNull).map(Object::toString).toList();
                httpConfig.setExcludedGlobalHeaders(new ArrayList<>(excluded));
            }
            if (data.get("params") instanceof Map) {
                httpConfig.setParams((Map<String, String>) data.get("params"));
            }
            if (data.get("body") != null) {
                httpConfig.setBody(data.get("body"));
            }
            if (data.get("expectedStatus") != null) {
                httpConfig.setExpectedStatus(Integer.parseInt(String.valueOf(data.get("expectedStatus"))));
            }
            if (data.get("retryCount") != null) {
                try {
                    httpConfig.setRetryCount(Integer.parseInt(String.valueOf(data.get("retryCount"))));
                } catch (Exception ignored) {}
            }
            if (data.get("retryIntervalMs") != null) {
                try {
                    httpConfig.setRetryIntervalMs(Integer.parseInt(String.valueOf(data.get("retryIntervalMs"))));
                } catch (Exception ignored) {}
            }
            step.setHttpConfig(httpConfig);

        } else if (nodeType.contains("db") || nodeType.contains("sql")) {
            step.setType(StepType.DB_POLL);
            DbPollStepConfig dbConfig = new DbPollStepConfig();
            dbConfig.setJdbcUrl((String) data.getOrDefault("jdbcUrl", "jdbc:postgresql://localhost:5432/e2e_orchestrator"));
            dbConfig.setUsername((String) data.getOrDefault("username", "postgres"));
            dbConfig.setPassword((String) data.getOrDefault("password", "postgres"));
            if (data.get("driverClassName") != null && !String.valueOf(data.get("driverClassName")).isBlank()) {
                dbConfig.setDriverClassName(String.valueOf(data.get("driverClassName")).trim());
            }
            dbConfig.setQuery((String) data.getOrDefault("query", "SELECT status FROM orders WHERE id = '${orderId}'"));
            dbConfig.setCondition((String) data.getOrDefault("condition", "row.status == 'COMPLETED'"));
            if (data.get("timeoutMs") != null) {
                try {
                    dbConfig.setTimeoutMs(Long.parseLong(String.valueOf(data.get("timeoutMs"))));
                } catch (Exception ignored) {}
            }
            if (data.get("pollIntervalMs") != null) {
                try {
                    dbConfig.setPollIntervalMs(Long.parseLong(String.valueOf(data.get("pollIntervalMs"))));
                } catch (Exception ignored) {}
            }
            if (data.get("maxAttempts") != null) {
                try {
                    dbConfig.setMaxAttempts(Integer.parseInt(String.valueOf(data.get("maxAttempts"))));
                } catch (Exception ignored) {}
            } else if (data.get("retryCount") != null) {
                try {
                    dbConfig.setMaxAttempts(Integer.parseInt(String.valueOf(data.get("retryCount"))));
                } catch (Exception ignored) {}
            }
            step.setDbPollConfig(dbConfig);

        } else if (nodeType.contains("kafka")) {
            step.setType(StepType.KAFKA_ASSERT);
            KafkaAssertStepConfig kafkaConfig = new KafkaAssertStepConfig();
            kafkaConfig.setBootstrapServers((String) data.getOrDefault("bootstrapServers", "localhost:9092"));
            kafkaConfig.setTopic((String) data.getOrDefault("topic", "order-events"));
            kafkaConfig.setJsonPath((String) data.getOrDefault("jsonPath", "$.status"));
            kafkaConfig.setExpectedValue((String) data.getOrDefault("expectedValue", "CONFIRMED"));
            step.setKafkaConfig(kafkaConfig);

        } else if (nodeType.contains("delay")) {
            step.setType(StepType.DELAY);
            long delay = data.get("delayMs") != null ? Long.parseLong(String.valueOf(data.get("delayMs"))) : 2000L;
            step.setDelayMs(delay);

        } else if (nodeType.contains("transform") || nodeType.contains("script")) {
            step.setType(StepType.TRANSFORM);
            TransformStepConfig transConfig = new TransformStepConfig();
            String script = (String) data.getOrDefault("script", "function(ctx) { return { timestamp: new Date().toISOString() }; }");
            transConfig.setScript(script);
            step.setTransformConfig(transConfig);

        } else if (nodeType.toLowerCase().contains("subworkflow") || nodeType.toLowerCase().contains("sub_workflow")) {
            step.setType(StepType.SUB_WORKFLOW);
            SubWorkflowStepConfig subConfig = new SubWorkflowStepConfig();
            subConfig.setWorkflowId((String) data.get("workflowId"));
            subConfig.setWorkflowName((String) data.get("workflowName"));
            if (data.get("inputVariables") instanceof Map<?, ?> inVars) {
                subConfig.setInputVariables((Map<String, Object>) inVars);
            }
            if (data.containsKey("inheritParentContext")) {
                subConfig.setInheritParentContext(!"false".equalsIgnoreCase(String.valueOf(data.get("inheritParentContext"))));
            }
            if (data.containsKey("inheritEnvAndSecrets")) {
                subConfig.setInheritEnvAndSecrets(!"false".equalsIgnoreCase(String.valueOf(data.get("inheritEnvAndSecrets"))));
            }
            if (data.containsKey("inheritFlowHeaders")) {
                subConfig.setInheritFlowHeaders(!"false".equalsIgnoreCase(String.valueOf(data.get("inheritFlowHeaders"))));
            }
            step.setSubWorkflowConfig(subConfig);
        }

        // Extractions
        if (data.get("extractions") instanceof List) {
            List<Map<String, Object>> exts = (List<Map<String, Object>>) data.get("extractions");
            for (Map<String, Object> ex : exts) {
                step.getExtractions().add(new VariableExtraction(
                        (String) ex.get("variableName"),
                        (String) ex.get("source"),
                        (String) ex.get("jsonPath"),
                        (String) ex.get("defaultValue")
                ));
            }
        }

        // Assertions
        if (data.get("assertions") instanceof List) {
            List<Map<String, Object>> ass = (List<Map<String, Object>>) data.get("assertions");
            for (Map<String, Object> as : ass) {
                step.getAssertions().add(new StepAssertion(
                        (String) as.get("field"),
                        (String) as.get("operator"),
                        as.get("expectedValue")
                ));
            }
        }

        return step;
    }

    @PostConstruct
    public void seedInitialDemoWorkflows() {
        try {
            if (workflowRepository.count() == 0) {
                log.info("Seeding default demo test workflow...");
                seedDemoOrderProcessingWorkflow();
            }
            seedDedicatedDbPollWorkflowIfMissing();
        } catch (Exception e) {
            log.warn("Could not seed initial workflow: {}", e.getMessage());
        }
    }

    private void seedDedicatedDbPollWorkflowIfMissing() {
        try {
            boolean exists = workflowRepository.findAll().stream()
                    .anyMatch(w -> "Database Polling & Variable Chaining Demo".equalsIgnoreCase(w.getName()));
            if (!exists) {
                log.info("Seeding dedicated Database Polling & Variable Chaining Demo workflow...");
                seedDedicatedDbPollWorkflow();
            }
        } catch (Exception e) {
            log.warn("Could not seed dedicated DB poll workflow: {}", e.getMessage());
        }
    }

    private void seedDedicatedDbPollWorkflow() {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Database Polling & Variable Chaining Demo");
        req.setDescription("Demonstrates polling an in-memory SQL database, extracting multiple row columns, and consuming them downstream.");

        CanvasState state = new CanvasState();
        List<CanvasNode> nodes = new ArrayList<>();

        // Node 1: DB SQL Poll Step
        CanvasNode node1 = new CanvasNode();
        node1.setId("step_db_poll");
        node1.setType("dbPoll");
        node1.setPosition(new CanvasPosition(150, 100));
        Map<String, Object> data1 = new HashMap<>();
        data1.put("name", "Query Database & Extract Variables");
        data1.put("jdbcUrl", "jdbc:h2:mem:demo_orders;DB_CLOSE_DELAY=-1");
        data1.put("username", "sa");
        data1.put("password", "");
        data1.put("driverClassName", "org.h2.Driver");
        data1.put("query", "SELECT 101 AS order_id, 'READY_FOR_SHIPMENT' AS order_status, 'CARRIER_FEDEX' AS carrier, 49.99 AS total_amount");
        data1.put("condition", "row.order_status == 'READY_FOR_SHIPMENT'");
        data1.put("timeoutMs", 5000L);
        data1.put("pollIntervalMs", 500L);
        data1.put("maxAttempts", 3);
        data1.put("extractions", List.of(
                Map.of("variableName", "extractedOrderId", "source", "DB_ROW_COLUMN", "jsonPath", "order_id"),
                Map.of("variableName", "extractedStatus", "source", "DB_ROW_COLUMN", "jsonPath", "ORDER_STATUS"),
                Map.of("variableName", "carrierCode", "source", "DB_ROW_COLUMN", "jsonPath", "carrier")
        ));
        node1.setData(data1);
        nodes.add(node1);

        // Node 2: HTTP Verification Step consuming extracted DB variables
        CanvasNode node2 = new CanvasNode();
        node2.setId("step_verify_order");
        node2.setType("httpRequest");
        node2.setPosition(new CanvasPosition(150, 300));
        Map<String, Object> data2 = new HashMap<>();
        data2.put("name", "Verify Order API using DB Variables");
        data2.put("method", "GET");
        data2.put("url", "http://localhost:8080/api/v1/mock/orders/${extractedOrderId}");
        data2.put("headers", Map.of("X-Carrier-Code", "${carrierCode}", "X-DB-Status", "${extractedStatus}"));
        data2.put("expectedStatus", 200);
        data2.put("assertions", List.of(
                Map.of("field", "status", "operator", "EQUALS", "expectedValue", "200")
        ));
        node2.setData(data2);
        nodes.add(node2);

        state.setNodes(nodes);

        List<CanvasEdge> edges = new ArrayList<>();
        edges.add(createEdge("e_db_1", "step_db_poll", "step_verify_order"));
        state.setEdges(edges);

        req.setCanvasState(state);
        createWorkflow(req);
        log.info("Dedicated DB polling demo workflow seeded successfully");
    }

    private void seedDemoOrderProcessingWorkflow() {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("E-Commerce Order Lifecycle & DB Polling E2E");
        req.setDescription("Complete end-to-end test flow: Authenticate, Create Order, DB Poll verification, and Status Query.");

        CanvasState state = new CanvasState();
        List<CanvasNode> nodes = new ArrayList<>();

        // Node 1: Login
        CanvasNode node1 = new CanvasNode();
        node1.setId("step_auth");
        node1.setType("httpRequest");
        node1.setPosition(new CanvasPosition(150, 100));
        Map<String, Object> data1 = new HashMap<>();
        data1.put("name", "User Authentication");
        data1.put("method", "POST");
        data1.put("url", "http://localhost:8080/api/v1/mock/auth/login");
        data1.put("body", Map.of("username", "qa_tester", "password", "Secret123!"));
        data1.put("expectedStatus", 200);
        data1.put("extractions", List.of(
                Map.of("variableName", "authToken", "source", "RESPONSE_BODY", "jsonPath", "$.token"),
                Map.of("variableName", "userId", "source", "RESPONSE_BODY", "jsonPath", "$.userId")
        ));
        node1.setData(data1);
        nodes.add(node1);

        // Node 2: Create Order
        CanvasNode node2 = new CanvasNode();
        node2.setId("step_create_order");
        node2.setType("httpRequest");
        node2.setPosition(new CanvasPosition(150, 260));
        Map<String, Object> data2 = new HashMap<>();
        data2.put("name", "Submit New Order");
        data2.put("method", "POST");
        data2.put("url", "http://localhost:8080/api/v1/mock/orders");
        data2.put("headers", Map.of("Authorization", "Bearer ${authToken}", "Content-Type", "application/json"));
        data2.put("body", Map.of(
                "userId", "${userId}",
                "productId", "PROD-9988",
                "quantity", 2,
                "totalPrice", 199.98
        ));
        data2.put("expectedStatus", 201);
        data2.put("extractions", List.of(
                Map.of("variableName", "orderId", "source", "RESPONSE_BODY", "jsonPath", "$.orderId")
        ));
        node2.setData(data2);
        nodes.add(node2);

        // Node 3: DB SQL Poll Step
        CanvasNode node3 = new CanvasNode();
        node3.setId("step_db_poll");
        node3.setType("dbPoll");
        node3.setPosition(new CanvasPosition(150, 420));
        Map<String, Object> data3 = new HashMap<>();
        data3.put("name", "Poll Order DB & Extract Details");
        data3.put("jdbcUrl", "jdbc:h2:mem:demo_orders;DB_CLOSE_DELAY=-1");
        data3.put("username", "sa");
        data3.put("password", "");
        data3.put("driverClassName", "org.h2.Driver");
        data3.put("query", "SELECT '${orderId}' AS order_id, 'CONFIRMED' AS status, 'TRK-987654' AS tracking_code");
        data3.put("condition", "row.status == 'CONFIRMED'");
        data3.put("timeoutMs", 5000L);
        data3.put("pollIntervalMs", 500L);
        data3.put("maxAttempts", 3);
        data3.put("extractions", List.of(
                Map.of("variableName", "dbOrderStatus", "source", "DB_ROW_COLUMN", "jsonPath", "STATUS"),
                Map.of("variableName", "trackingCode", "source", "DB_ROW_COLUMN", "jsonPath", "tracking_code")
        ));
        node3.setData(data3);
        nodes.add(node3);

        // Node 4: Query Order Details
        CanvasNode node4 = new CanvasNode();
        node4.setId("step_get_order");
        node4.setType("httpRequest");
        node4.setPosition(new CanvasPosition(150, 580));
        Map<String, Object> data4 = new HashMap<>();
        data4.put("name", "Verify Order Details");
        data4.put("method", "GET");
        data4.put("url", "http://localhost:8080/api/v1/mock/orders/${orderId}");
        data4.put("headers", Map.of("Authorization", "Bearer ${authToken}", "X-Tracking-Code", "${trackingCode}"));
        data4.put("expectedStatus", 200);
        data4.put("assertions", List.of(
                Map.of("field", "status", "operator", "EQUALS", "expectedValue", "200"),
                Map.of("field", "body.status", "operator", "EQUALS", "expectedValue", "${dbOrderStatus}")
        ));
        node4.setData(data4);
        nodes.add(node4);

        state.setNodes(nodes);

        // Edges
        List<CanvasEdge> edges = new ArrayList<>();
        edges.add(createEdge("e1", "step_auth", "step_create_order"));
        edges.add(createEdge("e2", "step_create_order", "step_db_poll"));
        edges.add(createEdge("e3", "step_db_poll", "step_get_order"));
        state.setEdges(edges);

        req.setCanvasState(state);
        createWorkflow(req);
        log.info("Default test workflow seeded successfully");
    }

    private CanvasEdge createEdge(String id, String source, String target) {
        CanvasEdge edge = new CanvasEdge();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        edge.setAnimated(true);
        return edge;
    }
}
