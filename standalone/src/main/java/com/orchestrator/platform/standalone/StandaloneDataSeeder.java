package com.orchestrator.platform.standalone;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.ast.WorkflowStep;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import com.orchestrator.platform.repository.ApiEndpointRepository;
import com.orchestrator.platform.repository.TestWorkflowRepository;
import com.orchestrator.platform.web.ApiCatalogController;

@Component
public class StandaloneDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StandaloneDataSeeder.class);

    private final TestWorkflowRepository workflowRepository;
    private final ApiEndpointRepository catalogRepository;
    private final ApiCatalogController catalogController;
    private final ObjectMapper objectMapper;

    public StandaloneDataSeeder(TestWorkflowRepository workflowRepository,
                                ApiEndpointRepository catalogRepository,
                                ApiCatalogController catalogController,
                                ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.catalogRepository = catalogRepository;
        this.catalogController = catalogController;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        seedCatalogIfEmpty();
        seedWorkflowsIfEmpty();
    }

    private void seedCatalogIfEmpty() {
        if (catalogRepository.count() == 0) {
            try {
                catalogController.seedSampleServices();
                log.info("[MeterFlow Standalone] Seeded sample microservice catalog endpoints.");
            } catch (Exception e) {
                log.warn("[MeterFlow Standalone] Failed to seed sample catalog: {}", e.getMessage());
            }
        }
    }

    private void seedWorkflowsIfEmpty() {
        boolean hasReferenceWorkflows = workflowRepository.findAll().stream()
                .anyMatch(w -> w.getName() != null && w.getName().contains("[Reference]"));

        if (hasReferenceWorkflows) {
            log.info("[MeterFlow Standalone] Database already contains reference workflows. Preserving user state.");
            return;
        }

        log.info("[MeterFlow Standalone] Initializing reference workflows into H2 database...");
        int seededCount = 0;

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] allResources = resolver.getResources("classpath*:db/migration/*.sql");

            List<Resource> resources = new ArrayList<>();
            for (Resource r : allResources) {
                String fname = r.getFilename() != null ? r.getFilename() : "";
                if (fname.startsWith("V") && !fname.startsWith("V1_")) {
                    resources.add(r);
                }
            }

            // Sort resources to preserve V2 -> V9 order
            resources.sort((a, b) -> {
                String nameA = a.getFilename() != null ? a.getFilename() : "";
                String nameB = b.getFilename() != null ? b.getFilename() : "";
                return nameA.compareTo(nameB);
            });

            Map<String, UUID> oldToNewIdMap = new HashMap<>();

            for (Resource res : resources) {
                try (InputStream is = res.getInputStream()) {
                    String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    String originalUuid = extractOriginalUuid(sql);

                    TestWorkflowEntity entity = parseWorkflowFromSql(sql);
                    if (entity != null) {
                        // Remap subworkflow references if V8 refers to V7
                        remapSubworkflowIds(entity, oldToNewIdMap);

                        // Null ID allows JPA to persist cleanly as new entity
                        entity.setId(null);
                        TestWorkflowEntity saved = workflowRepository.save(entity);

                        if (originalUuid != null) {
                            oldToNewIdMap.put(originalUuid, saved.getId());
                        }
                        seededCount++;
                    }
                } catch (Exception ex) {
                    log.warn("[MeterFlow Standalone] Could not parse reference workflow from {}: {}",
                            res.getFilename(), ex.getMessage());
                }
            }

            log.info("[MeterFlow Standalone] Successfully seeded {} reference workflows into H2 database.", seededCount);
        } catch (Exception e) {
            log.warn("[MeterFlow Standalone] Error discovering reference workflow migrations: {}", e.getMessage());
        }
    }

    private String extractOriginalUuid(String sql) {
        Pattern uuidPattern = Pattern.compile("'([0-9a-fA-F-]{36})'::uuid");
        Matcher uuidMatcher = uuidPattern.matcher(sql);
        return uuidMatcher.find() ? uuidMatcher.group(1) : null;
    }

    private void remapSubworkflowIds(TestWorkflowEntity entity, Map<String, UUID> idMap) {
        if (entity.getWorkflowAst() != null && entity.getWorkflowAst().getSteps() != null) {
            for (WorkflowStep step : entity.getWorkflowAst().getSteps()) {
                if (step.getSubWorkflowConfig() != null && step.getSubWorkflowConfig().getWorkflowId() != null) {
                    String oldId = step.getSubWorkflowConfig().getWorkflowId().trim();
                    if (idMap.containsKey(oldId)) {
                        step.getSubWorkflowConfig().setWorkflowId(idMap.get(oldId).toString());
                    }
                }
            }
        }
    }

    private TestWorkflowEntity parseWorkflowFromSql(String sql) {
        try {
            TestWorkflowEntity entity = new TestWorkflowEntity();

            // Extract Name: '[Reference] ...'
            Pattern namePattern = Pattern.compile("'\\[Reference\\][^']+'");
            Matcher nameMatcher = namePattern.matcher(sql);
            if (nameMatcher.find()) {
                String raw = nameMatcher.group();
                entity.setName(raw.substring(1, raw.length() - 1));
            } else {
                entity.setName("Reference Workflow");
            }

            // Extract Description
            Pattern descPattern = Pattern.compile("1,\\s*'ACTIVE',");
            Matcher descMatcher = descPattern.matcher(sql);
            if (descMatcher.find()) {
                int activeIdx = descMatcher.start();
                int prevEnd = sql.lastIndexOf("'", activeIdx - 1);
                int prevStart = sql.lastIndexOf("'", prevEnd - 1);
                if (prevStart != -1 && prevEnd != -1) {
                    entity.setDescription(sql.substring(prevStart + 1, prevEnd));
                }
            }
            if (entity.getDescription() == null) {
                entity.setDescription("Seeded reference workflow");
            }

            entity.setVersion(1);
            entity.setStatus("ACTIVE");

            // Extract JSON blocks marked with '::jsonb'
            Pattern jsonbPattern = Pattern.compile("'(\\{[\\s\\S]*?\\})'::jsonb");
            Matcher jsonbMatcher = jsonbPattern.matcher(sql);

            if (jsonbMatcher.find()) {
                String canvasJson = jsonbMatcher.group(1).replace("''", "'");
                entity.setCanvasState(objectMapper.readValue(canvasJson, CanvasState.class));
            }

            if (jsonbMatcher.find()) {
                String astJson = jsonbMatcher.group(1).replace("''", "'");
                entity.setWorkflowAst(objectMapper.readValue(astJson, WorkflowAst.class));
            }

            Instant now = Instant.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);

            return entity;
        } catch (Exception e) {
            log.error("[MeterFlow Standalone] Failed to parse workflow SQL: {}", e.getMessage());
            return null;
        }
    }
}
