package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class WorkflowExecutionNavigationUrlTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private TestWorkflowEntity testWorkflow;

    @BeforeEach
    void setUp() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .addFilters(new ForwardedHeaderFilter())
                    .build();
        }
        if (testWorkflow == null) {
            TestWorkflowEntity wf = new TestWorkflowEntity();
            wf.setName("Navigation URL Verification Flow");
            wf.setCanvasState(new CanvasState());
            testWorkflow = workflowRepository.save(wf);
        }
    }

    @Test
    @DisplayName("EXEC-01 & EXEC-02: Triggering execution returns HTTP 202, Location header, and absolute statusUrl/reportUrl/streamUrl")
    void testTriggerExecutionReturnsLocationHeaderAndUrls() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute", testWorkflow.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.statusUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$.streamUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/stream")))
                .andExpect(jsonPath("$.reportUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/report/.*")));
    }
}
