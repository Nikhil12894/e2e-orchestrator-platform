# Phase 5: CI/CD Webhook Modal & Pipeline Snippets - Research
**Researched:** 2026-11-08  
**Domain:** Frontend React UI, CI/CD Integration, cURL & Bash Script Generation  
**Confidence:** HIGH  

## Project Constraints (from GEMINI.md)

Directives extracted from `./GEMINI.md`:
- **Compatibility**: All existing frontend packages (`@xyflow/react`, `@stomp/stompjs`, `axios`, `clsx`, `lucide-react`, `sockjs-client`, `tailwind-merge`) must build without errors under Node 26.
- **Docker**: The frontend Dockerfile must build reliably with `node:26-alpine`.
- **Zero Breaking Changes**: The visual canvas UI and execution streaming behavior must not change.
- **Code Conventions**:
  - React component files use PascalCase with `.tsx` (`CiCdWebhookModal.tsx`).
  - TypeScript / React: 2-space indentation; ES module imports; Tailwind CSS utility classes organized logically (layout, sizing, typography, colors, dark mode variants).
  - TypeScript compiler (`tsc --noEmit` in `npm run build`) with strict type checking enabled in `frontend/tsconfig.json` (`strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`).
  - Standard relative imports (`./`, `../../`).

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CURL-01 | `CiCdWebhookModal` displays the direct Karate HTML report URL format and live status checking cURL command alongside the trigger snippet. | Verified backend endpoints `GET /api/v1/executions/{id}` and `GET /api/v1/executions/{id}/report/**` in `WorkflowExecutionController.java`. Modal UI provides distinct copyable blocks for status polling and Karate HTML report URLs with dynamic origin interpolation. |
| CURL-02 | `CiCdWebhookModal` provides toggleable choices between "Asynchronous Trigger (HTTP 202)" and "Synchronous CI/CD Pipeline (HTTP 200 with report link)". | Verified `POST /api/v1/workflows/{workflowId}/execute` behavior in `WorkflowExecutionController.java` (`wait=true&timeoutSec=N`). Modal features a segmented mode toggle dynamically adapting parameters, explanation badges, cURL snippets, and bash scripts. |
| CURL-03 | `CiCdWebhookModal` provides a copyable bash script snippet for CI/CD pipelines that triggers execution, verifies exit status, and outputs the direct HTML report URL. | Researched POSIX-compliant, zero-dependency bash pipeline scripts handling HTTP status extraction, 504 timeout detection, JSON property parsing via fallback-safe tools, exit code assertions, and direct Karate HTML report link emission. |
</phase_requirements>

## Summary

Phase 5 completes Milestone v1.1 ("Execution Status & Report URLs for cURL / CI/CD") by modernizing and expanding the frontend `CiCdWebhookModal` component. In Phase 3 and Phase 4, the backend was enriched with absolute navigation URLs (`statusUrl`, `reportUrl`, `streamUrl`), standard RFC 7231 `Location` headers, and synchronous wait execution (`?wait=true&timeoutSec=60`) on Java 25 virtual threads. Phase 5 equips automated pipeline authors (GitHub Actions, GitLab CI, Jenkins, CLI scripts) to copy ready-to-run cURL snippets and end-to-end bash scripts directly from the visual orchestrator canvas.

The current modal implementation (`frontend/src/components/navbar/CiCdWebhookModal.tsx:24-30`) provides only a basic asynchronous trigger cURL command without status polling commands, report URL formats, synchronous wait toggles, or automated pipeline scripts. Phase 5 replaces this with a feature-rich, responsive dialog featuring:
1. **Execution Mode Switcher**: Seamless toggle between "Asynchronous Trigger (HTTP 202)" and "Synchronous CI/CD Pipeline (HTTP 200 with report link)".
2. **Configuration Controls**: Target execution environment selector (`default`, `dev`, `staging`, `prod`) and configurable wait timeout stepper (`timeoutSec`, 1s–300s, default 60s) for synchronous executions.
3. **Multi-snippet cURL Display**: Dedicated, copyable cards for the execution trigger, live status polling (`GET /api/v1/executions/{id}`), and direct Karate HTML report link template (`/api/v1/executions/{id}/report/workflow-report.html`).
4. **End-to-End CI/CD Bash Script**: A complete, portable, POSIX-compatible bash script that executes the workflow, evaluates HTTP codes, handles 504 timeout continuations, checks execution completion, and prints the direct Karate HTML report URL.

**Primary recommendation:** Implement clean modular snippet generation utility functions in `frontend/src/utils/cicdSnippets.ts` with comprehensive unit tests using Node 26's built-in `node:test` runner, and integrate these into `frontend/src/components/navbar/CiCdWebhookModal.tsx` utilizing Tailwind CSS and `lucide-react` icons while preserving the existing component props interface in `Navbar.tsx`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Modal Presentation & Toggle State | Browser / Client | — | React state manages selected mode (async vs sync), environment dropdown, timeout input, active snippet tab, and copy notifications |
| Dynamic cURL Snippet Generation | Browser / Client | — | Dynamically constructs cURL commands interpolating `window.location.origin`, `workflow.id`, selected environment, and timeout parameters |
| CI/CD Pipeline Bash Script Generation | Browser / Client | — | Templates full-featured shell scripts with robust error handling, exit code verification, and report URL output |
| Asynchronous Workflow Execution | API / Backend | — | Java 25 Virtual Thread worker initiated via `POST /api/v1/workflows/{id}/execute`, returning HTTP 202 with `Location` header and navigation URLs |
| Synchronous Workflow Wait & Timeout | API / Backend | — | Virtual thread completion awaited via non-blocking `CompletableFuture`, returning HTTP 200 upon completion or HTTP 504 on timeout |
| Status Tracking & Details | API / Backend | Database / Storage | `GET /api/v1/executions/{id}` returns persisted execution entity, final status, context snapshot, and populated `reportUrl` |
| Karate HTML Report Serving | API / Backend | File Storage | `GET /api/v1/executions/{id}/report/**` streams static HTML/CSS/JS artifacts directly to browser or CI artifact collectors |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `react` | 18.3.1 [VERIFIED: frontend/package.json:17] | UI component tree & hooks | Standard project frontend UI framework |
| `typescript` | 5.6.3 [VERIFIED: frontend/package.json:31] | Static typing & interface definitions | Strict compile-time checks (`tsc --noEmit`) |
| `tailwindcss` | 3.4.14 [VERIFIED: frontend/package.json:30] | Utility styling & responsive layout | Standard UI styling system used across all modals and canvas components |
| `lucide-react` | 0.453.0 [VERIFIED: frontend/package.json:16] | Iconography | Standard icon set for navbar, modal controls, copy feedback, and badges |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `clsx` & `tailwind-merge` | 2.1.1 / 2.5.4 [VERIFIED: frontend/package.json:15,20] | Dynamic class name resolution | Merging conditional Tailwind styles on buttons and tabs |
| `node:test` & `node:assert` | Built-in (Node 26) [VERIFIED: node -v v26.13.0] | Unit testing snippet generator utilities | Zero-dependency fast testing of pure snippet formatting logic |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Monospace `<pre>` + Tailwind | PrismJS / highlight.js | External syntax highlighters add 40–150 kB bundle bloat and external styles; Tailwind pre/code blocks render instantly with zero extra dependencies |
| Pure POSIX shell parsing (`grep`/`cut`/`sed`) | Hardcoded `jq` dependency | Many CI/CD runner container images (e.g. minimal Alpine, slim custom images) do not have `jq` preinstalled; pure POSIX commands work universally without failing pipelines |
| Pure client snippet generator | Backend `/api/v1/workflows/{id}/snippets` endpoint | Client-side snippet generation avoids unnecessary network calls, updates instantly as users adjust inputs, and leverages `window.location.origin` directly |

**Installation:**
No new packages are needed. All required tools are already installed in `frontend/package.json`.

## Package Legitimacy Audit

> **Verification:** No new external packages are introduced in Phase 5. All capabilities are implemented with existing core libraries (`react`, `lucide-react`, `tailwindcss`) and Node.js built-ins.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `react` | npm | 11+ yrs | 25M+/wk | github.com/facebook/react | [OK] | Existing approved dependency |
| `lucide-react` | npm | 4+ yrs | 5M+/wk | github.com/lucide-icons/lucide | [OK] | Existing approved dependency |
| `tailwindcss` | npm | 7+ yrs | 9M+/wk | github.com/tailwindlabs/tailwindcss | [OK] | Existing approved dependency |

**Packages removed due to [SLOP] verdict:** none  
**Packages flagged as suspicious [SUS]:** none  

## Architecture Patterns

### System Architecture Diagram

```
+---------------------------------------------------------------------------------------+
|                                    User Browser                                       |
|                                                                                       |
|  [Navbar: Terminal Icon]                                                              |
|          | (click)                                                                    |
|          v                                                                            |
|  +---------------------------------------------------------------------------------+  |
|  |                             CiCdWebhookModal                                    |  |
|  |                                                                                 |  |
|  |  +---------------------------------------------------------------------------+  |  |
|  |  | Execution Mode:  [ (A) Asynchronous (HTTP 202) ] [ (B) Synchronous (HTTP 200) ]  |  |
|  |  +---------------------------------------------------------------------------+  |  |
|  |  | Options: Environment [default/dev/staging/prod] | Timeout [60s] (Sync only)  |  |  |
|  |  +---------------------------------------------------------------------------+  |  |
|  |  | Tabs: [ cURL Commands ]   [ CI/CD Bash Pipeline Script ]                  |  |  |
|  |  +---------------------------------------------------------------------------+  |  |
|  |  | Content:                                                                  |  |  |
|  |  |  - Trigger Snippet (POST .../execute[?wait=true&timeoutSec=N])             |  |  |
|  |  |  - Status Check Snippet (GET .../executions/{id})                        |  |  |
|  |  |  - Karate HTML Report URL (GET .../executions/{id}/report/...)            |  |  |
|  |  |  - Complete CI/CD Shell Script with exit code verification & reporting    |  |  |
|  |  |  - Copy Button per snippet with visual "Copied!" feedback                 |  |  |
|  |  +---------------------------------------------------------------------------+  |  |
|  +---------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------+
                                           |
                               (Copies snippet into)
                                           v
+---------------------------------------------------------------------------------------+
|                        External CI/CD Runner / Terminal                               |
|                   (GitHub Actions / GitLab CI / Jenkins / CLI)                         |
|                                                                                       |
|   1. POST /api/v1/workflows/{id}/execute[?wait=true&timeoutSec=N]                     |
|          |                                                                            |
|          +---> (Async) ===> Returns 202 + Location + statusUrl                        |
|          |                   |                                                        |
|          |                   v (Loop poll GET /api/v1/executions/{id})                |
|          |                                                                            |
|          +---> (Sync)  ===> Blocks on Virtual Thread                                  |
|                              |                                                        |
|                              +---> (Finished in time) => 200 OK + status + reportUrl   |
|                              |                                                        |
|                              +---> (Exceeded timeout) => 504 Gateway Timeout          |
|                                                          + statusUrl (keeps running)  |
|                                                                                       |
|   2. Script evaluates status, verifies COMPLETED, and outputs Karate HTML Report URL  |
+---------------------------------------------------------------------------------------+
```

### Recommended Project Structure
```
frontend/src/
├── components/
│   └── navbar/
│       ├── CiCdWebhookModal.tsx       # Upgraded modal with mode switcher, tabs, copy buttons
│       └── Navbar.tsx                 # Consumes CiCdWebhookModal (lazy-loaded, unchanged interface)
├── utils/
│   └── cicdSnippets.ts                # Pure formatting functions for cURL commands and bash scripts
└── types/
    └── workflow.ts                    # TestWorkflow and WorkflowExecution interfaces
tests/
└── cicdSnippets.test.ts               # Unit tests verifying formatting, escaping, and script logic
```

### Pattern 1: Pure Utility Functions for Snippet Generation
**What:** Decouple string templating and script formatting from React component state into pure, deterministic functions (`generateAsyncCurlSnippet`, `generateSyncCurlSnippet`, `generateStatusPollSnippet`, `generateKarateReportUrl`, `generateCiCdBashScript`).  
**When to use:** Whenever dynamic commands or scripts depend on multiple configuration parameters (`origin`, `workflowId`, `environment`, `timeoutSec`, `mode`).  
**Benefit:** Enables 100% automated unit testing with Node's native test runner (`node --test`), eliminates string manipulation bugs inside JSX render paths, and simplifies maintenance.

### Pattern 2: Multi-Card Segmented Tabs with Dedicated Copy Feedback
**What:** Organize the modal into two primary tabs ("cURL Commands" and "CI/CD Pipeline Script"), where the cURL tab displays stacked cards (Trigger, Polling Status, Karate Report) each equipped with independent copy buttons and individual "Copied!" timeout states.  
**When to use:** When users need either quick one-off CLI verification commands or a complete end-to-end automation script for their CI pipeline.

### Anti-Patterns to Avoid
- **Hardcoding localhost in snippets:** Never hardcode `http://localhost:8080`. Always use `window.location.origin` (or host) dynamically so that deployments behind reverse proxies, Docker, Kubernetes, or domain names generate accurate URLs matching `ExecutionNavigationUrlBuilder`.
- **Requiring `jq` in bash snippets without fallback:** Many lightweight container images (such as Alpine Linux or scratch images) lack `jq`. Pipelines will crash immediately. Provide POSIX parameter extraction (`grep`/`cut`/`sed`) or a conditional fallback `if command -v jq >/dev/null 2>&1`.
- **Misrepresenting HTTP 504 timeouts as fatal run abortions:** When `?wait=true` times out, the backend keeps running the execution on the virtual thread. The bash script must explicitly notify the user that the run is still active in the background and provide the `statusUrl` to inspect.
- **Uncontrolled modal height:** A modal with multiple long code blocks will overflow small laptop viewports. Wrap the content container in `max-h-[85vh]` or `max-h-[90vh]` with `overflow-y-auto`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Code syntax highlighting | Custom parser or PrismJS dependency | Styled monospace `<pre className="font-mono bg-slate-950 ...">` | Zero bundle size increase, fast rendering, perfect dark/light theme integration |
| Clipboard copy | Deprecated `document.execCommand('copy')` | `navigator.clipboard.writeText()` with `try/catch` fallback | Modern standard browser API, secure, asynchronous |
| Reverse proxy URL adaptation | Custom string concatenation for host/port | `window.location.origin` on client; Spring's `ServletUriComponentsBuilder` on backend | Handles HTTPS, reverse proxies (`X-Forwarded-*`), and custom ports transparently |
| JSON parsing in shell scripts | Custom regex parsers or Python one-liners | POSIX `grep -o` + `cut -d'"' -f4` with optional `jq` check | Universal portability across Linux, macOS, Alpine, Debian, RedHat runners |

## Runtime State Inventory

> Greenfield UI modal enhancement for Phase 5. Verified across all categories:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — database entities unchanged | None — UI formatting only |
| Live service config | None — Nginx and Spring Boot endpoints unchanged | None |
| OS-registered state | None | None |
| Secrets/env vars | None — environment names (`default`, `dev`, `staging`, `prod`) are client-side parameters | None |
| Build artifacts | `frontend/dist/` bundle assets | Rebuilt cleanly via `npm run build` |

## Common Pitfalls

### Pitfall 1: Broken JSON in cURL command when environment contains special characters
**What goes wrong:** If environment or variables contain spaces or quotes, bash interprets them as unquoted tokens, causing HTTP 400 Bad Request.  
**Why it happens:** Naive string interpolation without proper shell quoting.  
**How to avoid:** Use properly escaped double quotes and structured JSON formatting in snippets:
```bash
-d '{
  "triggerType": "WEBHOOK",
  "environment": "'"${selectedEnv}"'",
  "initialVariables": {}
}'
```

### Pitfall 2: Shell script exits immediately on grep non-match with `set -e`
**What goes wrong:** In bash scripts running with `set -e`, if `grep` returns exit code 1 (when a pattern like `"reportUrl"` is missing or empty), the script immediately aborts before printing helpful diagnostics.  
**Why it happens:** `grep` returns 1 when no matching line is found; `set -e` traps this as a script failure.  
**How to avoid:** Append `|| true` to grep extractions: `grep -o '"reportUrl":"[^"]*' | cut -d'"' -f4 || true`.

### Pitfall 3: Component Props Incompatibility with Navbar
**What goes wrong:** Renaming or adding mandatory props to `CiCdWebhookModal` breaks `Navbar.tsx:499-504`.  
**Why it happens:** `Navbar.tsx` lazy-loads `CiCdWebhookModal` passing `{ isOpen, onClose, workflow, currentEnvironment }`.  
**How to avoid:** Keep `CiCdWebhookModalProps` exactly compatible:
```typescript
[VERIFIED: frontend/src/components/navbar/CiCdWebhookModal.tsx:5-10]
interface CiCdWebhookModalProps {
  isOpen: boolean;
  onClose: () => void;
  workflow: TestWorkflow;
  currentEnvironment?: string;
}
```

### Pitfall 4: Timeout parameter out of backend bounds
**What goes wrong:** User enters a negative number or a very large number (e.g. 10000s) for timeout.  
**Why it happens:** Unconstrained number inputs allow arbitrary values.  
**How to avoid:** Clamp client input to `min={1}` and `max={300}` (matching backend's `orchestrator.execution.max-wait-timeout-sec: 300` in `WorkflowExecutionController.java:46-47`).

## Code Examples

### Verified In-Repo API Definitions

From `WorkflowExecutionController.java:59-68`:
```java
// [VERIFIED: backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:59-68]
    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "Trigger workflow execution",
            description = "Triggers workflow test execution. With ?wait=true, blocks up to timeoutSec returning HTTP 200 upon completion, or HTTP 504 (or 202 if timeoutStatus=202) on timeout. Without ?wait=true, returns HTTP 202 immediately.")
    public ResponseEntity<WorkflowExecutionEntity> executeWorkflow(
            @PathVariable("workflowId") UUID workflowId,
            @RequestParam(value = "wait", required = false, defaultValue = "false") boolean wait,
            @RequestParam(value = "timeoutSec", required = false, defaultValue = "60") int timeoutSec,
            @RequestParam(value = "timeoutStatus", required = false, defaultValue = "${orchestrator.execution.default-timeout-status:504}") int timeoutStatus,
            @RequestBody(required = false) WorkflowExecutionRequest request,
            HttpServletRequest httpRequest) {
```

From `WorkflowExecutionController.java:127-130`:
```java
// [VERIFIED: backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:127-130]
    @GetMapping("/executions/{id}")
    @Operation(summary = "Get execution details", description = "Fetches status, context snapshot, step evaluations, and report links")
    public ResponseEntity<WorkflowExecutionEntity> getExecutionById(
            @PathVariable("id") UUID id,
```

From `WorkflowExecutionController.java:184-188`:
```java
// [VERIFIED: backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:184-188]
    @GetMapping("/executions/{id}/report/**")
    @Operation(summary = "Serve Karate HTML Report", description = "Serves isolated per-run Karate HTML report, javascript, stylesheets, and assets")
    public ResponseEntity<Resource> serveReportAsset(
            @PathVariable("id") UUID id,
            HttpServletRequest request) {
```

From `ExecutionNavigationUrlBuilder.java:55-59`:
```java
// [VERIFIED: backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java:55-59]
        return getBaseBuilder(request)
                .path("/api/v1/executions/{id}/report/workflow-report.html")
                .buildAndExpand(executionId)
                .toUriString();
```

### Complete End-to-End CI/CD Bash Script Template (Synchronous Mode)

```bash
#!/usr/bin/env bash
# ==============================================================================
# Automated CI/CD Execution Script for E2E Test Workflow Orchestrator
# Workflow: {{workflow.name}} (ID: {{workflow.id}})
# Mode: Synchronous Execution (HTTP 200 with Karate HTML Report Link)
# ==============================================================================
set -euo pipefail

BASE_URL="${ORCHESTRATOR_URL:-{{origin}}}"
WORKFLOW_ID="{{workflow.id}}"
ENVIRONMENT="${TARGET_ENV:-{{selectedEnv}}}"
TIMEOUT_SEC={{timeoutSec}}

echo "🚀 Triggering synchronous workflow execution (${WORKFLOW_ID})..."
echo "   Target endpoint: ${BASE_URL}/api/v1/workflows/${WORKFLOW_ID}/execute?wait=true&timeoutSec=${TIMEOUT_SEC}"
echo "   Environment    : ${ENVIRONMENT}"

# Execute request, capturing response body and HTTP status code
RESPONSE=$(curl -s -S -w "\n%{http_code}" -X POST \
  "${BASE_URL}/api/v1/workflows/${WORKFLOW_ID}/execute?wait=true&timeoutSec=${TIMEOUT_SEC}" \
  -H "Content-Type: application/json" \
  -d "{
    \"triggerType\": \"WEBHOOK\",
    \"environment\": \"${ENVIRONMENT}\",
    \"initialVariables\": {}
  }")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

# Handle timeout
if [ "$HTTP_CODE" -eq 504 ]; then
  echo "⚠️ Execution timed out waiting for completion after ${TIMEOUT_SEC}s."
  echo "   The execution continues running in the background."
  STATUS_URL=$(echo "$BODY" | grep -o '"statusUrl":"[^"]*' | cut -d'"' -f4 || true)
  if [ -n "$STATUS_URL" ]; then
    echo "   Track status at: ${STATUS_URL}"
  fi
  exit 1
elif [ "$HTTP_CODE" -ne 200 ]; then
  echo "❌ Workflow execution trigger failed (HTTP ${HTTP_CODE})"
  echo "   Payload: ${BODY}"
  exit 1
fi

# Extract execution properties
STATUS=$(echo "$BODY" | grep -o '"status":"[^"]*' | cut -d'"' -f4 || echo "UNKNOWN")
REPORT_URL=$(echo "$BODY" | grep -o '"reportUrl":"[^"]*' | cut -d'"' -f4 || echo "")
EXECUTION_ID=$(echo "$BODY" | grep -o '"id":"[^"]*' | cut -d'"' -f4 || echo "")

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Execution Finished: ${EXECUTION_ID}"
echo "   Final Status : ${STATUS}"
if [ -n "$REPORT_URL" ]; then
  echo "   Karate Report: ${REPORT_URL}"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ "$STATUS" != "COMPLETED" ]; then
  echo "❌ Workflow failed with status: ${STATUS}"
  exit 1
fi

echo "✅ Workflow completed successfully!"
exit 0
```

### Complete End-to-End CI/CD Bash Script Template (Asynchronous Mode + Polling)

```bash
#!/usr/bin/env bash
# ==============================================================================
# Automated CI/CD Polling Script for E2E Test Workflow Orchestrator
# Workflow: {{workflow.name}} (ID: {{workflow.id}})
# Mode: Asynchronous Trigger (HTTP 202) + Polling Status Loop
# ==============================================================================
set -euo pipefail

BASE_URL="${ORCHESTRATOR_URL:-{{origin}}}"
WORKFLOW_ID="{{workflow.id}}"
ENVIRONMENT="${TARGET_ENV:-{{selectedEnv}}}"
POLL_INTERVAL_SEC=3
MAX_POLLS=60

echo "🚀 Triggering asynchronous workflow execution (${WORKFLOW_ID})..."
RESPONSE=$(curl -s -S -w "\n%{http_code}" -X POST \
  "${BASE_URL}/api/v1/workflows/${WORKFLOW_ID}/execute" \
  -H "Content-Type: application/json" \
  -d "{
    \"triggerType\": \"WEBHOOK\",
    \"environment\": \"${ENVIRONMENT}\",
    \"initialVariables\": {}
  }")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -ne 202 ]; then
  echo "❌ Failed to trigger workflow execution (HTTP ${HTTP_CODE})"
  echo "   Payload: ${BODY}"
  exit 1
fi

EXECUTION_ID=$(echo "$BODY" | grep -o '"id":"[^"]*' | cut -d'"' -f4 || true)
STATUS_URL="${BASE_URL}/api/v1/executions/${EXECUTION_ID}"
REPORT_URL="${STATUS_URL}/report/workflow-report.html"

echo "⏳ Execution triggered [ID: ${EXECUTION_ID}]. Polling for completion..."
ATTEMPT=1
while [ "$ATTEMPT" -le "$MAX_POLLS" ]; do
  STATUS_RESP=$(curl -s -S "${STATUS_URL}" || echo "{}")
  STATUS=$(echo "$STATUS_RESP" | grep -o '"status":"[^"]*' | cut -d'"' -f4 || echo "RUNNING")
  
  if [ "$STATUS" = "COMPLETED" ]; then
    echo "✅ Workflow completed successfully!"
    echo "📊 Karate HTML Report: ${REPORT_URL}"
    exit 0
  elif [ "$STATUS" = "FAILED" ] || [ "$STATUS" = "CANCELLED" ]; then
    echo "❌ Workflow finished with status: ${STATUS}"
    echo "📊 Karate HTML Report: ${REPORT_URL}"
    exit 1
  fi
  
  echo "  [${ATTEMPT}/${MAX_POLLS}] Status: ${STATUS}. Waiting ${POLL_INTERVAL_SEC}s..."
  sleep "$POLL_INTERVAL_SEC"
  ATTEMPT=$((ATTEMPT + 1))
done

echo "⚠️ Polling timed out waiting for workflow completion."
echo "   Inspect run at: ${STATUS_URL}"
exit 1
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Modal displayed single static cURL async snippet | Mode switcher between Async (HTTP 202) and Sync (HTTP 200 with wait) | Phase 5 (Current) | Pipeline authors can directly copy blocking or non-blocking commands suited for their CI runner |
| Manual URL assembly for status checks | Explicit status polling cURL command and Karate HTML report URL templates displayed | Phase 5 (Current) | Eliminates guesswork and manual path construction for monitoring runs |
| Developers manually authored bash scripts | One-click copy of production-grade CI/CD bash script with exit code checks & report link | Phase 5 (Current) | Zero integration friction for GitHub Actions, GitLab CI, and Jenkins |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Developers prefer tabs/segmented toggles over long scrolling pages for switching between cURL and Bash script formats | Architecture Patterns | Low — tabs provide cleaner UX and prevent modal vertical clipping on smaller displays |
| A2 | POSIX grep/cut parameter extraction is preferable to requiring `jq` in generated bash scripts | Architecture Patterns | Low — POSIX extraction works universally on all runners, including minimal Alpine images |

## Open Questions

1. **Should the modal offer a custom timeout input for synchronous mode?**
   - What we know: Backend supports `?timeoutSec=N` defaulted to 60s, clamped between 1s and 300s (`WorkflowExecutionController.java:90`).
   - Recommendation: Provide a numeric input with default `60` (min: 1, max: 300) when synchronous mode is toggled, so users can configure expected run durations.
2. **Should the bash script support overriding URL and environment via env vars?**
   - What we know: CI/CD systems often inject environment variables like `ORCHESTRATOR_URL` or `TARGET_ENV`.
   - Recommendation: Use bash default parameter expansion `${ORCHESTRATOR_URL:-http://...}` and `${TARGET_ENV:-default}` with the modal's current values as defaults.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Frontend build & test | ✓ | v26.13.0 | — |
| npm | Dependency manager | ✓ | 10.9.2 | — |
| Git | Revision control | ✓ | 2.39.5 | — |
| Modern Browser | Clipboard API (`navigator.clipboard`) | ✓ | Evergreen (Chrome/Safari/Firefox/Edge) | Fallback text selection / textarea copy |

**Missing dependencies with no fallback:** None  
**Missing dependencies with fallback:** None  

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Node.js built-in `node:test` + `node:assert` (Zero external test runner dependencies) |
| Config file | `frontend/package.json` test script |
| Quick run command | `node --test tests/cicdSnippets.test.ts` (or `npm test` once wired) |
| Full suite command | `cd frontend && npm run build` (`tsc && vite build`) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CURL-01 | cURL status polling snippet and report URL generator produce exact valid formats | unit | `node --test tests/cicdSnippets.test.ts` | ❌ Wave 0 (`tests/cicdSnippets.test.ts`) |
| CURL-02 | Async vs Sync mode generator outputs correct query params (`?wait=true&timeoutSec=N`) and HTTP headers | unit | `node --test tests/cicdSnippets.test.ts` | ❌ Wave 0 (`tests/cicdSnippets.test.ts`) |
| CURL-03 | End-to-end CI/CD bash script contains valid syntax, handles 504 timeouts, exit codes, and prints report URL | unit | `node --test tests/cicdSnippets.test.ts` | ❌ Wave 0 (`tests/cicdSnippets.test.ts`) |
| ALL | Frontend TypeScript compiles cleanly with zero errors and builds production bundle | smoke / build | `cd frontend && npm run build` | ✅ Existing |

### Sampling Rate
- **Per task commit:** `cd frontend && npm run build`
- **Per wave merge:** `node --test tests/cicdSnippets.test.ts && cd frontend && npm run build`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `frontend/src/utils/cicdSnippets.ts` — Snippet formatting pure utility functions
- [ ] `tests/cicdSnippets.test.ts` — Unit tests for snippet formatting and script generation
- [ ] Framework install: None needed (`node:test` is built-in on Node 26)

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Webhook trigger endpoint currently public/internal; authentication out of scope for v1.1 |
| V3 Session Management | no | Stateless REST & Webhook endpoints |
| V4 Access Control | no | Handled at network / deployment perimeter |
| V5 Input Validation | yes | Client-side validation: `timeoutSec` clamped between 1 and 300; `selectedEnv` limited to whitelist (`default`, `dev`, `staging`, `prod`) |
| V6 Cryptography | no | No cryptographic operations in modal snippets |

### Known Threat Patterns for React & CI/CD Shell Snippets

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Shell Command Injection in generated script | Tampering / Elevation of Privilege | Properly quote and escape all workflow identifiers and environment variables in the bash script template; do not interpolate raw unescaped strings |
| XSS via workflow name in modal | Tampering | React JSX renders workflow name safely as text node (`{workflow.name}`) with automatic escaping; no `dangerouslySetInnerHTML` |
| Connection starvation via excessive timeout | Denial of Service | Frontend clamps `timeoutSec` input (1–300s); backend enforces `max-wait-timeout-sec` clamp |

## Sources

### Primary (HIGH confidence)
- `frontend/src/components/navbar/CiCdWebhookModal.tsx:1-135` — Current modal implementation inspected
- `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:59-111` — `executeWorkflow` with `?wait=true&timeoutSec=N`
- `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:127-136` — `getExecutionById` details endpoint
- `backend/src/main/java/com/orchestrator/platform/web/WorkflowExecutionController.java:184-242` — `serveReportAsset` Karate report endpoint
- `backend/src/main/java/com/orchestrator/platform/service/execution/ExecutionNavigationUrlBuilder.java:1-71` — Navigation URL builder logic
- `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionNavigationUrlTest.java:1-125` — EXEC-01, EXEC-02, EXEC-03 tests
- `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java:1-298` — SYNC-01, SYNC-02, SYNC-03 tests
- `frontend/package.json:1-35` — Frontend stack & scripts

### Secondary (MEDIUM confidence)
- Official Node.js Test Runner documentation (`node:test`, `node:assert`) for built-in unit testing without third-party frameworks

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Verified in `frontend/package.json`
- Architecture: HIGH — Verified in `WorkflowExecutionController.java` and `Navbar.tsx`
- Pitfalls: HIGH — Grounded in shell scripting and responsive React modal constraints

**Research date:** 2026-11-08  
**Valid until:** 2026-12-08 (Stable architecture)  
