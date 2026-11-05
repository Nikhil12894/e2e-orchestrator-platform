package com.orchestrator.platform.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.orchestrator.platform.domain.entity.TestWorkflowEntity;
import com.orchestrator.platform.domain.entity.WorkflowExecutionEntity;
import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.dto.ExecutionStepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class WorkflowReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowReportGenerator.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.of("UTC"));

    private final ObjectMapper prettyMapper;

    public WorkflowReportGenerator(ObjectMapper objectMapper) {
        this.prettyMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public File generateReport(WorkflowExecutionEntity execution, TestWorkflowEntity workflow, Path reportDir) {
        try {
            if (!Files.exists(reportDir)) {
                Files.createDirectories(reportDir);
            }

            String html = buildReportHtml(execution, workflow);
            Path reportPath = reportDir.resolve("workflow-report.html");
            Files.writeString(reportPath, html, StandardCharsets.UTF_8);

            log.info("Executive Workflow Profiling Report generated at: {}", reportPath);
            return reportPath.toFile();
        } catch (Exception e) {
            log.error("Failed to generate workflow profiling report: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildReportHtml(WorkflowExecutionEntity execution, TestWorkflowEntity workflow) {
        String workflowName = workflow != null ? escapeHtml(workflow.getName()) : (execution.getWorkflowName() != null ? escapeHtml(execution.getWorkflowName()) : "Unnamed Workflow");
        int version = workflow != null ? workflow.getVersion() : 1;
        String executionId = execution.getId().toString();
        ExecutionStatus status = execution.getStatus() != null ? execution.getStatus() : ExecutionStatus.PENDING;
        boolean passed = status == ExecutionStatus.COMPLETED;
        long totalDurationMs = execution.getDurationMs() != null ? execution.getDurationMs() : 0L;
        String formattedDuration = totalDurationMs >= 1000 ? String.format("%.2f s", totalDurationMs / 1000.0) : totalDurationMs + " ms";
        String triggerType = execution.getTriggerType() != null ? escapeHtml(execution.getTriggerType().name()) : "MANUAL";
        String startTimeStr = execution.getStartTime() != null ? DATE_FMT.format(execution.getStartTime()) : DATE_FMT.format(Instant.now());

        List<ExecutionStepResult> allStepResults = execution.getStepResults() != null ? execution.getStepResults() : Collections.emptyList();
        List<ExecutionStepResult> reportableSteps = allStepResults.stream().filter(s -> !s.isExcludeFromReport()).toList();
        List<ExecutionStepResult> auxiliarySteps = allStepResults.stream().filter(ExecutionStepResult::isExcludeFromReport).toList();

        // If user configured all steps as excluded, fallback to all steps to prevent empty metrics
        List<ExecutionStepResult> scoredSteps = reportableSteps.isEmpty() ? allStepResults : reportableSteps;
        int totalSteps = scoredSteps.size();
        long passedSteps = scoredSteps.stream().filter(ExecutionStepResult::isAssertionsPassed).count();
        int healthPercentage = totalSteps > 0 ? (int) Math.round(((double) passedSteps / totalSteps) * 100) : 100;

        // Bottleneck step identification (profile all executed steps)
        ExecutionStepResult slowestStep = null;
        long maxStepDuration = -1;
        for (ExecutionStepResult sr : allStepResults) {
            long dur = sr.getDurationMs() != null ? sr.getDurationMs() : 0L;
            if (dur > maxStepDuration) {
                maxStepDuration = dur;
                slowestStep = sr;
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Workflow Profiling Report - ").append(workflowName).append("</title>\n");
        html.append("<style>\n");
        html.append(getCssStyles());
        html.append("</style>\n</head>\n<body>\n");

        // Top Navigation Header
        html.append("<header class=\"top-bar\">\n");
        html.append("  <div class=\"brand\">\n");
        html.append("    <span class=\"badge-logo\">⚡ MeterFlow</span>\n");
        html.append("    <h1>Executive Workflow Profiling Report</h1>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"report-switcher\">\n");
        html.append("    <a href=\"workflow-report.html\" class=\"nav-btn active\">📊 Workflow Profiling</a>\n");
        html.append("    <a href=\"karate-summary.html\" class=\"nav-btn\">🥋 Karate Engine Summary</a>\n");
        html.append("    <a href=\"karate-timeline.html\" class=\"nav-btn\">⏱️ Karate Timeline</a>\n");
        html.append("  </div>\n");
        html.append("</header>\n\n");

        html.append("<main class=\"container\">\n");

        // Title Banner
        html.append("  <section class=\"hero-card\">\n");
        html.append("    <div class=\"hero-header\">\n");
        html.append("      <div>\n");
        html.append("        <div class=\"status-tag ").append(passed ? "status-pass" : "status-fail").append("\">");
        html.append(passed ? "● EXECUTION PASSED" : "✕ EXECUTION FAILED");
        html.append("        </div>\n");
        html.append("        <h2 class=\"workflow-title\">").append(workflowName).append(" <span class=\"version-tag\">v").append(version).append("</span></h2>\n");
        html.append("        <p class=\"hero-meta\">Run ID: <code>").append(executionId).append("</code> &bull; Trigger: ").append(triggerType).append(" &bull; ").append(startTimeStr).append("</p>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");

        // Metrics Grid
        html.append("    <div class=\"metrics-grid\">\n");
        html.append("      <div class=\"metric-box\">\n");
        html.append("        <span class=\"metric-label\">Total Latency</span>\n");
        html.append("        <span class=\"metric-value text-emerald\">").append(formattedDuration).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"metric-box\">\n");
        html.append("        <span class=\"metric-label\">Steps Completed</span>\n");
        html.append("        <span class=\"metric-value\">").append(passedSteps).append(" / ").append(totalSteps).append("</span>\n");
        if (!auxiliarySteps.isEmpty()) {
            html.append("        <span class=\"metric-sub font-mono\">(").append(auxiliarySteps.size()).append(" auxiliary setup omitted)</span>\n");
        }
        html.append("      </div>\n");
        html.append("      <div class=\"metric-box\">\n");
        html.append("        <span class=\"metric-label\">Success Rate</span>\n");
        html.append("        <span class=\"metric-value ").append(healthPercentage == 100 ? "text-emerald" : "text-amber").append("\">").append(healthPercentage).append("%</span>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"metric-box\">\n");
        html.append("        <span class=\"metric-label\">Execution Engine</span>\n");
        html.append("        <span class=\"metric-value text-blue\">Karate 2.1.4 (Java 25)</span>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </section>\n\n");

        // Performance Bottleneck Alert
        if (slowestStep != null && totalDurationMs > 0 && maxStepDuration > 0) {
            double percent = ((double) maxStepDuration / totalDurationMs) * 100;
            html.append("  <div class=\"bottleneck-banner ").append(percent > 40 ? "bottleneck-warning" : "bottleneck-info").append("\">\n");
            html.append("    <span class=\"bottleneck-icon\">⚡</span>\n");
            html.append("    <div>\n");
            html.append("      <strong>Latency Bottleneck Analysis:</strong> Step <em>\"").append(escapeHtml(slowestStep.getStepName())).append("\"</em> took <strong>").append(maxStepDuration).append(" ms</strong> (<strong>").append(String.format("%.1f", percent)).append("%</strong> of total workflow execution time).\n");
            html.append("    </div>\n");
            html.append("  </div>\n\n");
        }

        // Waterfall Latency Chart
        html.append("  <section class=\"panel\">\n");
        html.append("    <h3 class=\"panel-title\">⏱️ Step Latency Profiling Waterfall</h3>\n");
        html.append("    <div class=\"waterfall-container\">\n");
        for (int i = 0; i < allStepResults.size(); i++) {
            ExecutionStepResult sr = allStepResults.get(i);
            long stepDur = sr.getDurationMs() != null ? sr.getDurationMs() : 0L;
            double stepPercent = totalDurationMs > 0 ? ((double) stepDur / totalDurationMs) * 100 : 0;
            double widthPercent = Math.max(stepPercent, 2.0); // minimum visual width
            boolean isBottleneck = (slowestStep == sr && stepPercent >= 35.0);

            html.append("      <div class=\"waterfall-row\">\n");
            html.append("        <div class=\"waterfall-label\">\n");
            html.append("          <span class=\"step-num\">").append(i + 1).append("</span>\n");
            html.append("          <span class=\"step-name truncate\">").append(escapeHtml(sr.getStepName())).append("</span>\n");
            if (sr.isExcludeFromReport()) {
                html.append("          <span class=\"badge-aux font-mono\">AUXILIARY</span>\n");
            }
            html.append("        </div>\n");
            html.append("        <div class=\"waterfall-bar-track\">\n");
            html.append("          <div class=\"waterfall-bar ").append(isBottleneck ? "bar-bottleneck" : "bar-normal").append("\" style=\"width: ").append(String.format(Locale.US, "%.1f", widthPercent)).append("%;\">\n");
            html.append("            <span class=\"bar-tooltip\">").append(stepDur).append(" ms (").append(String.format(Locale.US, "%.1f", stepPercent)).append("%)</span>\n");
            html.append("          </div>\n");
            html.append("        </div>\n");
            html.append("        <div class=\"waterfall-time font-mono\">").append(stepDur).append(" ms</div>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");
        html.append("  </section>\n\n");

        // Step by Step Detailed Accordion
        html.append("  <section class=\"panel\">\n");
        html.append("    <h3 class=\"panel-title\">🔍 Step-by-Step Diagnostics & Payload Inspector</h3>\n");
        html.append("    <div class=\"steps-list\">\n");

        for (int i = 0; i < allStepResults.size(); i++) {
            ExecutionStepResult sr = allStepResults.get(i);
            boolean stepPassed = sr.isAssertionsPassed() && sr.getStatus() == ExecutionStatus.COMPLETED;
            long stepDur = sr.getDurationMs() != null ? sr.getDurationMs() : 0L;
            String typeStr = sr.getStepType() != null ? sr.getStepType().name() : "HTTP_REQUEST";

            html.append("      <details class=\"step-card\" open>\n");
            html.append("        <summary class=\"step-summary\">\n");
            html.append("          <div class=\"step-left\">\n");
            html.append("            <span class=\"step-badge ").append(stepPassed ? "badge-pass" : "badge-fail").append("\">").append(stepPassed ? "✓ PASS" : "✕ FAIL").append("</span>\n");
            if (sr.isExcludeFromReport()) {
                html.append("            <span class=\"step-badge badge-aux\">UNCOUNTED / AUXILIARY</span>\n");
            }
            html.append("            <span class=\"step-type-pill\">").append(typeStr).append("</span>\n");
            html.append("            <strong class=\"step-title\">").append(escapeHtml(sr.getStepName())).append("</strong>\n");
            html.append("          </div>\n");
            html.append("          <div class=\"step-right\">\n");
            if (sr.getHttpStatus() != null) {
                html.append("            <span class=\"http-status-pill http-").append(sr.getHttpStatus() < 400 ? "ok" : "err").append("\">HTTP ").append(sr.getHttpStatus()).append("</span>\n");
            }
            html.append("            <span class=\"step-duration font-mono\">").append(stepDur).append(" ms</span>\n");
            html.append("          </div>\n");
            html.append("        </summary>\n");

            html.append("        <div class=\"step-body\">\n");

            // Assertions
            if (sr.getAssertionDetails() != null && !sr.getAssertionDetails().isEmpty()) {
                html.append("          <div class=\"sub-panel\">\n");
                html.append("            <h4 class=\"sub-title\">Assertions Evaluated</h4>\n");
                html.append("            <ul class=\"assertion-list\">\n");
                for (String detail : sr.getAssertionDetails()) {
                    boolean pass = detail.contains("[PASS]");
                    html.append("              <li class=\"assertion-item ").append(pass ? "assert-pass" : "assert-fail").append("\">").append(escapeHtml(detail)).append("</li>\n");
                }
                html.append("            </ul>\n");
                html.append("          </div>\n");
            }

            // Extracted variables
            if (sr.getExtractedVariables() != null && !sr.getExtractedVariables().isEmpty()) {
                html.append("          <div class=\"sub-panel\">\n");
                html.append("            <h4 class=\"sub-title text-emerald\">Extracted Variables</h4>\n");
                html.append("            <div class=\"code-block\"><code>").append(escapeHtml(formatJson(sr.getExtractedVariables()))).append("</code></div>\n");
                html.append("          </div>\n");
            }

            // Payloads
            html.append("          <div class=\"payload-grid\">\n");
            if (sr.getRequestHeaders() != null && !sr.getRequestHeaders().isEmpty()) {
                html.append("            <div class=\"payload-col\">\n");
                html.append("              <h4 class=\"sub-title\">Effective Request Headers</h4>\n");
                html.append("              <div class=\"code-block\"><pre>").append(escapeHtml(formatJson(sr.getRequestHeaders()))).append("</pre></div>\n");
                html.append("            </div>\n");
            }
            if (sr.getRequestPayload() != null) {
                html.append("            <div class=\"payload-col\">\n");
                html.append("              <h4 class=\"sub-title\">Request Payload</h4>\n");
                html.append("              <div class=\"code-block\"><pre>").append(escapeHtml(formatJson(sr.getRequestPayload()))).append("</pre></div>\n");
                html.append("            </div>\n");
            }
            if (sr.getResponsePayload() != null) {
                html.append("            <div class=\"payload-col\">\n");
                html.append("              <h4 class=\"sub-title\">Response Payload</h4>\n");
                html.append("              <div class=\"code-block\"><pre>").append(escapeHtml(formatJson(sr.getResponsePayload()))).append("</pre></div>\n");
                html.append("            </div>\n");
            }
            html.append("          </div>\n");

            if (sr.getErrorMessage() != null && !sr.getErrorMessage().isEmpty()) {
                html.append("          <div class=\"error-banner\">\n");
                html.append("            <strong>Error:</strong> ").append(escapeHtml(sr.getErrorMessage())).append("\n");
                html.append("          </div>\n");
            }

            html.append("        </div>\n");
            html.append("      </details>\n");
        }

        html.append("    </div>\n");
        html.append("  </section>\n\n");

        // Context Snapshot
        if (execution.getContextSnapshot() != null && !execution.getContextSnapshot().isEmpty()) {
            html.append("  <section class=\"panel\">\n");
            html.append("    <h3 class=\"panel-title\">📦 Execution Context Snapshot & Masked Secrets</h3>\n");
            html.append("    <p class=\"panel-desc\">Sensitive secrets registered in workflow settings have been scrubbed and replaced with <code>[REDACTED]</code>.</p>\n");
            html.append("    <div class=\"code-block\"><pre>").append(escapeHtml(formatJson(execution.getContextSnapshot()))).append("</pre></div>\n");
            html.append("  </section>\n");
        }

        html.append("</main>\n\n");
        html.append("<footer class=\"footer\">\n");
        html.append("  MeterFlow E2E Orchestrator Platform &bull; Automated Execution Report &bull; Powered by Java 25 &amp; Karate 2.1.4\n");
        html.append("</footer>\n\n");
        html.append("</body>\n</html>");

        return html.toString();
    }

    private String formatJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String str) {
            try {
                Object parsed = prettyMapper.readTree(str);
                return prettyMapper.writeValueAsString(parsed);
            } catch (Exception e) {
                return str;
            }
        }
        try {
            return prettyMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String getCssStyles() {
        return """
            :root {
              --bg: #030712;
              --card: #0f172a;
              --card-border: #1e293b;
              --text: #f8fafc;
              --text-muted: #94a3b8;
              --emerald: #10b981;
              --blue: #3b82f6;
              --amber: #f59e0b;
              --rose: #f43f5e;
              --indigo: #6366f1;
            }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              background-color: var(--bg);
              color: var(--text);
              line-height: 1.5;
              padding-bottom: 60px;
            }
            code, pre, .font-mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
            .top-bar {
              display: flex;
              align-items: center;
              justify-content: space-between;
              padding: 14px 28px;
              background-color: rgba(15, 23, 42, 0.95);
              border-bottom: 1px solid var(--card-border);
              position: sticky;
              top: 0;
              z-index: 50;
              backdrop-filter: blur(8px);
            }
            .brand { display: flex; align-items: center; gap: 12px; }
            .badge-logo {
              background: linear-gradient(135deg, #2563eb, #4f46e5);
              color: white;
              padding: 4px 10px;
              border-radius: 8px;
              font-weight: 700;
              font-size: 13px;
              letter-spacing: 0.5px;
            }
            .brand h1 { font-size: 15px; font-weight: 600; color: #e2e8f0; }
            .report-switcher { display: flex; gap: 8px; }
            .nav-btn {
              padding: 6px 14px;
              border-radius: 8px;
              font-size: 12px;
              font-weight: 500;
              text-decoration: none;
              color: var(--text-muted);
              background-color: #1e293b;
              border: 1px solid #334155;
              transition: all 0.15s ease;
            }
            .nav-btn:hover { color: white; background-color: #334155; }
            .nav-btn.active {
              background-color: #2563eb;
              border-color: #3b82f6;
              color: white;
              font-weight: 600;
            }
            .container { max-width: 1200px; margin: 28px auto; padding: 0 20px; display: flex; flex-direction: column; gap: 24px; }
            .hero-card {
              background: linear-gradient(180deg, #0f172a, #0b1120);
              border: 1px solid var(--card-border);
              border-radius: 16px;
              padding: 28px;
              box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            }
            .hero-header { margin-bottom: 24px; }
            .status-tag {
              display: inline-block;
              font-size: 11px;
              font-weight: 700;
              letter-spacing: 0.5px;
              padding: 4px 10px;
              border-radius: 9999px;
              margin-bottom: 12px;
            }
            .status-pass { background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); }
            .status-fail { background: rgba(244, 63, 94, 0.15); color: #fb7185; border: 1px solid rgba(244, 63, 94, 0.3); }
            .workflow-title { font-size: 24px; font-weight: 700; color: #f8fafc; }
            .version-tag { font-size: 13px; font-weight: 500; color: var(--text-muted); margin-left: 6px; }
            .hero-meta { font-size: 13px; color: var(--text-muted); margin-top: 6px; }
            .metrics-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
              gap: 16px;
              padding-top: 20px;
              border-top: 1px solid rgba(255,255,255,0.06);
            }
            .metric-box {
              background-color: #1e293b50;
              border: 1px solid #334155;
              padding: 16px;
              border-radius: 12px;
            }
            .metric-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted); display: block; margin-bottom: 6px; }
            .metric-value { font-size: 20px; font-weight: 700; color: #f1f5f9; }
            .text-emerald { color: #34d399; }
            .text-blue { color: #60a5fa; }
            .text-amber { color: #fbbf24; }
            .bottleneck-banner {
              display: flex;
              align-items: center;
              gap: 12px;
              padding: 14px 18px;
              border-radius: 12px;
              font-size: 13px;
            }
            .bottleneck-warning { background: rgba(245, 158, 11, 0.1); border: 1px solid rgba(245, 158, 11, 0.3); color: #fbbf24; }
            .bottleneck-info { background: rgba(59, 130, 246, 0.1); border: 1px solid rgba(59, 130, 246, 0.3); color: #93c5fd; }
            .bottleneck-icon { font-size: 18px; }
            .panel {
              background-color: var(--card);
              border: 1px solid var(--card-border);
              border-radius: 14px;
              padding: 24px;
            }
            .panel-title { font-size: 16px; font-weight: 600; color: #e2e8f0; margin-bottom: 16px; }
            .panel-desc { font-size: 12px; color: var(--text-muted); margin-bottom: 14px; }
            .waterfall-container { display: flex; flex-direction: column; gap: 10px; }
            .waterfall-row { display: grid; grid-template-columns: 240px 1fr 90px; align-items: center; gap: 14px; font-size: 13px; }
            .waterfall-label { display: flex; align-items: center; gap: 8px; min-width: 0; }
            .step-num { font-size: 10px; font-weight: 700; background: #334155; padding: 2px 6px; border-radius: 6px; color: #cbd5e1; }
            .truncate { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .waterfall-bar-track { background-color: #1e293b; height: 18px; border-radius: 6px; overflow: hidden; position: relative; }
            .waterfall-bar { height: 100%; border-radius: 6px; transition: width 0.3s ease; position: relative; }
            .bar-normal { background: linear-gradient(90deg, #3b82f6, #6366f1); }
            .bar-bottleneck { background: linear-gradient(90deg, #f59e0b, #ef4444); }
            .bar-tooltip { font-size: 10px; font-weight: 600; color: white; padding-left: 6px; line-height: 18px; white-space: nowrap; }
            .waterfall-time { text-align: right; color: var(--text-muted); font-size: 12px; }
            .steps-list { display: flex; flex-direction: column; gap: 12px; }
            .step-card {
              border: 1px solid var(--card-border);
              border-radius: 10px;
              background-color: #0b1120;
              overflow: hidden;
            }
            .step-summary {
              display: flex;
              align-items: center;
              justify-content: space-between;
              padding: 12px 18px;
              cursor: pointer;
              background-color: #131d33;
              transition: background-color 0.15s;
            }
            .step-summary:hover { background-color: #1e293b; }
            .step-left, .step-right { display: flex; align-items: center; gap: 10px; }
            .step-badge { font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 6px; }
            .badge-pass { background: rgba(16, 185, 129, 0.2); color: #34d399; }
            .badge-fail { background: rgba(244, 63, 94, 0.2); color: #fb7185; }
            .badge-aux { background: rgba(148, 163, 184, 0.15); color: #94a3b8; border: 1px dashed rgba(148, 163, 184, 0.4); font-size: 10px; font-weight: 600; padding: 2px 6px; border-radius: 4px; }
            .metric-sub { font-size: 11px; color: #94a3b8; display: block; margin-top: 4px; }
            .step-type-pill { font-size: 10px; font-family: monospace; background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 4px; }
            .http-status-pill { font-size: 11px; font-family: monospace; font-weight: 600; padding: 2px 8px; border-radius: 6px; }
            .http-ok { background: rgba(16, 185, 129, 0.2); color: #34d399; }
            .http-err { background: rgba(244, 63, 94, 0.2); color: #fb7185; }
            .step-duration { font-size: 12px; color: var(--text-muted); }
            .step-body { padding: 18px; border-top: 1px solid var(--card-border); display: flex; flex-direction: column; gap: 14px; }
            .sub-panel { background: #070d19; border: 1px solid #1e293b; border-radius: 8px; padding: 12px; }
            .sub-title { font-size: 12px; font-weight: 600; color: #94a3b8; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }
            .assertion-list { list-style: none; display: flex; flex-direction: column; gap: 6px; }
            .assertion-item { font-size: 12px; font-family: monospace; padding: 4px 8px; border-radius: 6px; }
            .assert-pass { background: rgba(16, 185, 129, 0.1); color: #34d399; }
            .assert-fail { background: rgba(244, 63, 94, 0.1); color: #fb7185; }
            .payload-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 12px; }
            .payload-col { background: #070d19; border: 1px solid #1e293b; border-radius: 8px; padding: 12px; }
            .code-block { background-color: #030712; padding: 12px; border-radius: 6px; overflow-x: auto; max-height: 260px; font-size: 12px; color: #cbd5e1; border: 1px solid #1e293b; }
            .error-banner { background: rgba(244, 63, 94, 0.15); border: 1px solid rgba(244, 63, 94, 0.3); color: #fda4af; padding: 10px 14px; border-radius: 8px; font-size: 12px; }
            .footer { text-align: center; color: var(--text-muted); font-size: 12px; margin-top: 30px; }
            """;
    }
}
