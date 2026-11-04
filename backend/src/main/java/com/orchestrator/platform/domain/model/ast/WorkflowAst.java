package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkflowAst implements Serializable {
    private List<WorkflowStep> steps = new ArrayList<>();
    private Map<String, Object> variables = new HashMap<>();
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private Map<String, String> dynamicHeaders = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> envProperties = new LinkedHashMap<>();
    private Map<String, String> secrets = new LinkedHashMap<>();
    private Long timeoutMs = 60000L;

    public WorkflowAst() {}

    public List<WorkflowStep> getSteps() { return steps; }
    public void setSteps(List<WorkflowStep> steps) { this.steps = steps; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public Map<String, String> getDefaultHeaders() { return defaultHeaders; }
    public void setDefaultHeaders(Map<String, String> defaultHeaders) { this.defaultHeaders = defaultHeaders; }

    public Map<String, String> getDynamicHeaders() { return dynamicHeaders; }
    public void setDynamicHeaders(Map<String, String> dynamicHeaders) { this.dynamicHeaders = dynamicHeaders; }

    public Map<String, Map<String, Object>> getEnvProperties() { return envProperties; }
    public void setEnvProperties(Map<String, Map<String, Object>> envProperties) { this.envProperties = envProperties; }

    public Map<String, String> getSecrets() { return secrets; }
    public void setSecrets(Map<String, String> secrets) { this.secrets = secrets; }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
}
