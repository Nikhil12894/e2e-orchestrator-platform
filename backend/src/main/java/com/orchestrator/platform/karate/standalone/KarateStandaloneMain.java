package com.orchestrator.platform.karate.standalone;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.orchestrator.platform.karate.DbHelper;

/**
 * Custom Standalone CLI Runner for Karate with universal database support.
 *
 * Usage:
 *   java -jar meterflow-karate-standalone.jar -f workflow.feature -e staging
 *   java -jar meterflow-karate-standalone.jar --driver-dir=./drivers -f workflow.feature
 */
public class KarateStandaloneMain {

    public static void main(String[] args) {
        String driverDir = System.getProperty("meterflow.driver.dir", System.getenv("METERFLOW_DRIVER_DIR"));
        List<String> karateArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--driver-dir=")) {
                driverDir = arg.substring("--driver-dir=".length()).trim();
            } else if (arg.equals("--driver-dir") || arg.equals("-d")) {
                if (i + 1 < args.length) {
                    driverDir = args[++i].trim();
                }
            } else if (arg.equals("--help") || arg.equals("-h")) {
                printHelp();
                karateArgs.add(arg);
            } else {
                karateArgs.add(arg);
            }
        }

        // Auto-scan default "./drivers" folder if present and not explicitly set
        if (driverDir == null || driverDir.isBlank()) {
            File defaultDir = new File("drivers");
            if (defaultDir.exists() && defaultDir.isDirectory()) {
                driverDir = defaultDir.getAbsolutePath();
            }
        }

        if (driverDir != null && !driverDir.isBlank()) {
            System.out.println("[MeterFlow Karate] Loading external JDBC drivers from: " + driverDir);
            DbHelper.loadDriversFromDir(driverDir);
        }

        if (karateArgs.isEmpty()) {
            printHelp();
            System.exit(0);
        }

        // Delegate execution to Karate's official CLI Runner
        com.intuit.karate.Main.main(karateArgs.toArray(new String[0]));
    }

    private static void printHelp() {
        System.out.println("""
            ========================================================================
            MeterFlow Standalone Karate Runner with Universal Database Support
            Karate Core Version: 2.1.4
            ========================================================================
            Bundled JDBC Drivers:
              - PostgreSQL: org.postgresql.Driver
              - MySQL:      com.mysql.cj.jdbc.Driver
              - MariaDB:    org.mariadb.jdbc.Driver
              - Oracle:     oracle.jdbc.OracleDriver
              - SQL Server: com.microsoft.sqlserver.jdbc.SQLServerDriver
              - SQLite:     org.sqlite.JDBC
              - H2:         org.h2.Driver
            
            Options:
              --driver-dir, -d <path>   Directory with extra third-party driver JARs
              -f, --features <path>     Feature file or directory to execute
              -e, --env <env>           Karate environment name (default: default)
              -t, --tags <tags>         Cucumber tag expression (e.g. '@e2e-workflow')
              -o, --output <dir>        Output directory for reports (default: target/karate-reports)
              -h, --help                Show this help message
            
            Karate Feature Database Polling Example:
              * def Db = Java.type('com.orchestrator.platform.karate.DbHelper')
              * def res = Db.poll({
                  jdbcUrl: 'jdbc:postgresql://localhost:5432/testdb',
                  username: 'postgres',
                  password: 'password',
                  query: 'SELECT status FROM orders WHERE id = 123',
                  condition: 'row.status == "COMPLETED"'
                })
              * match res.matched == true
            ========================================================================
            """);
    }
}
