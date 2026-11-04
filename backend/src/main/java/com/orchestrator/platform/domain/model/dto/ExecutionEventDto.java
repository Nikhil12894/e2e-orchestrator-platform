package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.enums.ExecutionStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class ExecutionEventDto implements Serializable {
    private UUID executionId;
    private UUID workflowId;
    private String eventType; // WORKFLOW_STARTED, STEP_STARTED, STEP_FINISHED, WORKFLOW_FINISHED, LOG_MESSAGE
    private String stepId;
    private String stepName;
    private ExecutionStatus status;
    private String message;
    private Instant timestamp = Instant.now();
    private Object details;

    public ExecutionEventDto() {}

    public ExecutionEventDto(UUID executionId, UUID workflowId, String eventType, String stepId,
                             String stepName, ExecutionStatus status, String message, Object details) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.eventType = eventType;
        this.stepId = stepId;
        this.stepName = stepName;
        this.status = status;
        this.message = message;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public UUID getWorkflowId() { return workflowId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Object getDetails() { return details; }
    public void setDetails(Object details) { this.details = details; }
}
