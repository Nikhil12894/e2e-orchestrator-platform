package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.TestWorkflowExecutionService;
import com.orchestrator.platform.service.TestWorkflowService;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ExecutionCleanupAndStorageReclamationTest {

    @Autowired
    private DynamicKarateRunner karateRunner;

    @Autowired
    private TestWorkflowExecutionService executionService;

    @Autowired
    private TestWorkflowService workflowService;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Test
    @DisplayName("Verify DynamicKarateRunner deletes report and features directories from disk")
    public void testDeleteExecutionFiles() throws IOException {
        UUID executionId = UUID.randomUUID();
        Path reportDir = karateRunner.getReportDir(executionId);
        Path featuresDir = reportDir.resolveSibling("karate-features").resolve(executionId.toString());

        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("karate-summary.html"), "<html>Summary</html>");
        Files.createDirectories(featuresDir);
        Files.writeString(featuresDir.resolve("workflow-execution.feature"), "Feature: test");

        assertTrue(Files.exists(reportDir));
        assertTrue(Files.exists(featuresDir));

        karateRunner.deleteExecutionFiles(executionId);

        assertFalse(Files.exists(reportDir), "Report directory must be deleted");
        assertFalse(Files.exists(featuresDir), "Features directory must be deleted");
    }

    @Test
    @DisplayName("Verify deleting a single execution removes DB entity and reclaims disk space")
    public void testDeleteSingleExecution() throws IOException {
        WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
        exec.setWorkflowId(UUID.randomUUID());
        exec.setWorkflowName("Single Delete Test");
        exec.setStatus(ExecutionStatus.COMPLETED);
        exec = executionRepository.saveAndFlush(exec);

        UUID executionId = exec.getId();
        Path reportDir = karateRunner.getReportDir(executionId);
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("report.html"), "Dummy report");
        assertTrue(Files.exists(reportDir));

        executionService.deleteExecution(executionId);

        assertTrue(executionRepository.findById(executionId).isEmpty(), "Entity must be removed from DB");
        assertFalse(Files.exists(reportDir), "Disk report directory must be removed");
    }

    @Test
    @DisplayName("Verify clearing all executions for a workflow removes all DB rows and disk folders")
    public void testClearExecutionsForWorkflow() throws IOException {
        UUID workflowId = UUID.randomUUID();

        WorkflowExecutionEntity exec1 = new WorkflowExecutionEntity();
        exec1.setWorkflowId(workflowId);
        exec1.setWorkflowName("Clear Wf Test");
        exec1.setStatus(ExecutionStatus.COMPLETED);
        exec1 = executionRepository.saveAndFlush(exec1);

        WorkflowExecutionEntity exec2 = new WorkflowExecutionEntity();
        exec2.setWorkflowId(workflowId);
        exec2.setWorkflowName("Clear Wf Test");
        exec2.setStatus(ExecutionStatus.FAILED);
        exec2 = executionRepository.saveAndFlush(exec2);

        Path reportDir1 = karateRunner.getReportDir(exec1.getId());
        Path reportDir2 = karateRunner.getReportDir(exec2.getId());
        Files.createDirectories(reportDir1);
        Files.createDirectories(reportDir2);

        assertTrue(Files.exists(reportDir1));
        assertTrue(Files.exists(reportDir2));

        executionService.deleteExecutionsForWorkflow(workflowId);

        List<WorkflowExecutionEntity> remaining = executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
        assertTrue(remaining.isEmpty(), "All executions for workflow must be removed from DB");
        assertFalse(Files.exists(reportDir1), "Report dir 1 must be purged from disk");
        assertFalse(Files.exists(reportDir2), "Report dir 2 must be purged from disk");
    }

    @Test
    @DisplayName("Verify bulk purgeExecutions retains keepLatest runs and purges older ones from DB and disk")
    public void testPurgeExecutionsRetention() throws IOException {
        UUID workflowId = UUID.randomUUID();

        // Create 4 execution runs
        WorkflowExecutionEntity e1 = createExecutionWithDiskFiles(workflowId);
        WorkflowExecutionEntity e2 = createExecutionWithDiskFiles(workflowId);
        WorkflowExecutionEntity e3 = createExecutionWithDiskFiles(workflowId);
        WorkflowExecutionEntity e4 = createExecutionWithDiskFiles(workflowId);

        // Purge keeping latest 2
        int purged = executionService.purgeExecutions(2, null);
        assertTrue(purged >= 2, "At least 2 executions should be purged");

        List<WorkflowExecutionEntity> remaining = executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
        assertEquals(2, remaining.size(), "Only 2 executions should remain for this workflow");

        // The older executions (e1, e2) should have had their disk folders deleted
        assertFalse(Files.exists(karateRunner.getReportDir(e1.getId())), "e1 report dir should be deleted");
        assertFalse(Files.exists(karateRunner.getReportDir(e2.getId())), "e2 report dir should be deleted");
    }

    @Test
    @DisplayName("Verify deleting a workflow cascades disk cleanup for all associated execution runs")
    public void testDeleteWorkflowCascadesDiskCleanup() throws IOException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setName("Cascade Disk Test");
        req.setCanvasState(new CanvasState());
        TestWorkflowEntity wf = workflowService.createWorkflow(req);

        WorkflowExecutionEntity exec = createExecutionWithDiskFiles(wf.getId());
        Path reportDir = karateRunner.getReportDir(exec.getId());
        assertTrue(Files.exists(reportDir));

        workflowService.deleteWorkflow(wf.getId());

        assertTrue(workflowRepository.findById(wf.getId()).isEmpty());
        assertTrue(executionRepository.findByWorkflowIdOrderByCreatedAtDesc(wf.getId()).isEmpty());
        assertFalse(Files.exists(reportDir), "Workflow deletion must purge execution disk files to prevent orphans");
    }

    private WorkflowExecutionEntity createExecutionWithDiskFiles(UUID workflowId) throws IOException {
        WorkflowExecutionEntity ex = new WorkflowExecutionEntity();
        ex.setWorkflowId(workflowId);
        ex.setWorkflowName("Retention Test Workflow");
        ex.setStatus(ExecutionStatus.COMPLETED);
        ex = executionRepository.saveAndFlush(ex);

        Path reportDir = karateRunner.getReportDir(ex.getId());
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("report.html"), "Dummy content");
        return ex;
    }
}
