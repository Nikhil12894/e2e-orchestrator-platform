package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionStepResult implements Serializable {
    private String stepId;
    private String stepName;
    private StepType stepType;
    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private Object requestPayload;
    private Object responsePayload;
    private Integer httpStatus;
    private boolean assertionsPassed = true;
    private List<String> assertionDetails = new ArrayList<>();
    private Map<String, Object> extractedVariables = new HashMap<>();
    private String errorMessage;
    private boolean excludeFromReport = false;

    public ExecutionStepResult() {}

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public StepType getStepType() { return stepType; }
    public void setStepType(StepType stepType) { this.stepType = stepType; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Object getRequestPayload() { return requestPayload; }
    public void setRequestPayload(Object requestPayload) { this.requestPayload = requestPayload; }

    public Object getResponsePayload() { return responsePayload; }
    public void setResponsePayload(Object responsePayload) { this.responsePayload = responsePayload; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public boolean isAssertionsPassed() { return assertionsPassed; }
    public void setAssertionsPassed(boolean assertionsPassed) { this.assertionsPassed = assertionsPassed; }

    public List<String> getAssertionDetails() { return assertionDetails; }
    public void setAssertionDetails(List<String> assertionDetails) { this.assertionDetails = assertionDetails; }

    public Map<String, Object> getExtractedVariables() { return extractedVariables; }
    public void setExtractedVariables(Map<String, Object> extractedVariables) { this.extractedVariables = extractedVariables; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isExcludeFromReport() { return excludeFromReport; }
    public void setExcludeFromReport(boolean excludeFromReport) { this.excludeFromReport = excludeFromReport; }
}
