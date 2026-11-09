package com.orchestrator.platform.karate;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * Universal Database Helper for Karate Scenarios and Standalone CLI executions.
 * Supports PostgreSQL, MySQL, MariaDB, Oracle, Microsoft SQL Server, SQLite, H2,
 * plus dynamic external third-party JDBC drivers (Snowflake, DB2, ClickHouse, etc.).
 */
public class DbHelper {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final ScriptEngine SCRIPT_ENGINE = new ScriptEngineManager().getEngineByName("nashorn") != null
            ? new ScriptEngineManager().getEngineByName("nashorn")
            : new ScriptEngineManager().getEngineByName("JavaScript");

    /**
     * Poll a database with a query until an expected condition or row count matches,
     * or timeout/max-attempts expire.
     *
     * @param config Map containing jdbcUrl, username, password, query, condition, maxAttempts, etc.
     * @return Map with matched, attempts, durationMs, rows, row, latestRow, errorMessage
     */
    public static Map<String, Object> poll(Map<String, Object> config) {
        long startTime = System.currentTimeMillis();
        int maxAttempts = getInt(config, "maxAttempts", 5);
        long intervalMs = getLong(config, "intervalMs", 1000L);
        long timeoutMs = getLong(config, "timeoutMs", 15000L);
        Integer expectedRowCount = getIntegerOrNull(config, "expectedRowCount");
        String condition = getString(config, "condition", null);

        int attempts = 0;
        List<Map<String, Object>> rows = Collections.emptyList();
        boolean matched = false;
        String lastError = null;

        while (attempts < maxAttempts && (System.currentTimeMillis() - startTime) < timeoutMs) {
            attempts++;
            try {
                rows = query(config);
                lastError = null;

                if (expectedRowCount != null) {
                    if (rows.size() == expectedRowCount) {
                        matched = true;
                        break;
                    }
                } else if (condition != null && !condition.isBlank()) {
                    if (evaluateCondition(condition, rows)) {
                        matched = true;
                        break;
                    }
                } else if (!rows.isEmpty()) {
                    matched = true;
                    break;
                }
            } catch (Exception e) {
                lastError = e.getMessage() != null ? e.getMessage() : e.toString();
            }

            if (attempts < maxAttempts && (System.currentTimeMillis() - startTime + intervalMs) < timeoutMs) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    lastError = "DB Poll interrupted";
                    break;
                }
            }
        }

        Map<String, Object> latestRow = !rows.isEmpty() ? rows.get(0) : Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("attempts", attempts);
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("rows", rows);
        result.put("row", latestRow);
        result.put("latestRow", latestRow);
        result.put("errorMessage", lastError);
        return result;
    }

    /**
     * Executes a SELECT SQL query once and returns rows as a list of maps.
     */
    public static List<Map<String, Object>> query(Map<String, Object> config) throws Exception {
        String url = resolveToken(getString(config, "jdbcUrl", ""));
        String user = resolveToken(getString(config, "username", ""));
        String pass = resolveToken(getString(config, "password", ""));
        String query = resolveToken(getString(config, "query", ""));
        String driverClass = getString(config, "driverClassName", null);

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl is required for DB query");
        }

        ensureDriverLoaded(url, driverClass);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = getConnection(url, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String colName = md.getColumnLabel(i);
                    Object val = normalizeSqlValue(rs.getObject(i));
                    row.put(colName, val);
                    if (!colName.toLowerCase().equals(colName)) {
                        row.put(colName.toLowerCase(), val);
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Executes an INSERT / UPDATE / DELETE / DDL statement and returns affected rows count.
     */
    public static int executeUpdate(Map<String, Object> config) throws Exception {
        String url = resolveToken(getString(config, "jdbcUrl", ""));
        String user = resolveToken(getString(config, "username", ""));
        String pass = resolveToken(getString(config, "password", ""));
        String sql = resolveToken(getString(config, "sql", getString(config, "query", "")));
        String driverClass = getString(config, "driverClassName", null);

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl is required for DB execution");
        }

        ensureDriverLoaded(url, driverClass);

        try (Connection conn = getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * Dynamically loads any third-party JDBC driver JARs located in the directory or file path.
     */
    public static void loadDriversFromDir(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) return;
        File path = new File(pathStr);
        if (!path.exists()) return;

        List<File> jars = new ArrayList<>();
        if (path.isDirectory()) {
            File[] files = path.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (files != null) {
                for (File f : files) jars.add(f);
            }
        } else if (path.getName().toLowerCase().endsWith(".jar")) {
            jars.add(path);
        }

        for (File jar : jars) {
            try {
                URL[] urls = new URL[]{jar.toURI().toURL()};
                URLClassLoader loader = new URLClassLoader(urls, DbHelper.class.getClassLoader());
                ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class, loader);
                for (Driver d : loadedDrivers) {
                    DriverManager.registerDriver(new DriverShim(d));
                }
            } catch (Exception e) {
                System.err.println("Warning: failed to load driver jar " + jar.getName() + ": " + e.getMessage());
            }
        }
    }

    private static Connection getConnection(String url, String user, String pass) throws Exception {
        if (user != null && !user.isBlank()) {
            return DriverManager.getConnection(url, user, pass != null ? pass : "");
        }
        return DriverManager.getConnection(url);
    }

    public static void ensureDriverLoaded(String jdbcUrl, String explicitDriver) {
        String driver = explicitDriver;
        if (driver == null || driver.isBlank()) {
            driver = detectDriverClassName(jdbcUrl);
        }
        if (driver != null && !driver.isBlank()) {
            try {
                Class.forName(driver);
            } catch (Exception ignored) {}
        }
    }

    public static String detectDriverClassName(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        String lower = jdbcUrl.toLowerCase().trim();
        if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        } else if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (lower.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        } else if (lower.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (lower.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if (lower.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        } else if (lower.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        return null;
    }

    public static boolean evaluateCondition(String condition, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return false;
        if (condition == null || condition.isBlank()) return true;

        String condTrimmed = condition.trim();

        // 1. Fast path: single field equality e.g. "row.status == 'COMPLETED'" or "status == 'SUCCESS'"
        if (!condTrimmed.contains("&&") && !condTrimmed.contains("||") && condTrimmed.matches("^\\s*(?:row\\.)?\\w+\\s*==\\s*[^=&|]+$")) {
            String[] parts = condTrimmed.split("==", 2);
            String field = parts[0].trim();
            if (field.startsWith("row.")) field = field.substring(4);
            String expected = parts[1].trim();

            if ((expected.startsWith("'") && expected.endsWith("'")) || (expected.startsWith("\"") && expected.endsWith("\""))) {
                expected = expected.substring(1, expected.length() - 1);
            }

            Map<String, Object> r = rows.get(0);
            Object actual = r.get(field);
            if (actual == null) actual = r.get(field.toLowerCase());
            return String.valueOf(actual).equals(expected);
        }

        // 2. JavaScript expression fallback
        if (SCRIPT_ENGINE != null) {
            try {
                SCRIPT_ENGINE.put("rows", rows);
                SCRIPT_ENGINE.put("row", rows.get(0));
                SCRIPT_ENGINE.put("r", rows.get(0));
                Object res = SCRIPT_ENGINE.eval(condTrimmed);
                if (res instanceof Boolean b) return b;
                return Boolean.parseBoolean(String.valueOf(res));
            } catch (Exception ignored) {}
        }

        return false;
    }

    public static Object normalizeSqlValue(Object val) {
        if (val == null) return null;
        if (val instanceof String || val instanceof Number || val instanceof Boolean) {
            return val;
        }
        if (val instanceof TemporalAccessor) {
            return val.toString();
        }
        if (val instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (val instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (val instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (val instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) Math.min(clob.length(), 1_000_000L));
            } catch (Exception ignored) {}
        }
        if (val instanceof java.sql.Blob blob) {
            try {
                return Base64.getEncoder().encodeToString(blob.getBytes(1, (int) Math.min(blob.length(), 1_000_000L)));
            } catch (Exception ignored) {}
        }
        if (val instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (val instanceof java.sql.Array arr) {
            try {
                Object a = arr.getArray();
                if (a instanceof Object[] oArr) {
                    List<Object> list = new ArrayList<>(oArr.length);
                    for (Object item : oArr) list.add(normalizeSqlValue(item));
                    return list;
                }
            } catch (Exception ignored) {}
        }
        return val.toString();
    }

    public static String resolveToken(String str) {
        if (str == null || !str.contains("${")) return str;
        Matcher m = TOKEN_PATTERN.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            String val = null;
            if (key.startsWith("$env:") || key.startsWith("env:")) {
                String k = key.substring(key.indexOf(':') + 1).trim();
                val = System.getenv(k);
                if (val == null) val = System.getProperty(k);
            } else if (key.startsWith("$secret:") || key.startsWith("secret:")) {
                String k = key.substring(key.indexOf(':') + 1).trim();
                val = System.getenv(k);
                if (val == null) val = System.getProperty(k);
            } else {
                val = System.getProperty(key);
                if (val == null) val = System.getenv(key);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    private static Integer getIntegerOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (Exception ignored) {}
        }
        return null;
    }

    private static long getLong(Map<String, Object> map, String key, long defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
            try { return Long.parseLong(v.toString()); } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    /**
     * Driver wrapper to register drivers loaded through custom ClassLoaders.
     */
    private static class DriverShim implements Driver {
        private final Driver driver;
        public DriverShim(Driver d) { this.driver = d; }
        public boolean acceptsURL(String u) throws java.sql.SQLException { return this.driver.acceptsURL(u); }
        public Connection connect(String u, java.util.Properties p) throws java.sql.SQLException { return this.driver.connect(u, p); }
        public int getMajorVersion() { return this.driver.getMajorVersion(); }
        public int getMinorVersion() { return this.driver.getMinorVersion(); }
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, java.util.Properties p) throws java.sql.SQLException { return this.driver.getPropertyInfo(u, p); }
        public boolean jdbcCompliant() { return this.driver.jdbcCompliant(); }
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException { return this.driver.getParentLogger(); }
    }
}
