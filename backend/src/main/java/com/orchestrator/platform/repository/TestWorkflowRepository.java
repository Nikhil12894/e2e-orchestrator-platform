package com.orchestrator.platform.repository;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestWorkflowRepository extends JpaRepository<TestWorkflowEntity, UUID> {
    List<TestWorkflowEntity> findAllByOrderByUpdatedAtDesc();
}
