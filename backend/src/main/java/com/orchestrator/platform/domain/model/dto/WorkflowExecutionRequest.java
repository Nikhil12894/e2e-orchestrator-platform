package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.enums.TriggerType;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class WorkflowExecutionRequest implements Serializable {
    private TriggerType triggerType = TriggerType.MANUAL;
    private Map<String, Object> initialVariables = new HashMap<>();
    private String environment = "default";

    public WorkflowExecutionRequest() {}

    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }

    public Map<String, Object> getInitialVariables() { return initialVariables; }
    public void setInitialVariables(Map<String, Object> initialVariables) { this.initialVariables = initialVariables; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
}
