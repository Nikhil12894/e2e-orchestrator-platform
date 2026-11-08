package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @DisplayName("EXEC-01 & EXEC-03: Details endpoint returns populated reportUrl and statusUrl matching execution ID")
    void testGetExecutionDetailsContainsReportUrl() throws Exception {
        WorkflowExecutionEntity entity = new WorkflowExecutionEntity();
        entity.setWorkflowId(testWorkflow.getId());
        entity.setWorkflowName(testWorkflow.getName());
        entity.setStatus(ExecutionStatus.COMPLETED);
        entity = executionRepository.save(entity);

        String reportPath = "/api/v1/executions/" + entity.getId() + "/report/workflow-report.html";
        entity.setReportPath(reportPath);
        entity = executionRepository.save(entity);

        mockMvc.perform(get("/api/v1/executions/{id}", entity.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusUrl", is("http://localhost/api/v1/executions/" + entity.getId())))
                .andExpect(jsonPath("$.reportUrl", is("http://localhost" + reportPath)))
                .andExpect(jsonPath("$.streamUrl", is("http://localhost/api/v1/executions/" + entity.getId() + "/stream")));
    }

    @Test
    @DisplayName("PROXY-01: Reverse proxy headers (X-Forwarded-Host & X-Forwarded-Proto) adapt navigation URLs")
    void testForwardedHeadersAdaptNavigationUrls() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/{id}/execute", testWorkflow.getId())
                        .header("X-Forwarded-Host", "ci-runner.internal.org:9000")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")))
                .andExpect(jsonPath("$.statusUrl", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")))
                .andExpect(jsonPath("$.reportUrl", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")))
                .andExpect(jsonPath("$.streamUrl", startsWith("https://ci-runner.internal.org:9000/api/v1/executions/")));
    }

    @Test
    @DisplayName("EXEC-01: List executions endpoint enriches all executions with statusUrl, streamUrl, and reportUrl")
    void testGetAllExecutionsContainsNavigationUrls() throws Exception {
        mockMvc.perform(get("/api/v1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].statusUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+")))
                .andExpect(jsonPath("$[0].streamUrl", matchesPattern("http://localhost/api/v1/executions/[0-9a-fA-F-]+/stream")))
                .andExpect(jsonPath("$[0].reportUrl", notNullValue()));
    }
}
