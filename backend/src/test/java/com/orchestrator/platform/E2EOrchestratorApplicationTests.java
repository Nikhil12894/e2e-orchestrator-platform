package com.orchestrator.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class E2EOrchestratorApplicationTests {

    @Test
    void contextLoads() {
        // Confirms Spring Boot 3 context bootstraps cleanly
    }
}
