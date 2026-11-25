package com.orchestrator.platform.standalone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.orchestrator.platform"
})
public class StandaloneApplication {

    public static void main(String[] args) {
        System.out.println("""
            ========================================================================
             MeterFlow Standalone E2E Orchestrator (Zero-Docker / H2 File DB)
             Storage:  ./data/meterflow.mv.db
             Reports:  ./data/reports
             Web UI:   http://localhost:8080
            ========================================================================
            """);
        SpringApplication.run(StandaloneApplication.class, args);
    }
}
