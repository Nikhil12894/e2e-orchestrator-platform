package com.orchestrator.platform.standalone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.repository.TestWorkflowRepository;

@SpringBootTest(classes = StandaloneApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:standalone_test_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "orchestrator.reports.base-path=./target/test-reports"
})
public class StandaloneApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestWorkflowRepository workflowRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("Verify StandaloneApplication boots with H2, seeds reference workflows, and serves REST API & UI")
    public void testStandaloneAppBootAndSeeding() throws Exception {
        // 1. Verify workflows are seeded
        List<TestWorkflowEntity> workflows = workflowRepository.findAll();
        assertNotNull(workflows);
        assertFalse(workflows.isEmpty(), "Reference workflows should be automatically seeded");

        // 2. Verify REST API returns seeded workflows
        HttpRequest apiRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/workflows"))
                .GET()
                .build();
        HttpResponse<String> apiResponse = httpClient.send(apiRequest, HttpResponse.BodyHandlers.ofString());
        assertTrue(apiResponse.statusCode() >= 200 && apiResponse.statusCode() < 300,
                "API request should return 2xx status, got: " + apiResponse.statusCode());
        assertTrue(apiResponse.body().contains("[Reference]"), "Response should list reference workflows");

        // 3. Verify static index.html resolves from root
        HttpRequest spaRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/"))
                .GET()
                .build();
        HttpResponse<String> spaResponse = httpClient.send(spaRequest, HttpResponse.BodyHandlers.ofString());
        assertTrue(spaResponse.statusCode() >= 200 && spaResponse.statusCode() < 300,
                "Root endpoint should return 2xx status, got: " + spaResponse.statusCode());
        assertTrue(spaResponse.body().contains("<div id=\"root\">") || spaResponse.body().contains("html"),
                "Root endpoint should return bundled HTML page");
    }
}
