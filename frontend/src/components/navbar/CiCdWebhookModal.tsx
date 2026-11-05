import React, { useState } from 'react';
import { X, Terminal, Copy, Check, ExternalLink, ShieldCheck } from 'lucide-react';
import { TestWorkflow } from '../../types/workflow';

interface CiCdWebhookModalProps {
  isOpen: boolean;
  onClose: () => void;
  workflow: TestWorkflow;
  currentEnvironment?: string;
}

export const CiCdWebhookModal: React.FC<CiCdWebhookModalProps> = ({
  isOpen,
  onClose,
  workflow,
  currentEnvironment = 'default',
}) => {
  const [selectedEnv, setSelectedEnv] = useState(currentEnvironment);
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const origin = window.location.origin;
  const curlCommand = `curl -X POST "${origin}/api/v1/workflows/${workflow.id}/execute" \\
  -H "Content-Type: application/json" \\
  -d '{
    "triggerType": "WEBHOOK",
    "environment": "${selectedEnv}",
    "initialVariables": {}
  }'`;

  const handleCopy = () => {
    navigator.clipboard.writeText(curlCommand);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in select-none">
      <div className="relative w-full max-w-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden text-slate-800 dark:text-slate-100 transition-colors">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/70">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-blue-600/10 text-blue-600 dark:text-blue-400 border border-blue-500/20">
              <Terminal className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-base text-slate-900 dark:text-slate-100">
                CI/CD Webhook & cURL Trigger
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Trigger <span className="font-medium text-slate-800 dark:text-slate-200">"{workflow.name}"</span> from GitHub Actions, GitLab CI, or Jenkins.
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-4">
          {/* Environment selection */}
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
              Target Execution Environment:
            </label>
            <select
              value={selectedEnv}
              onChange={(e) => setSelectedEnv(e.target.value)}
              className="px-3 py-1.5 bg-slate-100 dark:bg-slate-950 border border-slate-300 dark:border-slate-800 rounded-lg text-xs font-mono font-medium text-slate-800 dark:text-slate-200 focus:outline-none focus:border-blue-500"
            >
              <option value="default">default</option>
              <option value="dev">dev</option>
              <option value="staging">staging</option>
              <option value="prod">prod</option>
            </select>
          </div>

          {/* Code snippet block */}
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

          {/* Instructions note */}
          <div className="p-3.5 rounded-xl bg-blue-50 dark:bg-blue-950/30 border border-blue-200 dark:border-blue-900/50 flex items-start gap-3 text-xs text-blue-700 dark:text-blue-300">
            <ShieldCheck className="w-4 h-4 text-blue-500 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Automated Virtual Thread Execution (HTTP 202)</p>
              <p className="text-[11px] text-blue-600/90 dark:text-blue-300/80 mt-0.5 leading-relaxed">
                Calling this endpoint immediately kicks off execution on an isolated Java 25 virtual thread. It returns an HTTP 202 Accepted response containing the new execution ID, streaming SSE endpoint, and Karate HTML report link.
              </p>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/50">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-800 dark:text-slate-200 text-xs font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
