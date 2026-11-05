package com.orchestrator.platform.web;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.service.TestWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "Workflows", description = "Test workflow visual canvas definitions and AST persistence")
public class WorkflowController {

    private final TestWorkflowService workflowService;

    public WorkflowController(TestWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    @Operation(summary = "List all workflows", description = "Returns all test workflows sorted by last update")
    public ResponseEntity<List<TestWorkflowEntity>> getAllWorkflows() {
        return ResponseEntity.ok(workflowService.getAllWorkflows());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workflow by ID", description = "Fetches a test workflow including canvas state and AST")
    public ResponseEntity<TestWorkflowEntity> getWorkflowById(@PathVariable("id") UUID id) {
        return workflowService.getWorkflowById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create test workflow", description = "Persists new visual flow canvas state and derived AST")
    public ResponseEntity<TestWorkflowEntity> createWorkflow(@RequestBody WorkflowCreateRequest request) {
        TestWorkflowEntity created = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update test workflow", description = "Updates visual flow canvas state and recompiles AST")
    public ResponseEntity<TestWorkflowEntity> updateWorkflow(
            @PathVariable("id") UUID id,
            @RequestBody WorkflowCreateRequest request) {
        return ResponseEntity.ok(workflowService.updateWorkflow(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete test workflow", description = "Removes a test workflow")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable("id") UUID id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/export/feature", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "Export as Karate .feature file", description = "Generates standalone Gherkin Karate feature file for CI/CD")
    public ResponseEntity<byte[]> exportAsFeature(@PathVariable("id") UUID id) {
        String featureContent = workflowService.generateStandaloneKarateFeature(id);
        TestWorkflowEntity wf = workflowService.getWorkflowById(id).orElse(null);
        String filename = (wf != null && wf.getName() != null)
                ? wf.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".feature"
                : "workflow.feature";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(featureContent.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/{id}/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export workflow as JSON", description = "Exports full workflow entity with canvas state and AST")
    public ResponseEntity<TestWorkflowEntity> exportAsJson(@PathVariable("id") UUID id) {
        TestWorkflowEntity wf = workflowService.getWorkflowById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + id));
        String filename = (wf.getName() != null)
                ? wf.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json"
                : "workflow.json";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(wf);
    }

    @PostMapping("/import")
    @Operation(summary = "Import workflow JSON", description = "Restores a workflow from JSON definition as a new workflow")
    public ResponseEntity<TestWorkflowEntity> importWorkflow(@RequestBody WorkflowCreateRequest request) {
        TestWorkflowEntity imported = workflowService.importWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }
}
