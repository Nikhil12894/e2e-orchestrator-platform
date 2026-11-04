package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowAst implements Serializable {
    private List<WorkflowStep> steps = new ArrayList<>();
    private Map<String, Object> variables = new HashMap<>();
    private Long timeoutMs = 60000L;

    public WorkflowAst() {}

    public List<WorkflowStep> getSteps() { return steps; }
    public void setSteps(List<WorkflowStep> steps) { this.steps = steps; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
}
