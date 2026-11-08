package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.enums.StepType;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
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
}
