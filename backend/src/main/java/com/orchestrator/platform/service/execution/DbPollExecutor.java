package com.orchestrator.platform.service.execution;

import com.orchestrator.platform.config.HikariConnectionPoolManager;
import com.orchestrator.platform.domain.model.ast.DbPollStepConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class DbPollExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbPollExecutor.class);
    private final HikariConnectionPoolManager connectionPoolManager;

    public DbPollExecutor(HikariConnectionPoolManager connectionPoolManager) {
        this.connectionPoolManager = connectionPoolManager;
    }

    public static class DbPollResult {
        private boolean matched;
        private int attempts;
        private long durationMs;
        private List<Map<String, Object>> rows = new ArrayList<>();
        private Map<String, Object> latestRow = new HashMap<>();
        private String errorMessage;

        public boolean isMatched() { return matched; }
        public void setMatched(boolean matched) { this.matched = matched; }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }

        public Map<String, Object> getLatestRow() { return latestRow; }
        public void setLatestRow(Map<String, Object> latestRow) { this.latestRow = latestRow; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public DbPollResult execute(DbPollStepConfig config) {
        DbPollResult result = new DbPollResult();
        long startTime = System.currentTimeMillis();
        long timeoutMs = config.getTimeoutMs() != null ? config.getTimeoutMs() : 15000L;
        long intervalMs = config.getPollIntervalMs() != null ? config.getPollIntervalMs() : 1000L;

        int attempts = 0;
        while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            attempts++;
            try {
                List<Map<String, Object>> rows = connectionPoolManager.executeQuery(
                        config.getJdbcUrl(),
                        config.getUsername(),
                        config.getPassword(),
                        config.getDriverClassName(),
                        config.getQuery()
                );

                result.setRows(rows);
                if (!rows.isEmpty()) {
                    result.setLatestRow(rows.get(0));
                }

                boolean conditionMatched = checkCondition(rows, config);
                if (conditionMatched) {
                    result.setMatched(true);
                    result.setAttempts(attempts);
                    result.setDurationMs(System.currentTimeMillis() - startTime);
                    return result;
                }

            } catch (Exception e) {
                log.warn("DB poll attempt {} failed: {}", attempts, e.getMessage());
                result.setErrorMessage(e.getMessage());
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        result.setMatched(false);
        result.setAttempts(attempts);
        result.setDurationMs(System.currentTimeMillis() - startTime);
        if (result.getErrorMessage() == null) {
            result.setErrorMessage("DB poll timed out after " + result.getDurationMs() + "ms. Condition was not satisfied.");
        }
        return result;
    }

    private boolean checkCondition(List<Map<String, Object>> rows, DbPollStepConfig config) {
        if (config.getExpectedRowCount() != null) {
            return rows.size() == config.getExpectedRowCount();
        }

        if (config.getCondition() == null || config.getCondition().trim().isEmpty()) {
            return !rows.isEmpty();
        }

        String condition = config.getCondition().trim();
        // Simple evaluator: row.column == 'value' or column == 'value' or count > 0
        if (rows.isEmpty()) {
            return false;
        }

        Map<String, Object> firstRow = rows.get(0);
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            String field = parts[0].trim().replace("row.", "");
            String expected = parts[1].trim().replace("'", "").replace("\"", "");

            Object actualVal = firstRow.get(field);
            if (actualVal == null) {
                // try case-insensitive
                for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(field)) {
                        actualVal = entry.getValue();
                        break;
                    }
                }
            }
            return actualVal != null && expected.equalsIgnoreCase(String.valueOf(actualVal));
        }

        if (condition.toLowerCase().contains("count > 0") || condition.toLowerCase().contains("not_empty")) {
            return !rows.isEmpty();
        }

        return !rows.isEmpty();
    }
}
