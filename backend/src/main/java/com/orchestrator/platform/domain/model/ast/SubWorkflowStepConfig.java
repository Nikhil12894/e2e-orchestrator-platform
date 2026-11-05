package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class SubWorkflowStepConfig implements Serializable {
    private String workflowId;
    private String workflowName;
    private Map<String, Object> inputVariables = new LinkedHashMap<>();
    private boolean inheritParentContext = true;
    private boolean inheritEnvAndSecrets = true;
    private boolean inheritFlowHeaders = true;

    public SubWorkflowStepConfig() {}

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public Map<String, Object> getInputVariables() {
        return inputVariables;
    }

    public void setInputVariables(Map<String, Object> inputVariables) {
        this.inputVariables = inputVariables != null ? inputVariables : new LinkedHashMap<>();
    }

    public boolean isInheritParentContext() {
        return inheritParentContext;
    }

    public void setInheritParentContext(boolean inheritParentContext) {
        this.inheritParentContext = inheritParentContext;
    }

    public boolean isInheritEnvAndSecrets() {
        return inheritEnvAndSecrets;
    }

    public void setInheritEnvAndSecrets(boolean inheritEnvAndSecrets) {
        this.inheritEnvAndSecrets = inheritEnvAndSecrets;
    }

    public boolean isInheritFlowHeaders() {
        return inheritFlowHeaders;
    }

    public void setInheritFlowHeaders(boolean inheritFlowHeaders) {
        this.inheritFlowHeaders = inheritFlowHeaders;
    }
}
