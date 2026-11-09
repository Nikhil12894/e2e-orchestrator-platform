package com.orchestrator.platform.karate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DbHelperTest {

    private static final String H2_URL = "jdbc:h2:mem:db_helper_test;DB_CLOSE_DELAY=-1";

    @BeforeEach
    public void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS inventory (id INT PRIMARY KEY, sku VARCHAR(50), quantity INT, status VARCHAR(50))");
                stmt.execute("DELETE FROM inventory");
                stmt.execute("INSERT INTO inventory (id, sku, quantity, status) VALUES (1, 'SKU-001', 100, 'AVAILABLE')");
                stmt.execute("INSERT INTO inventory (id, sku, quantity, status) VALUES (2, 'SKU-002', 0, 'OUT_OF_STOCK')");
            }
        }
    }

    @Test
    @DisplayName("Verify DbHelper.query executes SELECT query and normalizes column names and values")
    public void testDbHelperQuery() throws Exception {
        Map<String, Object> cfg = Map.of(
                "jdbcUrl", H2_URL,
                "username", "sa",
                "password", "",
                "query", "SELECT id, sku, quantity, status FROM inventory WHERE id = 1"
        );

        List<Map<String, Object>> rows = DbHelper.query(cfg);
        assertNotNull(rows);
        assertEquals(1, rows.size());

        Map<String, Object> row = rows.get(0);
        assertEquals("SKU-001", row.get("SKU"));
        assertEquals("SKU-001", row.get("sku")); // Case-insensitive normalized access
        assertEquals(100, ((Number) row.get("quantity")).intValue());
        assertEquals("AVAILABLE", row.get("status"));
    }

    @Test
    @DisplayName("Verify DbHelper.poll polls until condition matches")
    public void testDbHelperPollConditionMatches() {
        Map<String, Object> cfg = Map.of(
                "jdbcUrl", H2_URL,
                "username", "sa",
                "password", "",
                "query", "SELECT status FROM inventory WHERE sku = 'SKU-001'",
                "condition", "row.status == 'AVAILABLE'",
                "maxAttempts", 3,
                "intervalMs", 100,
                "timeoutMs", 5000
        );

        Map<String, Object> result = DbHelper.poll(cfg);
        assertNotNull(result);
        assertEquals(true, result.get("matched"));
        assertTrue((int) result.get("attempts") >= 1);
        assertNotNull(result.get("row"));
        assertEquals("AVAILABLE", ((Map<?, ?>) result.get("row")).get("status"));
    }

    @Test
    @DisplayName("Verify DbHelper.poll polls with expectedRowCount")
    public void testDbHelperPollExpectedRowCount() {
        Map<String, Object> cfg = Map.of(
                "jdbcUrl", H2_URL,
                "username", "sa",
                "password", "",
                "query", "SELECT * FROM inventory",
                "expectedRowCount", 2,
                "maxAttempts", 2,
                "intervalMs", 100,
                "timeoutMs", 3000
        );

        Map<String, Object> result = DbHelper.poll(cfg);
        assertNotNull(result);
        assertEquals(true, result.get("matched"));
        List<?> rows = (List<?>) result.get("rows");
        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("Verify DbHelper.poll timeout when condition never matches")
    public void testDbHelperPollConditionFailsGracefully() {
        Map<String, Object> cfg = Map.of(
                "jdbcUrl", H2_URL,
                "username", "sa",
                "password", "",
                "query", "SELECT status FROM inventory WHERE sku = 'NON_EXISTENT'",
                "condition", "row.status == 'COMPLETED'",
                "maxAttempts", 2,
                "intervalMs", 50,
                "timeoutMs", 200
        );

        Map<String, Object> result = DbHelper.poll(cfg);
        assertNotNull(result);
        assertEquals(false, result.get("matched"));
        assertEquals(2, result.get("attempts"));
    }

    @Test
    @DisplayName("Verify DbHelper.executeUpdate executes DDL and DML statements")
    public void testDbHelperExecuteUpdate() throws Exception {
        Map<String, Object> updateCfg = Map.of(
                "jdbcUrl", H2_URL,
                "username", "sa",
                "password", "",
                "sql", "UPDATE inventory SET quantity = 150 WHERE id = 1"
        );

        int updated = DbHelper.executeUpdate(updateCfg);
        assertEquals(1, updated);

        List<Map<String, Object>> rows = DbHelper.query(Map.of(
                "jdbcUrl", H2_URL, "username", "sa", "password", "",
                "query", "SELECT quantity FROM inventory WHERE id = 1"
        ));
        assertEquals(150, ((Number) rows.get(0).get("quantity")).intValue());
    }

    @Test
    @DisplayName("Verify DbHelper.resolveToken interpolates environment and system property macros")
    public void testDbHelperResolveToken() {
        System.setProperty("TEST_DB_USER", "postgres_admin");
        try {
            String resolved = DbHelper.resolveToken("jdbc:postgresql://localhost:5432/${$env:TEST_DB_USER}");
            assertEquals("jdbc:postgresql://localhost:5432/postgres_admin", resolved);
        } finally {
            System.clearProperty("TEST_DB_USER");
        }
    }

    @Test
    @DisplayName("Verify driver auto-detection for all supported databases")
    public void testDriverDetection() {
        assertEquals("org.postgresql.Driver", DbHelper.detectDriverClassName("jdbc:postgresql://localhost:5432/db"));
        assertEquals("com.mysql.cj.jdbc.Driver", DbHelper.detectDriverClassName("jdbc:mysql://localhost:3306/db"));
        assertEquals("org.mariadb.jdbc.Driver", DbHelper.detectDriverClassName("jdbc:mariadb://localhost:3306/db"));
        assertEquals("oracle.jdbc.OracleDriver", DbHelper.detectDriverClassName("jdbc:oracle:thin:@localhost:1521:xe"));
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", DbHelper.detectDriverClassName("jdbc:sqlserver://localhost:1433;databaseName=db"));
        assertEquals("org.sqlite.JDBC", DbHelper.detectDriverClassName("jdbc:sqlite:sample.db"));
        assertEquals("org.h2.Driver", DbHelper.detectDriverClassName("jdbc:h2:mem:test"));
    }
}
