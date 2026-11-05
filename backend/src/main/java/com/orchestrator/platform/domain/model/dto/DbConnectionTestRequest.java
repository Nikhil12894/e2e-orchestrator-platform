package com.orchestrator.platform.domain.model.dto;

import java.io.Serializable;
import java.util.Map;

public class DbConnectionTestRequest implements Serializable {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    private String query;
    private String environment = "default";
    private Map<String, Object> envProperties;
    private Map<String, String> envSecrets;
    private Map<String, String> secrets;

    public DbConnectionTestRequest() {}

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Map<String, Object> getEnvProperties() { return envProperties; }
    public void setEnvProperties(Map<String, Object> envProperties) { this.envProperties = envProperties; }

    public Map<String, String> getEnvSecrets() { return envSecrets; }
    public void setEnvSecrets(Map<String, String> envSecrets) { this.envSecrets = envSecrets; }

    public Map<String, String> getSecrets() { return secrets; }
    public void setSecrets(Map<String, String> secrets) { this.secrets = secrets; }
}
