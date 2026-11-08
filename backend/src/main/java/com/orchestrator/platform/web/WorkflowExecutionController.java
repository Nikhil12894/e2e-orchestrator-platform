package com.orchestrator.platform.web;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.model.dto.ExecutionSyncResult;
import com.orchestrator.platform.domain.model.dto.WorkflowExecutionRequest;
import com.orchestrator.platform.service.TestWorkflowExecutionService;
import com.orchestrator.platform.service.execution.DynamicKarateRunner;
import com.orchestrator.platform.service.execution.ExecutionLogBroadcaster;
import com.orchestrator.platform.service.execution.ExecutionNavigationUrlBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Workflow Executions", description = "Asynchronous workflow test runs, live log streams, and Karate HTML reports")
public class WorkflowExecutionController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionController.class);

    private final TestWorkflowExecutionService executionService;
    private final ExecutionLogBroadcaster logBroadcaster;
    private final DynamicKarateRunner karateRunner;
    private final ExecutionNavigationUrlBuilder urlBuilder;

    @Value("${orchestrator.execution.max-wait-timeout-sec:300}")
    private int maxWaitTimeoutSec = 300;

    public WorkflowExecutionController(TestWorkflowExecutionService executionService,
                                       ExecutionLogBroadcaster logBroadcaster,
                                       DynamicKarateRunner karateRunner,
                                       ExecutionNavigationUrlBuilder urlBuilder) {
        this.executionService = executionService;
        this.logBroadcaster = logBroadcaster;
        this.karateRunner = karateRunner;
        this.urlBuilder = urlBuilder;
    }

    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "Trigger workflow execution",
            description = "Triggers workflow test execution. With ?wait=true, blocks up to timeoutSec returning HTTP 200 upon completion, or HTTP 504 (or 202 if timeoutStatus=202) on timeout. Without ?wait=true, returns HTTP 202 immediately.")
    public ResponseEntity<WorkflowExecutionEntity> executeWorkflow(
            @PathVariable("workflowId") UUID workflowId,
            @RequestParam(value = "wait", required = false, defaultValue = "false") boolean wait,
            @RequestParam(value = "timeoutSec", required = false, defaultValue = "60") int timeoutSec,
            @RequestParam(value = "timeoutStatus", required = false, defaultValue = "504") int timeoutStatus,
            @RequestBody(required = false) WorkflowExecutionRequest request,
            HttpServletRequest httpRequest) {

        if (request == null) {
            request = new WorkflowExecutionRequest();
        }

        if (!wait) {
            WorkflowExecutionEntity execution = executionService.triggerExecution(workflowId, request);
            urlBuilder.enrichNavigationUrls(execution, httpRequest);

            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.ACCEPTED);
            if (execution != null && execution.getStatusUrl() != null) {
                responseBuilder.location(URI.create(execution.getStatusUrl()));
            }
            return responseBuilder.body(execution);
        }

        // Defensive parameter clamping against connection starvation / Slowloris (T-04-01)
        int clampedTimeout = Math.max(1, Math.min(timeoutSec, maxWaitTimeoutSec));

        ExecutionSyncResult syncResult = executionService.triggerExecutionSync(workflowId, request, clampedTimeout);
        WorkflowExecutionEntity execution = syncResult.execution();
        urlBuilder.enrichNavigationUrls(execution, httpRequest);

        if (syncResult.timedOut()) {
            HttpStatus status = (timeoutStatus == 202) ? HttpStatus.ACCEPTED : HttpStatus.GATEWAY_TIMEOUT;
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status)
                    .header("X-Execution-Timed-Out", "true");
            if (execution != null && execution.getStatusUrl() != null) {
                responseBuilder.location(URI.create(execution.getStatusUrl()));
            }
            return responseBuilder.body(execution);
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (execution != null && execution.getStatusUrl() != null) {
            responseBuilder.location(URI.create(execution.getStatusUrl()));
        }
        return responseBuilder.body(execution);
    }

    @GetMapping("/executions")
    @Operation(summary = "List all executions", description = "Returns execution history across all workflows")
    public ResponseEntity<List<WorkflowExecutionEntity>> getAllExecutions(
            @RequestParam(value = "workflowId", required = false) UUID workflowId,
            HttpServletRequest httpRequest) {
        List<WorkflowExecutionEntity> executions = (workflowId != null)
                ? executionService.getExecutionsForWorkflow(workflowId)
                : executionService.getAllExecutions();
        executions.forEach(e -> urlBuilder.enrichNavigationUrls(e, httpRequest));
        return ResponseEntity.ok(executions);
    }

    @GetMapping("/executions/{id}")
    @Operation(summary = "Get execution details", description = "Fetches status, context snapshot, step evaluations, and report links")
    public ResponseEntity<WorkflowExecutionEntity> getExecutionById(
            @PathVariable("id") UUID id,
            HttpServletRequest httpRequest) {
        return executionService.getExecutionById(id)
                .map(execution -> {
                    urlBuilder.enrichNavigationUrls(execution, httpRequest);
                    return ResponseEntity.ok(execution);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/executions/{id}/cancel")
    @Operation(summary = "Cancel execution", description = "Aborts a running virtual thread execution and updates its status to CANCELLED")
    public ResponseEntity<WorkflowExecutionEntity> cancelExecution(
            @PathVariable("id") UUID id,
            HttpServletRequest httpRequest) {
        WorkflowExecutionEntity cancelled = executionService.cancelExecution(id);
        urlBuilder.enrichNavigationUrls(cancelled, httpRequest);
        return ResponseEntity.ok(cancelled);
    }

    @DeleteMapping("/executions/{id}")
    @Operation(summary = "Delete execution run", description = "Permanently deletes execution database record and purges report files from disk")
    public ResponseEntity<Void> deleteExecution(@PathVariable("id") UUID id) {
        executionService.deleteExecution(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/workflows/{workflowId}/executions")
    @Operation(summary = "Clear workflow execution history", description = "Permanently deletes all execution database records and report files for a workflow")
    public ResponseEntity<Void> clearWorkflowExecutions(@PathVariable("workflowId") UUID workflowId) {
        executionService.deleteExecutionsForWorkflow(workflowId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/executions")
    @Operation(summary = "Delete executions by query", description = "Deletes executions filtered by workflowId")
    public ResponseEntity<Void> deleteExecutionsByFilter(@RequestParam("workflowId") UUID workflowId) {
        executionService.deleteExecutionsForWorkflow(workflowId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/executions/purge")
    @Operation(summary = "Purge execution history", description = "Bulk prunes old executions and frees disk storage across workflows")
    public ResponseEntity<Map<String, Object>> purgeExecutions(
            @RequestParam(value = "keepLatest", required = false, defaultValue = "30") Integer keepLatest,
            @RequestParam(value = "olderThanDays", required = false) Integer olderThanDays) {
        int purged = executionService.purgeExecutions(keepLatest, olderThanDays);
        return ResponseEntity.ok(Map.of("message", "Execution history purged successfully", "purgedCount", purged));
    }

    @GetMapping(value = "/executions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream execution events (SSE)", description = "Real-time Server-Sent Events stream for execution progress and step logs")
    public SseEmitter streamExecutionLogs(@PathVariable("id") UUID id) {
        return logBroadcaster.registerSseEmitter(id);
    }

    @GetMapping("/executions/{id}/report/**")
    @Operation(summary = "Serve Karate HTML Report", description = "Serves isolated per-run Karate HTML report, javascript, stylesheets, and assets")
    public ResponseEntity<Resource> serveReportAsset(
            @PathVariable("id") UUID id,
            HttpServletRequest request) {

        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/api/v1/executions/" + id + "/report/";
        String relativeFile = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());

        if (relativeFile.isEmpty() || relativeFile.equals("/")) {
            relativeFile = "workflow-report.html";
        }

        // Prevent path traversal
        if (relativeFile.contains("..")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Path reportDir = karateRunner.getReportDir(id);
        Path targetFile = reportDir.resolve(relativeFile).normalize();

        if (!targetFile.startsWith(reportDir) || !Files.exists(targetFile)) {
            // Check for fallback files (e.g. karate-summary.html or workflow-report.html)
            Path fallbackSummary = reportDir.resolve("karate-summary.html");
            Path fallbackWorkflow = reportDir.resolve("workflow-report.html");
            if (Files.exists(fallbackWorkflow)) {
                targetFile = fallbackWorkflow;
            } else if (Files.exists(fallbackSummary)) {
                targetFile = fallbackSummary;
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }

        File file = targetFile.toFile();
        Resource resource = new FileSystemResource(file);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".html")) {
            mediaType = MediaType.TEXT_HTML;
        } else if (name.endsWith(".css")) {
            mediaType = MediaType.valueOf("text/css");
        } else if (name.endsWith(".js")) {
            mediaType = MediaType.valueOf("application/javascript");
        } else if (name.endsWith(".json")) {
            mediaType = MediaType.APPLICATION_JSON;
        } else if (name.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (name.endsWith(".svg")) {
            mediaType = MediaType.valueOf("image/svg+xml");
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
