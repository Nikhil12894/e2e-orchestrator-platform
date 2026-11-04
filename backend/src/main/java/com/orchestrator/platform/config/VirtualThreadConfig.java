package com.orchestrator.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfig {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfig.class);

    @Bean(name = "workflowVirtualThreadExecutor")
    public ExecutorService workflowVirtualThreadExecutor() {
        log.info("Initializing Java 21 Virtual Thread Per Task Executor for Workflow Runs");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
