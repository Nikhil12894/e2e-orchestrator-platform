package com.orchestrator.platform.config;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes Flyway configuration for the E2E Orchestrator platform.
 * Disables Flyway placeholder replacement so that orchestration expression variables
 * (such as ${authToken}, ${userId}, ${$env:VAR}, ${$secret:KEY}) stored inside
 * workflow JSON definitions are not mistaken for Flyway migration placeholders.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> configuration.placeholderReplacement(false);
    }
}
