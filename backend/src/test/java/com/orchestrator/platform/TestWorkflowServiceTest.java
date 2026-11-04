package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.TestWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TestWorkflowServiceTest {

    @Autowired
    private TestWorkflowService workflowService;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Test
    @DisplayName("Verify workflow creation and cascade deletion with executions")
    public void testWorkflowCreateAndDeleteCascade() {
        // 1. Create a test workflow
        WorkflowCreateRequest request = new WorkflowCreateRequest();
        request.setName("Delete Test Flow");
        request.setDescription("Workflow to test deletion and cascade cleanup");
        request.setCanvasState(new CanvasState());

        TestWorkflowEntity workflow = workflowService.createWorkflow(request);
        assertNotNull(workflow.getId());
        assertEquals("Delete Test Flow", workflow.getName());

        UUID workflowId = workflow.getId();

        // 2. Attach mock execution records
        WorkflowExecutionEntity exec1 = new WorkflowExecutionEntity();
        exec1.setWorkflowId(workflowId);
        exec1.setWorkflowName(workflow.getName());
        exec1.setStatus(ExecutionStatus.COMPLETED);
        executionRepository.saveAndFlush(exec1);

        WorkflowExecutionEntity exec2 = new WorkflowExecutionEntity();
        exec2.setWorkflowId(workflowId);
        exec2.setWorkflowName(workflow.getName());
        exec2.setStatus(ExecutionStatus.FAILED);
        executionRepository.saveAndFlush(exec2);

        assertEquals(2, executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId).size());

        // 3. Delete workflow
        workflowService.deleteWorkflow(workflowId);

        // 4. Verify workflow is deleted and executions are cascade-deleted
        Optional<TestWorkflowEntity> foundWorkflow = workflowRepository.findById(workflowId);
        assertFalse(foundWorkflow.isPresent(), "Workflow entity should be deleted");

        assertEquals(0, executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId).size(),
                "Associated execution entities should be cascade-deleted");
    }
}
