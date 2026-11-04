package com.orchestrator.platform.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
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
                    row.put(colName, val);
                }
                results.add(row);
            }
        }
        return results;
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
            if (driverClassName != null && !driverClassName.trim().isEmpty()) {
                config.setDriverClassName(driverClassName);
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
