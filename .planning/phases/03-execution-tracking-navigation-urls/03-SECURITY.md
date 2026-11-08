---
phase: "03"
slug: "execution-tracking-navigation-urls"
status: verified
threats_open: 0
asvs_level: 1
created: "2026-11-08"
---

# Phase 03 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client / Reverse Proxy → WorkflowExecutionController | Untrusted HTTP headers (Host, X-Forwarded-Host, X-Forwarded-Proto) cross into controller origin resolution | Forwarded header strings |
| HTTP Request → Report Asset File System | URL path parameters cross into local file path resolution for Karate HTML reports | Relative file paths |
| REST API → External Consumers (cURL, CI/CD) | Outgoing execution payload and Location header returned to unauthenticated / authenticated callers | Execution metadata and status URLs |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-03-01 | Spoofing | ForwardedHeaderFilter / application.yml | medium | mitigate | Enable server.forward-headers-strategy: framework so Spring's ForwardedHeaderFilter validates forwarded host syntax and normalizes proxy headers safely per RFC 7239 | closed |
| T-03-02 | Tampering | WorkflowExecutionController.serveReportAsset | high | mitigate | Restrict relativeFile path using contains("..") check and enforce targetFile.startsWith(reportDir) boundary check before serving files | closed |
| T-03-03 | Information Disclosure | ExecutionNavigationUrlBuilder | low | mitigate | Generate navigation URLs containing only resource UUIDs (/api/v1/executions/{id}) and standard report paths without embedding query secrets, tokens, or environment values | closed |
| T-03-SC | Tampering | npm/pip/cargo installs | high | mitigate | Zero new dependencies introduced in Phase 3; all implementations use existing Spring Boot 4.1.2 and TypeScript libraries | closed |

---

## Accepted Risks Log

No accepted risks.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-08 | 4 | 4 | 0 | gsd-security-auditor |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-08
