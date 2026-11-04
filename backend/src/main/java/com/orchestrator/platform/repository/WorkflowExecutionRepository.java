package com.orchestrator.platform.repository;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, UUID> {
    List<WorkflowExecutionEntity> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);
    List<WorkflowExecutionEntity> findAllByOrderByCreatedAtDesc();
    long countByStatus(ExecutionStatus status);
}
