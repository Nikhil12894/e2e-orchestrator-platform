package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.HttpRequestStepConfig;
import com.orchestrator.platform.domain.model.ast.TransformStepConfig;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class WorkflowExecutionSyncModeTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private TestWorkflowEntity fastWorkflow;
    private TestWorkflowEntity failingWorkflow;
    private TestWorkflowEntity delayWorkflow;

    @BeforeEach
    void setUp() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .addFilters(new ForwardedHeaderFilter())
                    .build();
        }

        if (fastWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Fast Synchronous Test Flow");
            wf.setCanvasState(new CanvasState());

            WorkflowAst ast = new WorkflowAst();
            WorkflowStep step = new WorkflowStep();
            step.setId(UUID.randomUUID().toString());
            step.setName("Fast Transform Step");
            step.setType(StepType.TRANSFORM);
            step.setEnabled(true);

            TransformStepConfig transformConfig = new TransformStepConfig();
            transformConfig.setScript("function(ctx) { return { status: 'OK' }; }");
            step.setTransformConfig(transformConfig);

            ast.setSteps(List.of(step));
            wf.setWorkflowAst(ast);
            fastWorkflow = workflowRepository.saveAndFlush(wf);
        }

        if (failingWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Failing Synchronous Test Flow");
            wf.setCanvasState(new CanvasState());

            WorkflowAst ast = new WorkflowAst();
            WorkflowStep step = new WorkflowStep();
            step.setId(UUID.randomUUID().toString());
            step.setName("Failing Step");
            step.setType(StepType.HTTP_REQUEST);
            step.setEnabled(true);

            HttpRequestStepConfig httpConfig = new HttpRequestStepConfig();
            httpConfig.setUrl("http://127.0.0.1:59999/api/fail");
            httpConfig.setMethod("GET");
            httpConfig.setRetryCount(1);
            httpConfig.setRetryIntervalMs(50);
            httpConfig.setExpectedStatus(200);
            step.setHttpConfig(httpConfig);

            ast.setSteps(List.of(step));
            wf.setWorkflowAst(ast);
            failingWorkflow = workflowRepository.saveAndFlush(wf);
        }

        if (delayWorkflow == null) {
            delayWorkflow = workflowRepository.saveAndFlush(createDelayWorkflow("Delay Test Flow", 3000L));
        }
    }

    private TestWorkflowEntity createDelayWorkflow(String name, long delayMs) {
        TestWorkflowEntity wf = new TestWorkflowEntity();
        wf.setName(name);
        wf.setCanvasState(new CanvasState());

        WorkflowAst ast = new WorkflowAst();
        WorkflowStep step = new WorkflowStep();
        step.setId(UUID.randomUUID().toString());
        step.setName("Delay Step");
        step.setType(StepType.DELAY);
        step.setEnabled(true);
        step.setDelayMs(delayMs);

        ast.setSteps(List.of(step));
        wf.setWorkflowAst(ast);
        return wf;
    }

    @Test
    @DisplayName("SYNC-01 & SYNC-02: Synchronous execution blocks until complete and returns HTTP 200 with navigation URLs")
    void testSynchronousExecutionBlocksUntilComplete() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=30", fastWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.reportUrl", notNullValue()))
                .andExpect(jsonPath("$.statusUrl", notNullValue()))
                .andExpect(jsonPath("$.streamUrl", notNullValue()));
    }

    @Test
    @DisplayName("SYNC-02: Successful synchronous execution returns HTTP 200 with status COMPLETED and populated reportUrl")
    void testSynchronousExecutionReturnsCompletedAndReportUrl() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=30", fastWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.reportUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/report/.*")));
    }

    @Test
    @DisplayName("SYNC-02: Failing workflow execution with ?wait=true returns HTTP 200 with status FAILED and populated reportUrl")
    void testSynchronousExecutionFailureReturns200WithFailedStatus() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=30", failingWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.reportUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/report/.*")));
    }

    @Test
    @DisplayName("SYNC-02: Cancelling in-flight execution immediately unblocks synchronous caller with status CANCELLED")
    void testSynchronousExecutionCancellationUnblocksCaller() throws Exception {
        TestWorkflowEntity cancellationWorkflow = workflowRepository.saveAndFlush(
                createDelayWorkflow("Cancellation Isolated Flow", 5000L));

        CompletableFuture<MvcResult> requestFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=10", cancellationWorkflow.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Await until execution is created and is RUNNING
        UUID executionId = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            List<WorkflowExecutionEntity> runs = executionRepository.findByWorkflowIdOrderByCreatedAtDesc(cancellationWorkflow.getId());
            if (!runs.isEmpty() && runs.get(0).getStatus() == ExecutionStatus.RUNNING) {
                executionId = runs.get(0).getId();
                break;
            }
            Thread.sleep(50);
        }
        assertNotNull(executionId, "Execution should reach RUNNING status");

        long cancelTime = System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/executions/{id}/cancel", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        MvcResult result = requestFuture.get(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - cancelTime;

        assertTrue(elapsed < 2000, "Synchronous request should unblock quickly upon cancellation (elapsed: " + elapsed + "ms)");
        assertEquals(200, result.getResponse().getStatus());
        String responseJson = result.getResponse().getContentAsString();
        assertTrue(responseJson.contains("\"status\":\"CANCELLED\""), "Response should reflect CANCELLED status: " + responseJson);
    }

    @Test
    @DisplayName("SYNC-03: Exceeding timeoutSec returns HTTP 504 Gateway Timeout with X-Execution-Timed-Out header")
    void testSynchronousExecutionTimeoutReturns504() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=1", delayWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Execution-Timed-Out", "true"))
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.status", isOneOf("RUNNING", "PENDING")));
    }

    @Test
    @DisplayName("SYNC-03: Exceeding timeout leaves background execution running until final completion")
    void testTimeoutLeavesBackgroundExecutionRunning() throws Exception {
        MvcResult timeoutResult = mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=1", delayWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Execution-Timed-Out", "true"))
                .andReturn();

        String responseJson = timeoutResult.getResponse().getContentAsString();
        WorkflowExecutionEntity entity = objectMapper.readValue(responseJson, WorkflowExecutionEntity.class);
        UUID executionId = entity.getId();

        // Background execution continues running without being cancelled (mitigating T-04-02)
        long deadline = System.currentTimeMillis() + 10000;
        WorkflowExecutionEntity finalEntity = null;
        while (System.currentTimeMillis() < deadline) {
            WorkflowExecutionEntity current = executionRepository.findById(executionId).orElse(null);
            if (current != null && (current.getStatus() == ExecutionStatus.COMPLETED || current.getStatus() == ExecutionStatus.FAILED)) {
                finalEntity = current;
                break;
            }
            Thread.sleep(100);
        }

        assertNotNull(finalEntity, "Background execution should finish within 10s without cancellation");
        assertEquals(ExecutionStatus.COMPLETED, finalEntity.getStatus());
        assertNotNull(finalEntity.getReportPath());
    }

    @Test
    @DisplayName("SYNC-03: timeoutStatus=202 query parameter overrides default 504 response code")
    void testTimeoutStatus202Override() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=1&timeoutStatus=202", delayWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Execution-Timed-Out", "true"))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", isOneOf("RUNNING", "PENDING")));
    }

    @Test
    @DisplayName("REG-01: Asynchronous execution without ?wait=true returns HTTP 202 without timeout header")
    void testAsynchronousExecutionRegressionReturns202() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute", fastWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("X-Execution-Timed-Out"))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.statusUrl", notNullValue()));
    }

    @Test
    @DisplayName("SYNC-03: Invalid timeoutStatus parameter returns HTTP 400 Bad Request")
    void testInvalidTimeoutStatusReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutStatus=404", fastWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isBadRequest());
    }
}
