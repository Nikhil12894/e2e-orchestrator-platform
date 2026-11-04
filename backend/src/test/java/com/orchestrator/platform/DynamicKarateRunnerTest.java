package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DynamicKarateRunnerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private DynamicKarateRunner karateRunner;

    @Test
    void testExecuteDynamicWorkflowWithKarate() {
        List<TestWorkflowEntity> workflows = workflowRepository.findAll();
        assertFalse(workflows.isEmpty(), "Workflow should have been seeded");

        TestWorkflowEntity workflow = workflows.get(0);

        // Adjust localhost URLs to use random port
        workflow.getWorkflowAst().getSteps().forEach(step -> {
            if (step.getHttpConfig() != null && step.getHttpConfig().getUrl() != null) {
                step.getHttpConfig().setUrl(step.getHttpConfig().getUrl().replace("8080", String.valueOf(port)));
            }
        });

        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowName(workflow.getName());
        execution.setStatus(ExecutionStatus.PENDING);
        execution = executionRepository.save(execution);

        WorkflowExecutionEntity finishedExecution = karateRunner.executeWorkflow(
                execution, workflow, new HashMap<>(), "test");

        assertNotNull(finishedExecution);
        assertEquals(ExecutionStatus.COMPLETED, finishedExecution.getStatus());
        assertTrue(finishedExecution.getDurationMs() > 0);
        assertNotNull(finishedExecution.getStepResults());
        assertFalse(finishedExecution.getStepResults().isEmpty());

        // Verify context snapshot contains extracted tokens
        Map<String, Object> snapshot = finishedExecution.getContextSnapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.containsKey("authToken") || snapshot.containsKey("step_auth.authToken"));

        // Verify HTML report was generated
        assertNotNull(finishedExecution.getReportPath());
        File reportDir = karateRunner.getReportDir(finishedExecution.getId()).toFile();
        assertTrue(reportDir.exists(), "Report directory should exist");
    }
}
