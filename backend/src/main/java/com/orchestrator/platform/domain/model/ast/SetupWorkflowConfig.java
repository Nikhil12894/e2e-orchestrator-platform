package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SetupWorkflowConfig implements Serializable {
    private String workflowId;
    private String workflowName;
    private boolean enabled = true;
    private boolean inheritEnvAndSecrets = true;
    private List<VariableExtraction> extractions = new ArrayList<>();

    public SetupWorkflowConfig() {}

    public SetupWorkflowConfig(String workflowId, String workflowName) {
        this.workflowId = workflowId;
        this.workflowName = workflowName;
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInheritEnvAndSecrets() {
        return inheritEnvAndSecrets;
    }

    public void setInheritEnvAndSecrets(boolean inheritEnvAndSecrets) {
        this.inheritEnvAndSecrets = inheritEnvAndSecrets;
    }

    public List<VariableExtraction> getExtractions() {
        return extractions;
    }

    public void setExtractions(List<VariableExtraction> extractions) {
        this.extractions = extractions != null ? extractions : new ArrayList<>();
    }
}
