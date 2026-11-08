package com.orchestrator.platform.service.execution;

import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Component
public class ExecutionNavigationUrlBuilder {

    public void enrichNavigationUrls(WorkflowExecutionEntity execution, HttpServletRequest request) {
        if (execution == null || execution.getId() == null) {
            return;
        }
        UUID id = execution.getId();
        execution.setStatusUrl(buildStatusUrl(id, request));
        execution.setStreamUrl(buildStreamUrl(id, request));
        execution.setReportUrl(buildReportUrl(id, execution.getReportPath(), request));
    }

    public String buildStatusUrl(UUID executionId, HttpServletRequest request) {
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}")
                .buildAndExpand(executionId)
                .toUriString();
    }

    public String buildStreamUrl(UUID executionId, HttpServletRequest request) {
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/stream")
                .buildAndExpand(executionId)
                .toUriString();
    }

    public String buildReportUrl(UUID executionId, String reportPath, HttpServletRequest request) {
        if (reportPath != null && !reportPath.isBlank()) {
            if (reportPath.startsWith("/")) {
                return getBaseBuilder(request)
                        .path(reportPath)
                        .toUriString();
            } else {
                return getBaseBuilder(request)
                        .path("/api/v1/executions/{id}/report/" + reportPath)
                        .buildAndExpand(executionId)
                        .toUriString();
            }
        }
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/report/workflow-report.html")
                .buildAndExpand(executionId)
                .toUriString();
    }

    private UriComponentsBuilder getBaseBuilder(HttpServletRequest request) {
        if (request != null) {
            return ServletUriComponentsBuilder.fromContextPath(request);
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath();
    }
}
