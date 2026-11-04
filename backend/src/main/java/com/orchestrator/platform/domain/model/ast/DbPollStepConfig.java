package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;

public class DbPollStepConfig implements Serializable {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";
    private String query;
    private Long pollIntervalMs = 1000L;
    private Long timeoutMs = 15000L;
    private String condition; // e.g. "row.status == 'COMPLETED'" or "row_count > 0"
    private Integer expectedRowCount;

    public DbPollStepConfig() {}

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

    public Long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(Long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public Integer getExpectedRowCount() { return expectedRowCount; }
    public void setExpectedRowCount(Integer expectedRowCount) { this.expectedRowCount = expectedRowCount; }
}
