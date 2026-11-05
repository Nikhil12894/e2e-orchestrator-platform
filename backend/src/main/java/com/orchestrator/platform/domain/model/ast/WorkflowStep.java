package com.orchestrator.platform.domain.model.ast;

import com.orchestrator.platform.domain.enums.StepType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class WorkflowStep implements Serializable {
    private String id;
    private String name;
    private StepType type;
    private boolean enabled = true;
    private HttpRequestStepConfig httpConfig;
    private DbPollStepConfig dbPollConfig;
    private KafkaAssertStepConfig kafkaConfig;
    private TransformStepConfig transformConfig;
    private SubWorkflowStepConfig subWorkflowConfig;
    private Long delayMs;
    private List<StepAssertion> assertions = new ArrayList<>();
    private List<VariableExtraction> extractions = new ArrayList<>();
    private boolean continueOnFailure = false;
    private boolean excludeFromReport = false;

    public WorkflowStep() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public StepType getType() { return type; }
    public void setType(StepType type) { this.type = type; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public HttpRequestStepConfig getHttpConfig() { return httpConfig; }
    public void setHttpConfig(HttpRequestStepConfig httpConfig) { this.httpConfig = httpConfig; }

    public DbPollStepConfig getDbPollConfig() { return dbPollConfig; }
    public void setDbPollConfig(DbPollStepConfig dbPollConfig) { this.dbPollConfig = dbPollConfig; }

    public KafkaAssertStepConfig getKafkaConfig() { return kafkaConfig; }
    public void setKafkaConfig(KafkaAssertStepConfig kafkaConfig) { this.kafkaConfig = kafkaConfig; }

    public TransformStepConfig getTransformConfig() { return transformConfig; }
    public void setTransformConfig(TransformStepConfig transformConfig) { this.transformConfig = transformConfig; }

    public SubWorkflowStepConfig getSubWorkflowConfig() { return subWorkflowConfig; }
    public void setSubWorkflowConfig(SubWorkflowStepConfig subWorkflowConfig) { this.subWorkflowConfig = subWorkflowConfig; }

    public Long getDelayMs() { return delayMs; }
    public void setDelayMs(Long delayMs) { this.delayMs = delayMs; }

    public List<StepAssertion> getAssertions() { return assertions; }
    public void setAssertions(List<StepAssertion> assertions) { this.assertions = assertions; }

    public List<VariableExtraction> getExtractions() { return extractions; }
    public void setExtractions(List<VariableExtraction> extractions) { this.extractions = extractions; }

    public boolean isContinueOnFailure() { return continueOnFailure; }
    public void setContinueOnFailure(boolean continueOnFailure) { this.continueOnFailure = continueOnFailure; }

    public boolean isExcludeFromReport() { return excludeFromReport; }
    public void setExcludeFromReport(boolean excludeFromReport) { this.excludeFromReport = excludeFromReport; }
}
