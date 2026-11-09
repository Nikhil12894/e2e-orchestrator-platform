package com.orchestrator.platform.domain.model.canvas;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.orchestrator.platform.domain.model.ast.SetupWorkflowConfig;

public class CanvasState implements Serializable {
    private List<CanvasNode> nodes = new ArrayList<>();
    private List<CanvasEdge> edges = new ArrayList<>();
    private CanvasViewport viewport = new CanvasViewport();
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private Map<String, String> dynamicHeaders = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> envProperties = new LinkedHashMap<>();
    private Map<String, Map<String, String>> envSecrets = new LinkedHashMap<>();
    private Map<String, String> secrets = new LinkedHashMap<>();
    private Map<String, Object> variables = new HashMap<>();
    private SetupWorkflowConfig setupWorkflow;

    public CanvasState() {}

    public List<CanvasNode> getNodes() { return nodes; }
    public void setNodes(List<CanvasNode> nodes) { this.nodes = nodes; }

    public List<CanvasEdge> getEdges() { return edges; }
    public void setEdges(List<CanvasEdge> edges) { this.edges = edges; }

    public CanvasViewport getViewport() { return viewport; }
    public void setViewport(CanvasViewport viewport) { this.viewport = viewport; }

    public Map<String, String> getDefaultHeaders() { return defaultHeaders; }
    public void setDefaultHeaders(Map<String, String> defaultHeaders) { this.defaultHeaders = defaultHeaders; }

    public Map<String, String> getDynamicHeaders() { return dynamicHeaders; }
    public void setDynamicHeaders(Map<String, String> dynamicHeaders) { this.dynamicHeaders = dynamicHeaders; }

    public Map<String, Map<String, Object>> getEnvProperties() { return envProperties; }
    public void setEnvProperties(Map<String, Map<String, Object>> envProperties) { this.envProperties = envProperties; }

    public Map<String, Map<String, String>> getEnvSecrets() { return envSecrets; }
    public void setEnvSecrets(Map<String, Map<String, String>> envSecrets) { this.envSecrets = envSecrets; }

    public Map<String, String> getSecrets() { return secrets; }
    public void setSecrets(Map<String, String> secrets) { this.secrets = secrets; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public SetupWorkflowConfig getSetupWorkflow() { return setupWorkflow; }
    public void setSetupWorkflow(SetupWorkflowConfig setupWorkflow) { this.setupWorkflow = setupWorkflow; }
}
