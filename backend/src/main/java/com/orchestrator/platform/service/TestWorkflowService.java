package com.orchestrator.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.*;
import com.orchestrator.platform.domain.model.canvas.CanvasEdge;
import com.orchestrator.platform.domain.model.canvas.CanvasNode;
import com.orchestrator.platform.domain.model.canvas.CanvasPosition;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TestWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(TestWorkflowService.class);

    private final TestWorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    public TestWorkflowService(TestWorkflowRepository workflowRepository, ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.objectMapper = objectMapper;
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
            entity.setWorkflowAst(request.getWorkflowAst());
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
            entity.setWorkflowAst(request.getWorkflowAst());
        }
        entity.setVersion(entity.getVersion() + 1);

        return workflowRepository.save(entity);
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        workflowRepository.deleteById(id);
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
        return ast;
    }

    @SuppressWarnings("unchecked")
    private WorkflowStep convertNodeToStep(CanvasNode node) {
        WorkflowStep step = new WorkflowStep();
        step.setId(node.getId());

        Map<String, Object> data = node.getData() != null ? node.getData() : Collections.emptyMap();
        step.setName((String) data.getOrDefault("name", node.getId()));

        String nodeType = node.getType() != null ? node.getType().toLowerCase() : "httprequest";

        if (nodeType.contains("http")) {
            step.setType(StepType.HTTP_REQUEST);
            HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
            httpConfig.setUrl((String) data.getOrDefault("url", "http://localhost:8080/api/v1/mock/orders"));
            httpConfig.setMethod((String) data.getOrDefault("method", "GET"));

            if (data.get("headers") instanceof Map) {
                httpConfig.setHeaders((Map<String, String>) data.get("headers"));
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
            step.setHttpConfig(httpConfig);

        } else if (nodeType.contains("db") || nodeType.contains("sql")) {
            step.setType(StepType.DB_POLL);
            DbPollStepConfig dbConfig = new DbPollStepConfig();
            dbConfig.setJdbcUrl((String) data.getOrDefault("jdbcUrl", "jdbc:postgresql://localhost:5432/e2e_orchestrator"));
            dbConfig.setUsername((String) data.getOrDefault("username", "postgres"));
            dbConfig.setPassword((String) data.getOrDefault("password", "postgres"));
            dbConfig.setQuery((String) data.getOrDefault("query", "SELECT status FROM orders WHERE id = '${orderId}'"));
            dbConfig.setCondition((String) data.getOrDefault("condition", "row.status == 'COMPLETED'"));
            if (data.get("timeoutMs") != null) {
                dbConfig.setTimeoutMs(Long.parseLong(String.valueOf(data.get("timeoutMs"))));
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
        } catch (Exception e) {
            log.warn("Could not seed initial workflow: {}", e.getMessage());
        }
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

        // Node 3: Delay
        CanvasNode node3 = new CanvasNode();
        node3.setId("step_delay");
        node3.setType("delay");
        node3.setPosition(new CanvasPosition(150, 420));
        Map<String, Object> data3 = new HashMap<>();
        data3.put("name", "Wait for Async Processing");
        data3.put("delayMs", 1500L);
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
        data4.put("headers", Map.of("Authorization", "Bearer ${authToken}"));
        data4.put("expectedStatus", 200);
        data4.put("assertions", List.of(
                Map.of("field", "status", "operator", "EQUALS", "expectedValue", "200"),
                Map.of("field", "body.status", "operator", "EQUALS", "expectedValue", "CONFIRMED")
        ));
        node4.setData(data4);
        nodes.add(node4);

        state.setNodes(nodes);

        // Edges
        List<CanvasEdge> edges = new ArrayList<>();
        edges.add(createEdge("e1", "step_auth", "step_create_order"));
        edges.add(createEdge("e2", "step_create_order", "step_delay"));
        edges.add(createEdge("e3", "step_delay", "step_get_order"));
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
