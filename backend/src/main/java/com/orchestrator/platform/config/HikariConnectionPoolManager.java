package com.orchestrator.platform.config;

import com.orchestrator.platform.domain.model.dto.DbConnectionTestResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HikariConnectionPoolManager {

    private static final Logger log = LoggerFactory.getLogger(HikariConnectionPoolManager.class);
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    public List<Map<String, Object>> executeQuery(String jdbcUrl, String username, String password,
                                                   String driverClassName, String sql) throws Exception {
        HikariDataSource ds = getOrCreateDataSource(jdbcUrl, username, password, driverClassName);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = metaData.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    row.put(colName, normalizeSqlValue(val));
                }
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Normalizes JDBC driver return types into JSON-safe standard Java types.
     * Prevents Jackson serialization errors with Oracle types (e.g. oracle.sql.TIMESTAMP -> ByteArrayInputStream).
     */
    public static Object normalizeSqlValue(Object val) {
        if (val == null) {
            return null;
        }

        // 1. Primitive wrappers and strings
        if (val instanceof String || val instanceof Number || val instanceof Boolean) {
            return val;
        }

        // 2. Standard java.time types
        if (val instanceof TemporalAccessor) {
            return val.toString();
        }

        // 3. java.sql.Timestamp -> ISO-8601 String
        if (val instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }

        // 4. java.sql.Date -> ISO Date String (yyyy-MM-dd)
        if (val instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }

        // 5. java.sql.Time -> Time String (HH:mm:ss)
        if (val instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }

        // 6. java.sql.Clob / java.sql.NClob -> String
        if (val instanceof java.sql.Clob clob) {
            try {
                long len = clob.length();
                int maxLen = (int) Math.min(len, 1_000_000L);
                return clob.getSubString(1, maxLen);
            } catch (Exception e) {
                log.warn("Failed to extract string from Clob: {}", e.getMessage());
            }
        }

        // 7. java.sql.Blob / byte[] -> Base64 String
        if (val instanceof java.sql.Blob blob) {
            try {
                long len = blob.length();
                int maxLen = (int) Math.min(len, 1_000_000L);
                return Base64.getEncoder().encodeToString(blob.getBytes(1, maxLen));
            } catch (Exception e) {
                log.warn("Failed to extract bytes from Blob: {}", e.getMessage());
            }
        }
        if (val instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }

        // 8. java.sql.Array -> List of normalized items
        if (val instanceof java.sql.Array sqlArray) {
            try {
                Object arr = sqlArray.getArray();
                if (arr instanceof Object[] objArr) {
                    List<Object> list = new ArrayList<>(objArr.length);
                    for (Object item : objArr) {
                        list.add(normalizeSqlValue(item));
                    }
                    return list;
                }
            } catch (Exception e) {
                log.warn("Failed to extract SQL Array: {}", e.getMessage());
            }
        }

        // 9. Oracle-specific types via reflection (oracle.sql.TIMESTAMP, DATE, TIMESTAMPTZ, ROWID, etc.)
        String className = val.getClass().getName();
        if (className.startsWith("oracle.sql.")) {
            // Priority 1: timestampValue() -> java.sql.Timestamp -> ISO Instant
            try {
                Method m = val.getClass().getMethod("timestampValue");
                Object res = m.invoke(val);
                if (res instanceof java.sql.Timestamp ts) {
                    return ts.toInstant().toString();
                }
            } catch (Exception ignored) {}

            // Priority 2: dateValue() -> java.sql.Date -> LocalDate String
            try {
                Method m = val.getClass().getMethod("dateValue");
                Object res = m.invoke(val);
                if (res instanceof java.sql.Date d) {
                    return d.toLocalDate().toString();
                }
            } catch (Exception ignored) {}

            // Priority 3: stringValue() -> String
            try {
                Method m = val.getClass().getMethod("stringValue");
                Object res = m.invoke(val);
                if (res != null) {
                    return res.toString();
                }
            } catch (Exception ignored) {}
        }

        // 10. Generic fallback: return toString()
        return val.toString();
    }

    /**
     * Interactive test connection utility for validating database reachability and credentials.
     */
    public DbConnectionTestResult testConnection(String jdbcUrl, String username, String password,
                                                 String driverClassName, String query) {
        long startTime = System.currentTimeMillis();
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return DbConnectionTestResult.failure("JDBC URL is required", 0);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl.trim());
        if (username != null && !username.trim().isEmpty()) config.setUsername(username.trim());
        if (password != null) config.setPassword(password);

        String effectiveDriver = detectDriverClassName(jdbcUrl, driverClassName);
        if (effectiveDriver != null && !effectiveDriver.trim().isEmpty()) {
            config.setDriverClassName(effectiveDriver);
        }

        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000L);
        config.setValidationTimeout(3000L);
        config.setPoolName("TestConnPool-" + System.currentTimeMillis());

        try (HikariDataSource ds = new HikariDataSource(config);
             Connection conn = ds.getConnection()) {

            boolean valid = conn.isValid(5);
            if (!valid) {
                long latency = System.currentTimeMillis() - startTime;
                return DbConnectionTestResult.failure("Connection established but failed validity check", latency);
            }

            DatabaseMetaData meta = conn.getMetaData();
            String prodName = meta.getDatabaseProductName();
            String prodVersion = meta.getDatabaseProductVersion();
            String driverName = meta.getDriverName();
            String driverVersion = meta.getDriverVersion();

            Map<String, Object> sampleRow = null;
            if (query != null && !query.trim().isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.setMaxRows(1);
                    stmt.setQueryTimeout(5);
                    try (ResultSet rs = stmt.executeQuery(query.trim())) {
                        ResultSetMetaData rsMeta = rs.getMetaData();
                        int colCount = rsMeta.getColumnCount();
                        if (rs.next()) {
                            sampleRow = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                sampleRow.put(rsMeta.getColumnLabel(i), normalizeSqlValue(rs.getObject(i)));
                            }
                        }
                    }
                }
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            String message = String.format("Connected successfully to %s in %dms", prodName, latencyMs);
            return DbConnectionTestResult.success(message, prodName, prodVersion, driverName, driverVersion, latencyMs, sampleRow);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("Database test connection failed for {}: {}", jdbcUrl, e.getMessage());
            return DbConnectionTestResult.failure(e.getMessage() != null ? e.getMessage() : e.toString(), latencyMs);
        }
    }

    public static String detectDriverClassName(String jdbcUrl, String explicitDriver) {
        if (explicitDriver != null && !explicitDriver.trim().isEmpty()) {
            return explicitDriver.trim();
        }
        if (jdbcUrl == null) return null;
        String lower = jdbcUrl.toLowerCase().trim();
        if (lower.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        } else if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (lower.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        } else if (lower.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        return null;
    }

    private synchronized HikariDataSource getOrCreateDataSource(String jdbcUrl, String username,
                                                                String password, String driverClassName) {
        String key = jdbcUrl + "|" + (username != null ? username : "");
        return dataSources.computeIfAbsent(key, k -> {
            log.info("Creating dynamic HikariDataSource pool for {}", jdbcUrl);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            if (username != null) config.setUsername(username);
            if (password != null) config.setPassword(password);

            String effectiveDriver = detectDriverClassName(jdbcUrl, driverClassName);
            if (effectiveDriver != null && !effectiveDriver.trim().isEmpty()) {
                config.setDriverClassName(effectiveDriver);
                log.info("Configured JDBC driver: {} for URL: {}", effectiveDriver, jdbcUrl);
            }
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(60000);
            config.setConnectionTimeout(10000);
            config.setPoolName("DynamicPool-" + Math.abs(key.hashCode()));
            return new HikariDataSource(config);
        });
    }

    @PreDestroy
    public void cleanup() {
        for (HikariDataSource ds : dataSources.values()) {
            try {
                ds.close();
            } catch (Exception e) {
                log.warn("Error closing Hikari pool: {}", e.getMessage());
            }
        }
        dataSources.clear();
    }
}
