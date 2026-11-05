package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.config.FlywayConfig;
import com.orchestrator.platform.domain.enums.StepType;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasNode;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.sqlscript.SqlStatement;
import org.flywaydb.core.internal.sqlscript.SqlStatementIterator;
import org.flywaydb.database.postgresql.PostgreSQLParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class FlywayReferenceWorkflowsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MIGRATIONS_DIR = "src/main/resources/db/migration";

    private static final Map<String, String> EXPECTED_WORKFLOW_IDS = Map.of(
            "V2", "10000000-0000-0000-0000-000000000001",
            "V3", "10000000-0000-0000-0000-000000000002",
            "V4", "10000000-0000-0000-0000-000000000003",
            "V5", "10000000-0000-0000-0000-000000000004",
            "V6", "10000000-0000-0000-0000-000000000005",
            "V7", "10000000-0000-0000-0000-000000000006",
            "V8", "10000000-0000-0000-0000-000000000007",
            "V9", "10000000-0000-0000-0000-000000000008"
    );

    @Test
    @DisplayName("Verify that all Flyway migration scripts from V1 to V9 exist")
    public void testMigrationFilesExist() {
        File dir = new File(MIGRATIONS_DIR);
        assertTrue(dir.exists() && dir.isDirectory(), "Migrations directory must exist: " + MIGRATIONS_DIR);

        String[] expectedPrefixes = {"V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9"};
        File[] files = dir.listFiles((d, name) -> name.endsWith(".sql"));
        assertNotNull(files);

        for (String prefix : expectedPrefixes) {
            boolean found = Arrays.stream(files).anyMatch(f -> f.getName().startsWith(prefix + "__"));
            assertTrue(found, "Expected Flyway migration file starting with " + prefix + "__ in " + MIGRATIONS_DIR);
        }
    }

    @Test
    @DisplayName("Verify that all reference workflow migrations (V2 to V9) parse into valid CanvasState and WorkflowAst models")
    public void testParseAllReferenceWorkflowMigrations() throws IOException {
        File dir = new File(MIGRATIONS_DIR);
        Set<StepType> discoveredStepTypes = new HashSet<>();

        for (int v = 2; v <= 9; v++) {
            String prefix = "V" + v + "__";
            File[] matches = dir.listFiles((d, name) -> name.startsWith(prefix));
            assertNotNull(matches);
            assertEquals(1, matches.length, "Exactly one migration file expected for " + prefix);

            File migrationFile = matches[0];
            String sql = Files.readString(migrationFile.toPath());

            assertTrue(sql.contains("INSERT INTO test_workflows"), "Must contain INSERT INTO test_workflows");
            assertTrue(sql.contains("ON CONFLICT (id) DO NOTHING;"), "Must be idempotent with ON CONFLICT DO NOTHING");

            // Verify expected UUID
            String expectedId = EXPECTED_WORKFLOW_IDS.get("V" + v);
            assertNotNull(expectedId);
            assertTrue(sql.contains(expectedId), "Migration " + prefix + " must contain fixed UUID: " + expectedId);

            // Extract canvas_state and workflow_ast JSON blocks
            ParsedWorkflowSql parsed = parseWorkflowSql(sql);
            assertNotNull(parsed.name, "Workflow name should not be null in " + migrationFile.getName());
            assertTrue(parsed.name.startsWith("[Reference]"), "Workflow name must have [Reference] prefix: " + parsed.name);
            assertNotNull(parsed.description, "Workflow description should not be null in " + migrationFile.getName());

            // Validate CanvasState deserialization
            CanvasState canvasState = objectMapper.readValue(parsed.canvasStateJson, CanvasState.class);
            assertNotNull(canvasState, "CanvasState should deserialize cleanly");
            assertNotNull(canvasState.getNodes(), "Canvas nodes list must not be null");
            assertFalse(canvasState.getNodes().isEmpty(), "Canvas nodes must not be empty");
            for (CanvasNode node : canvasState.getNodes()) {
                assertNotNull(node.getId(), "Node ID must not be null");
                assertNotNull(node.getType(), "Node type must not be null");
                assertNotNull(node.getPosition(), "Node position must not be null");
                assertNotNull(node.getData(), "Node data must not be null");
            }

            // Validate WorkflowAst deserialization
            WorkflowAst ast = objectMapper.readValue(parsed.workflowAstJson, WorkflowAst.class);
            assertNotNull(ast, "WorkflowAst should deserialize cleanly");
            assertNotNull(ast.getSteps(), "AST steps list must not be null");
            assertEquals(canvasState.getNodes().size(), ast.getSteps().size(),
                    "AST step count must match Canvas node count in " + migrationFile.getName());

            for (WorkflowStep step : ast.getSteps()) {
                assertNotNull(step.getId(), "Step ID must not be null");
                assertNotNull(step.getName(), "Step name must not be null");
                assertNotNull(step.getType(), "Step type must not be null");
                discoveredStepTypes.add(step.getType());
            }

            // Scenario specific assertions
            if (v == 3) {
                // Database SQL Polling
                WorkflowStep dbStep = ast.getSteps().stream()
                        .filter(s -> s.getType() == StepType.DB_POLL)
                        .findFirst()
                        .orElseThrow();
                assertNotNull(dbStep.getDbPollConfig());
                assertEquals(5, dbStep.getDbPollConfig().getMaxAttempts());
                assertEquals(1000L, dbStep.getDbPollConfig().getPollIntervalMs());
                assertFalse(dbStep.getExtractions().isEmpty());
            } else if (v == 4) {
                // Kafka Streaming Assertion
                WorkflowStep kafkaStep = ast.getSteps().stream()
                        .filter(s -> s.getType() == StepType.KAFKA_ASSERT)
                        .findFirst()
                        .orElseThrow();
                assertNotNull(kafkaStep.getKafkaConfig());
                assertEquals("order-events", kafkaStep.getKafkaConfig().getTopic());
            } else if (v == 5) {
                // JavaScript Transform Scripting
                WorkflowStep transStep = ast.getSteps().stream()
                        .filter(s -> s.getType() == StepType.TRANSFORM)
                        .findFirst()
                        .orElseThrow();
                assertNotNull(transStep.getTransformConfig());
                assertTrue(transStep.getTransformConfig().getScript().contains("function(ctx)"));
            } else if (v == 6) {
                // Multi-Environment Scoped Properties & Secrets
                assertNotNull(ast.getEnvProperties());
                assertTrue(ast.getEnvProperties().containsKey("default"));
                assertTrue(ast.getEnvProperties().containsKey("staging"));
                assertTrue(ast.getEnvProperties().containsKey("production"));
                assertNotNull(ast.getEnvSecrets());
                assertTrue(ast.getEnvSecrets().containsKey("default"));
                assertTrue(ast.getEnvSecrets().containsKey("staging"));
                assertTrue(ast.getEnvSecrets().containsKey("production"));
            } else if (v == 7) {
                // Reusable Child Sub-Workflow
                assertEquals("10000000-0000-0000-0000-000000000006", expectedId);
            } else if (v == 8) {
                // Master Sub-Workflow Composition
                WorkflowStep subStep = ast.getSteps().stream()
                        .filter(s -> s.getType() == StepType.SUB_WORKFLOW)
                        .findFirst()
                        .orElseThrow();
                assertNotNull(subStep.getSubWorkflowConfig());
                assertEquals("10000000-0000-0000-0000-000000000006", subStep.getSubWorkflowConfig().getWorkflowId(),
                        "Master composition subworkflow must reference V7 child workflow ID");
                assertTrue(subStep.getSubWorkflowConfig().isInheritParentContext());
                assertTrue(subStep.getSubWorkflowConfig().isInheritEnvAndSecrets());
                assertTrue(subStep.getSubWorkflowConfig().isInheritFlowHeaders());
            } else if (v == 9) {
                // Async Polling & Resilience
                WorkflowStep delayStep = ast.getSteps().stream()
                        .filter(s -> s.getType() == StepType.DELAY)
                        .findFirst()
                        .orElseThrow();
                assertEquals(2000L, delayStep.getDelayMs());

                WorkflowStep cleanupStep = ast.getSteps().stream()
                        .filter(s -> s.getId().equals("step_auxiliary_cleanup"))
                        .findFirst()
                        .orElseThrow();
                assertTrue(cleanupStep.isContinueOnFailure());
                assertTrue(cleanupStep.isExcludeFromReport());
            }
        }

        // Verify that all core engine step types are represented across the reference scenarios
        assertTrue(discoveredStepTypes.contains(StepType.HTTP_REQUEST), "HTTP_REQUEST must be represented");
        assertTrue(discoveredStepTypes.contains(StepType.DB_POLL), "DB_POLL must be represented");
        assertTrue(discoveredStepTypes.contains(StepType.KAFKA_ASSERT), "KAFKA_ASSERT must be represented");
        assertTrue(discoveredStepTypes.contains(StepType.TRANSFORM), "TRANSFORM must be represented");
        assertTrue(discoveredStepTypes.contains(StepType.DELAY), "DELAY must be represented");
        assertTrue(discoveredStepTypes.contains(StepType.SUB_WORKFLOW), "SUB_WORKFLOW must be represented");
    }

    private static class ParsedWorkflowSql {
        String id;
        String name;
        String description;
        String canvasStateJson;
        String workflowAstJson;
    }

    private ParsedWorkflowSql parseWorkflowSql(String sql) {
        ParsedWorkflowSql parsed = new ParsedWorkflowSql();

        // Match Name: '[Reference] ...'
        Pattern namePattern = Pattern.compile("'\\[Reference\\][^']+'");
        Matcher nameMatcher = namePattern.matcher(sql);
        if (nameMatcher.find()) {
            String raw = nameMatcher.group();
            parsed.name = raw.substring(1, raw.length() - 1);
        }

        // Match Description
        Pattern descPattern = Pattern.compile("1,\\s*'ACTIVE',");
        Matcher descMatcher = descPattern.matcher(sql);
        if (descMatcher.find()) {
            int activeIdx = descMatcher.start();
            // Look backwards for description string
            int prevEnd = sql.lastIndexOf("'", activeIdx - 1);
            int prevStart = sql.lastIndexOf("'", prevEnd - 1);
            if (prevStart != -1 && prevEnd != -1) {
                parsed.description = sql.substring(prevStart + 1, prevEnd);
            }
        }

        // Extract JSON blocks marked with '::jsonb'
        Pattern jsonbPattern = Pattern.compile("'(\\{[\\s\\S]*?\\})'::jsonb");
        Matcher jsonbMatcher = jsonbPattern.matcher(sql);

        if (jsonbMatcher.find()) {
            // First JSON block is canvas_state
            parsed.canvasStateJson = unescapeSqlString(jsonbMatcher.group(1));
        }
        if (jsonbMatcher.find()) {
            // Second JSON block is workflow_ast
            parsed.workflowAstJson = unescapeSqlString(jsonbMatcher.group(1));
        }

        return parsed;
    }

    private String unescapeSqlString(String sqlString) {
        // In SQL strings, '' represents an escaped single quote '
        return sqlString.replace("''", "'");
    }

    @Test
    @DisplayName("Verify that Flyway placeholder replacement causes error on V2 if not disabled")
    public void testFlywayPlaceholderReplacementFailsWithoutDisablement() {
        File v2File = new File(MIGRATIONS_DIR, "V2__seed_reference_rest_api_chaining_workflow.sql");
        assertTrue(v2File.exists(), "V2 migration file must exist");

        FluentConfiguration config = Flyway.configure().placeholderReplacement(true);
        PostgreSQLParser parser = new PostgreSQLParser(config, new ParsingContext());
        LoadableResource resource = createLoadableResource(v2File);

        FlywayException ex = assertThrows(FlywayException.class, () -> {
            SqlStatementIterator iterator = parser.parse(resource);
            while (iterator.hasNext()) {
                iterator.next();
            }
        }, "Default Flyway with placeholder replacement enabled should fail on dynamic workflow expressions like ${authToken}");

        assertTrue(ex.getMessage().contains("${authToken}"),
                "Exception message must mention missing placeholder ${authToken}, was: " + ex.getMessage());
    }

    @Test
    @DisplayName("Verify that all Flyway migrations (V1 to V9) parse cleanly when placeholder replacement is disabled")
    public void testFlywayPlaceholderReplacementDisabledParsesAllMigrationsSuccessfully() {
        File dir = new File(MIGRATIONS_DIR);
        File[] migrationFiles = dir.listFiles((d, name) -> name.endsWith(".sql"));
        assertNotNull(migrationFiles);
        Arrays.sort(migrationFiles, Comparator.comparing(File::getName));
        assertTrue(migrationFiles.length >= 9, "Expected at least 9 migration files");

        FluentConfiguration config = Flyway.configure().placeholderReplacement(false);
        assertFalse(config.isPlaceholderReplacement(), "Placeholder replacement must be disabled");

        PostgreSQLParser parser = new PostgreSQLParser(config, new ParsingContext());

        for (File file : migrationFiles) {
            LoadableResource resource = createLoadableResource(file);
            SqlStatementIterator iterator = parser.parse(resource);
            int statementCount = 0;
            while (iterator.hasNext()) {
                SqlStatement stmt = iterator.next();
                assertNotNull(stmt.getSql(), "Parsed statement SQL must not be null in " + file.getName());
                statementCount++;
            }
            assertTrue(statementCount > 0, "Migration file " + file.getName() + " must produce at least 1 parsed SQL statement");
        }
    }

    @Test
    @DisplayName("Verify that FlywayConfig customizer properly disables placeholder replacement")
    public void testFlywayConfigBeanDisablesPlaceholderReplacement() {
        FlywayConfig flywayConfig = new FlywayConfig();
        FluentConfiguration config = Flyway.configure();
        assertTrue(config.isPlaceholderReplacement(), "Default FluentConfiguration should have placeholder replacement enabled");

        flywayConfig.flywayConfigurationCustomizer().customize(config);
        assertFalse(config.isPlaceholderReplacement(), "Customizer must set placeholder replacement to false");
    }

    private LoadableResource createLoadableResource(File file) {
        return new LoadableResource() {
            @Override
            public java.io.Reader read() {
                try {
                    return Files.newBufferedReader(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read " + file.getAbsolutePath(), e);
                }
            }

            @Override
            public String getAbsolutePath() {
                return file.getAbsolutePath();
            }

            @Override
            public String getAbsolutePathOnDisk() {
                return file.getAbsolutePath();
            }

            @Override
            public String getFilename() {
                return file.getName();
            }

            @Override
            public String getRelativePath() {
                return file.getName();
            }
        };
    }
}
