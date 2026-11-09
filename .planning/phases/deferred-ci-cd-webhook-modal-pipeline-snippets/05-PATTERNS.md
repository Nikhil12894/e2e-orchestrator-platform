# Phase 5: CI/CD Webhook Modal & Pipeline Snippets - Pattern Map

**Mapped:** 2026-11-08  
**Files analyzed:** 3  
**Analogs found:** 3 / 3  

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `frontend/src/utils/cicdSnippets.ts` | utility | transform | `frontend/src/utils/layoutEngine.ts` | exact |
| `frontend/src/components/navbar/CiCdWebhookModal.tsx` | component | request-response | `frontend/src/components/navbar/WorkflowSettingsModal.tsx` & `frontend/src/components/navbar/CiCdWebhookModal.tsx` | exact |
| `tests/cicdSnippets.test.ts` | test | request-response | `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java` | role-match |

---

## Pattern Assignments

### `frontend/src/utils/cicdSnippets.ts` (utility, transform)

**Analog:** `frontend/src/utils/layoutEngine.ts`

**Imports & Options Interface pattern** (analog lines 1-9 in `frontend/src/utils/layoutEngine.ts`):
```typescript
// [ANALOG: frontend/src/utils/layoutEngine.ts:1-9]
import { Node, Edge } from '@xyflow/react';

export interface LayoutOptions {
  direction?: 'LR' | 'TB';
  nodeWidth?: number;
  nodeHeight?: number;
  horizontalSpacing?: number;
  verticalSpacing?: number;
}
```
*Adaptation for `cicdSnippets.ts`:*
```typescript
export interface SnippetOptions {
  origin: string;
  workflowId: string;
  workflowName: string;
  environment?: string;
  timeoutSec?: number;
}
```

**Core Transform & Pure Calculation pattern** (analog lines 15-26, 120-124 in `frontend/src/utils/layoutEngine.ts`):
```typescript
// [ANALOG: frontend/src/utils/layoutEngine.ts:15-26, 120-124]
export function getAutoLayoutedNodes(
  nodes: Node[],
  edges: Edge[],
  options: LayoutOptions = {}
): Node[] {
  if (nodes.length === 0) return nodes;

  const {
    direction = 'LR',
    horizontalSpacing = 340,
    verticalSpacing = 160,
  } = options;
  ...
  position: { x: Math.max(50, Math.round(x)), y: Math.max(50, Math.round(y)) }
```
*Adaptation for `cicdSnippets.ts`:*
Export pure, standalone functions with default parameter destructuring, input clamping, and deterministic string generation:
- `generateAsyncCurlSnippet(options: SnippetOptions): string`
- `generateSyncCurlSnippet(options: SnippetOptions): string`
- `generateStatusPollSnippet(origin: string, executionIdOrPlaceholder?: string): string`
- `generateKarateReportUrl(origin: string, executionIdOrPlaceholder?: string): string`
- `generateCiCdBashScript(options: SnippetOptions, mode: 'async' | 'sync'): string`

**Validation & Sanitization pattern**:
Clamp `timeoutSec` to `[1, 300]` (analogous to `Math.max(50, Math.round(x))` in `layoutEngine.ts:123`):
```typescript
export function sanitizeTimeoutSec(timeoutSec?: number): number {
  if (typeof timeoutSec !== 'number' || isNaN(timeoutSec)) return 60;
  return Math.max(1, Math.min(300, Math.round(timeoutSec)));
}

export function sanitizeOrigin(origin: string): string {
  return (origin || '').replace(/\/+$/, '');
}
```

---

### `frontend/src/components/navbar/CiCdWebhookModal.tsx` (component, request-response)

**Analog:** `frontend/src/components/navbar/WorkflowSettingsModal.tsx` (for multi-tab segmented controls and dark theme styling) & `frontend/src/components/navbar/CiCdWebhookModal.tsx:1-135` (existing modal component)

**Imports pattern** (analog lines 1-16 in `frontend/src/components/navbar/WorkflowSettingsModal.tsx` and lines 1-3 in `CiCdWebhookModal.tsx`):
```typescript
// [ANALOG: frontend/src/components/navbar/CiCdWebhookModal.tsx:1-3]
import React, { useState } from 'react';
import { X, Terminal, Copy, Check, ExternalLink, ShieldCheck, Clock, PlayCircle } from 'lucide-react';
import { TestWorkflow } from '../../types/workflow';
```

**Props Interface pattern** (preserving compatibility with `Navbar.tsx:499-504`):
```typescript
// [ANALOG: frontend/src/components/navbar/CiCdWebhookModal.tsx:5-10]
export interface CiCdWebhookModalProps {
  isOpen: boolean;
  onClose: () => void;
  workflow: TestWorkflow;
  currentEnvironment?: string;
}
```

**Modal Backdrop & Container Shell pattern** (analog lines 174-200 in `frontend/src/components/navbar/WorkflowSettingsModal.tsx`):
```typescript
// [ANALOG: frontend/src/components/navbar/WorkflowSettingsModal.tsx:174-177]
<div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none">
  <div className="w-full max-w-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden animate-in fade-in zoom-in-95 duration-150 text-slate-800 dark:text-slate-100 transition-colors">
```

**Execution Mode Segmented Switcher pattern** (analog lines 718-742 in `frontend/src/components/navbar/WorkflowSettingsModal.tsx`):
```typescript
// [ANALOG: frontend/src/components/navbar/WorkflowSettingsModal.tsx:718-742]
<div className="flex items-center gap-1.5 p-1 bg-slate-950/40 border border-slate-800 rounded-lg">
  {(['default', 'staging', 'production'] as const).map((env) => (
    <button
      key={env}
      type="button"
      onClick={() => setSelectedSecretEnv(env)}
      className={`flex-1 py-1.5 px-3 rounded-md text-xs font-medium transition-all flex items-center justify-center gap-2 ${
        selectedSecretEnv === env
          ? 'bg-rose-600 text-white shadow-sm'
          : 'text-slate-400 hover:text-slate-200'
      }`}
    >
      <span className="capitalize">{env}</span>
    </button>
  ))}
</div>
```
*Adaptation for `CiCdWebhookModal.tsx`:*
```typescript
<div className="flex items-center gap-1.5 p-1 bg-slate-100 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg">
  <button
    type="button"
    onClick={() => setExecutionMode('async')}
    className={`flex-1 py-1.5 px-3 rounded-md text-xs font-medium transition-all flex items-center justify-center gap-2 ${
      executionMode === 'async'
        ? 'bg-blue-600 text-white shadow-sm font-semibold'
        : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
    }`}
  >
    <PlayCircle className="w-3.5 h-3.5" />
    <span>Asynchronous (HTTP 202)</span>
  </button>
  <button
    type="button"
    onClick={() => setExecutionMode('sync')}
    className={`flex-1 py-1.5 px-3 rounded-md text-xs font-medium transition-all flex items-center justify-center gap-2 ${
      executionMode === 'sync'
        ? 'bg-blue-600 text-white shadow-sm font-semibold'
        : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
    }`}
  >
    <Clock className="w-3.5 h-3.5" />
    <span>Synchronous (HTTP 200 with Wait)</span>
  </button>
</div>
```

**Navigation Tab Switcher pattern** (analog lines 203-228 in `frontend/src/components/navbar/WorkflowSettingsModal.tsx`):
```typescript
// [ANALOG: frontend/src/components/navbar/WorkflowSettingsModal.tsx:203-228]
<div className="flex border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/40 px-6 pt-1 gap-1">
  <button
    onClick={() => setActiveTab('general')}
    className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
      activeTab === 'general'
        ? 'border-blue-500 text-blue-600 dark:text-blue-400 font-semibold'
        : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
    }`}
  >
    ...
```

**Copyable Code Card pattern** (analog lines 85-108 in `frontend/src/components/navbar/CiCdWebhookModal.tsx`):
```typescript
// [ANALOG: frontend/src/components/navbar/CiCdWebhookModal.tsx:85-108]
<div className="relative rounded-xl bg-slate-950 border border-slate-800 overflow-hidden shadow-inner">
  <div className="flex items-center justify-between px-4 py-2 border-b border-slate-800/80 bg-slate-900/50 text-[11px] text-slate-400">
    <span className="font-mono">cURL Bash Snippet</span>
    <button
      onClick={handleCopy}
      className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 border border-blue-500/30 font-medium transition-all"
    >
      {copied ? (
        <>
          <Check className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-emerald-400">Copied!</span>
        </>
      ) : (
        <>
          <Copy className="w-3.5 h-3.5" />
          <span>Copy Command</span>
        </>
      )}
    </button>
  </div>
  <pre className="p-4 text-xs font-mono text-emerald-400 dark:text-emerald-300 overflow-x-auto leading-relaxed select-all">
    {curlCommand}
  </pre>
</div>
```

**Clipboard Copy & Error Fallback pattern** (enhanced from `CiCdWebhookModal.tsx:32-36`):
```typescript
const [copiedKey, setCopiedKey] = useState<string | null>(null);

const handleCopy = async (key: string, text: string) => {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      // Fallback for non-secure HTTP contexts
      const textArea = document.createElement('textarea');
      textArea.value = text;
      textArea.style.position = 'fixed';
      textArea.style.opacity = '0';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();
      document.execCommand('copy');
      document.body.removeChild(textArea);
    }
    setCopiedKey(key);
    setTimeout(() => setCopiedKey((curr) => (curr === key ? null : curr)), 2000);
  } catch (err) {
    console.error('Failed to copy text to clipboard', err);
  }
};
```

---

### `tests/cicdSnippets.test.ts` (test, request-response)

**Analog:** `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java` (spec & contracts) & Node 26 native `node:test` + `node:assert/strict`

**Test Structure & Contract Alignment** (analog lines 138-163 in `backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java`):
```java
// [ANALOG: backend/src/test/java/com/orchestrator/platform/WorkflowExecutionSyncModeTest.java:141-150]
mockMvc.perform(post("/api/v1/workflows/{id}/execute?wait=true&timeoutSec=30", fastWorkflow.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WorkflowExecutionRequest())))
        .andExpect(status().isOk())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.status", is("COMPLETED")))
        .andExpect(jsonPath("$.reportUrl", notNullValue()))
        .andExpect(jsonPath("$.statusUrl", notNullValue()));
```

*Implementation pattern for `tests/cicdSnippets.test.ts`:*
```typescript
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  generateAsyncCurlSnippet,
  generateSyncCurlSnippet,
  generateStatusPollSnippet,
  generateKarateReportUrl,
  generateCiCdBashScript,
  sanitizeTimeoutSec,
} from '../frontend/src/utils/cicdSnippets.ts';

describe('CI/CD Snippet Utilities', () => {
  const mockOptions = {
    origin: 'http://localhost:8080',
    workflowId: '123e4567-e89b-12d3-a456-426614174000',
    workflowName: 'E2E Checkout Flow',
    environment: 'staging',
    timeoutSec: 45,
  };

  describe('CURL-01: Status Polling & Report URL Generation', () => {
    it('generates status polling snippet with correct GET endpoint', () => {
      const snippet = generateStatusPollSnippet(mockOptions.origin, mockOptions.workflowId);
      assert.ok(snippet.includes('GET'));
      assert.ok(snippet.includes('http://localhost:8080/api/v1/executions/123e4567-e89b-12d3-a456-426614174000'));
    });

    it('generates Karate HTML report URL format matching backend ExecutionNavigationUrlBuilder', () => {
      const reportUrl = generateKarateReportUrl(mockOptions.origin, mockOptions.workflowId);
      assert.equal(
        reportUrl,
        'http://localhost:8080/api/v1/executions/123e4567-e89b-12d3-a456-426614174000/report/workflow-report.html'
      );
    });
  });

  describe('CURL-02: Async vs Sync cURL Command Formatting', () => {
    it('generates asynchronous trigger snippet returning HTTP 202 without wait parameters', () => {
      const snippet = generateAsyncCurlSnippet(mockOptions);
      assert.ok(snippet.includes('POST'));
      assert.ok(snippet.includes('/api/v1/workflows/123e4567-e89b-12d3-a456-426614174000/execute"'));
      assert.ok(!snippet.includes('wait=true'));
      assert.ok(snippet.includes('"environment": "staging"'));
    });

    it('generates synchronous trigger snippet with wait=true and timeoutSec parameters', () => {
      const snippet = generateSyncCurlSnippet(mockOptions);
      assert.ok(snippet.includes('?wait=true&timeoutSec=45'));
    });

    it('clamps timeoutSec between 1s and 300s', () => {
      assert.equal(sanitizeTimeoutSec(-10), 1);
      assert.equal(sanitizeTimeoutSec(0), 1);
      assert.equal(sanitizeTimeoutSec(500), 300);
      assert.equal(sanitizeTimeoutSec(60), 60);
    });
  });

  describe('CURL-03: CI/CD Pipeline Bash Script Generation', () => {
    it('generates valid POSIX bash script with set -euo pipefail', () => {
      const script = generateCiCdBashScript(mockOptions, 'sync');
      assert.ok(script.startsWith('#!/usr/bin/env bash'));
      assert.ok(script.includes('set -euo pipefail'));
      assert.ok(script.includes('ORCHESTRATOR_URL:-http://localhost:8080'));
    });

    it('handles HTTP 504 gateway timeout and inspects statusUrl without crashing', () => {
      const script = generateCiCdBashScript(mockOptions, 'sync');
      assert.ok(script.includes('[ "$HTTP_CODE" -eq 504 ]'));
      assert.ok(script.includes('statusUrl'));
    });

    it('checks for COMPLETED status and outputs Karate report URL', () => {
      const script = generateCiCdBashScript(mockOptions, 'sync');
      assert.ok(script.includes('[ "$STATUS" != "COMPLETED" ]'));
      assert.ok(script.includes('Karate Report:'));
    });
  });
});
```

---

## Shared Patterns

### Modal Dialog Shell & Backdrop
**Source:** `frontend/src/components/navbar/WorkflowSettingsModal.tsx:174-200`  
**Apply to:** `frontend/src/components/navbar/CiCdWebhookModal.tsx`  
```typescript
<div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none">
  <div className="relative w-full max-w-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden text-slate-800 dark:text-slate-100 transition-colors animate-in fade-in zoom-in-95 duration-150">
    {/* Header */}
    {/* Body with overflow-y-auto */}
    {/* Footer */}
  </div>
</div>
```

### Segmented Pill Control
**Source:** `frontend/src/components/navbar/WorkflowSettingsModal.tsx:718-742`  
**Apply to:** Mode switcher in `CiCdWebhookModal.tsx`  
```typescript
<div className="flex items-center gap-1.5 p-1 bg-slate-100 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg">
  {options.map((opt) => (
    <button
      key={opt.id}
      type="button"
      onClick={() => setSelected(opt.id)}
      className={`flex-1 py-1.5 px-3 rounded-md text-xs font-medium transition-all flex items-center justify-center gap-2 ${
        selected === opt.id
          ? 'bg-blue-600 text-white shadow-sm font-semibold'
          : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
      }`}
    >
      {opt.label}
    </button>
  ))}
</div>
```

### Monospace Code Block with Top Bar Copy Action
**Source:** `frontend/src/components/navbar/CiCdWebhookModal.tsx:85-108`  
**Apply to:** All snippet display cards (Trigger cURL, Status Poll, Karate Report URL, CI/CD Script)  
```typescript
<div className="relative rounded-xl bg-slate-950 border border-slate-800 overflow-hidden shadow-inner">
  <div className="flex items-center justify-between px-4 py-2 border-b border-slate-800/80 bg-slate-900/50 text-[11px] text-slate-400">
    <span className="font-mono">{cardTitle}</span>
    <button
      onClick={() => handleCopy(snippetKey, snippetContent)}
      aria-label={copiedKey === snippetKey ? 'Copied to clipboard' : copyButtonLabel}
      className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 border border-blue-500/30 font-medium transition-all"
    >
      {copiedKey === snippetKey ? (
        <>
          <Check className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-emerald-400" aria-live="polite">Copied!</span>
        </>
      ) : (
        <>
          <Copy className="w-3.5 h-3.5" />
          <span>{copyButtonLabel}</span>
        </>
      )}
    </button>
  </div>
  <pre className="p-4 text-xs font-mono text-emerald-400 dark:text-emerald-300 overflow-x-auto leading-relaxed select-all">
    {snippetContent}
  </pre>
</div>
```

---

## No Analog Found

None. All files have matching tracked analogs in the codebase.

---

## Metadata

**Analog search scope:**
- `frontend/src/utils/`
- `frontend/src/components/navbar/`
- `frontend/src/components/console/`
- `backend/src/test/java/com/orchestrator/platform/`

**Files scanned:** 12  
**Pattern extraction date:** 2026-11-08  
