package com.orchestrator.platform.web;

import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.model.dto.WorkflowCreateRequest;
import com.orchestrator.platform.service.TestWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
