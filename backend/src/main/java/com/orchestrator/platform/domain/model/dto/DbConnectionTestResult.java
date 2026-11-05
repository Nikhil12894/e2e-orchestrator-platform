package com.orchestrator.platform.domain.model.dto;

import java.io.Serializable;
import java.util.Map;

public class DbConnectionTestResult implements Serializable {
    private boolean success;
    private String message;
    private String databaseProductName;
    private String databaseProductVersion;
    private String driverName;
    private String driverVersion;
    private Long latencyMs;
    private Map<String, Object> sampleRow;
    private String errorMessage;

    public DbConnectionTestResult() {}

    public static DbConnectionTestResult success(String message, String productName, String productVersion,
                                                 String driverName, String driverVersion, long latencyMs,
                                                 Map<String, Object> sampleRow) {
        DbConnectionTestResult res = new DbConnectionTestResult();
        res.setSuccess(true);
        res.setMessage(message);
        res.setDatabaseProductName(productName);
        res.setDatabaseProductVersion(productVersion);
        res.setDriverName(driverName);
        res.setDriverVersion(driverVersion);
        res.setLatencyMs(latencyMs);
        res.setSampleRow(sampleRow);
        return res;
    }

    public static DbConnectionTestResult failure(String errorMessage, long latencyMs) {
        DbConnectionTestResult res = new DbConnectionTestResult();
        res.setSuccess(false);
        res.setMessage("Database connection failed");
        res.setErrorMessage(errorMessage);
        res.setLatencyMs(latencyMs);
        return res;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDatabaseProductName() { return databaseProductName; }
    public void setDatabaseProductName(String databaseProductName) { this.databaseProductName = databaseProductName; }

    public String getDatabaseProductVersion() { return databaseProductVersion; }
    public void setDatabaseProductVersion(String databaseProductVersion) { this.databaseProductVersion = databaseProductVersion; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverVersion() { return driverVersion; }
    public void setDriverVersion(String driverVersion) { this.driverVersion = driverVersion; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public Map<String, Object> getSampleRow() { return sampleRow; }
    public void setSampleRow(Map<String, Object> sampleRow) { this.sampleRow = sampleRow; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
