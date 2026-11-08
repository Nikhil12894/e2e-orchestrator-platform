package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;

/**
 * Encapsulates the execution entity and boolean timed-out status for synchronous executions.
 */
public record ExecutionSyncResult(
        WorkflowExecutionEntity execution,
        boolean timedOut
) {}
