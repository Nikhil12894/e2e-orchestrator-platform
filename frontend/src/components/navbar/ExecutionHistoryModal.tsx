import React from 'react';
import {
  X,
  History,
  CheckCircle2,
  XCircle,
  Clock,
  FileText,
  AlertCircle,
  Timer,
  ChevronRight,
  Server
} from 'lucide-react';
import { WorkflowExecution } from '../../types/workflow';

interface ExecutionHistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  workflowName: string;
  executions: WorkflowExecution[];
  onViewReport: (reportPath: string) => void;
}

export const ExecutionHistoryModal: React.FC<ExecutionHistoryModalProps> = ({
  isOpen,
  onClose,
  workflowName,
  executions,
  onViewReport,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in select-none">
      <div className="relative w-full max-w-3xl max-h-[85vh] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden text-slate-800 dark:text-slate-100 transition-colors">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/70">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-blue-600/10 text-blue-600 dark:text-blue-400 border border-blue-500/20">
              <History className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-base text-slate-900 dark:text-slate-100">Execution Runs & Reports</h3>
                <span className="px-2 py-0.5 rounded-full text-xs font-mono font-bold bg-blue-500/20 text-blue-600 dark:text-blue-400 border border-blue-500/30">
                  {executions.length} {executions.length === 1 ? 'run' : 'runs'}
                </span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 truncate max-w-lg">
                Workflow: <span className="text-slate-800 dark:text-slate-200 font-medium">{workflowName}</span>
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

        {/* Content list */}
        <div className="flex-1 overflow-y-auto p-6 space-y-3">
          {executions.length === 0 ? (
            <div className="text-center py-16">
              <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800/80 flex items-center justify-center mx-auto mb-3 text-slate-400 dark:text-slate-500">
                <History className="w-6 h-6" />
              </div>
              <h4 className="font-medium text-sm text-slate-700 dark:text-slate-300">No execution history found</h4>
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                Run this workflow to generate execution telemetry and profiling reports.
              </p>
            </div>
          ) : (
            executions.map((exec) => {
              const isCompleted = exec.status === 'COMPLETED';
              const isFailed = exec.status === 'FAILED';
              const isRunning = exec.status === 'RUNNING' || exec.status === 'PENDING';
              const dateStr = exec.createdAt
                ? new Date(exec.createdAt).toLocaleString(undefined, {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                  })
                : 'Just now';

              return (
                <div
                  key={exec.id}
                  className="flex items-center justify-between p-4 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800/80 hover:border-slate-300 dark:hover:border-slate-700 transition-all gap-4 shadow-sm"
                >
                  <div className="flex items-center gap-3.5 min-w-0">
                    {isCompleted ? (
                      <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 shrink-0">
                        <CheckCircle2 className="w-4 h-4" />
                      </div>
                    ) : isFailed ? (
                      <div className="p-2 rounded-lg bg-rose-500/10 text-rose-600 dark:text-rose-400 shrink-0">
                        <XCircle className="w-4 h-4" />
                      </div>
                    ) : (
                      <div className="p-2 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400 shrink-0 animate-pulse">
                        <Timer className="w-4 h-4" />
                      </div>
                    )}

                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-semibold text-slate-800 dark:text-slate-200">
                          {exec.id.substring(0, 8)}
                        </span>
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            isCompleted
                              ? 'bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 border border-emerald-500/30'
                              : isFailed
                              ? 'bg-rose-500/20 text-rose-600 dark:text-rose-400 border border-rose-500/30'
                              : 'bg-blue-500/20 text-blue-600 dark:text-blue-400 border border-blue-500/30 animate-pulse'
                          }`}
                        >
                          {exec.status}
                        </span>
                        {exec.triggerType && (
                          <span className="text-[10px] font-mono text-slate-500 bg-slate-200 dark:bg-slate-900 px-1.5 py-0.5 rounded border border-slate-300 dark:border-slate-800">
                            {exec.triggerType}
                          </span>
                        )}
                      </div>

                      <div className="flex items-center gap-3 mt-1 text-[11px] text-slate-500 dark:text-slate-400">
                        <span>{dateStr}</span>
                        {exec.durationMs !== undefined && exec.durationMs !== null && (
                          <span className="flex items-center gap-1 font-mono text-slate-700 dark:text-slate-300">
                            <Clock className="w-3 h-3 text-slate-400" />
                            {exec.durationMs}ms
                          </span>
                        )}
                        {exec.stepResults && exec.stepResults.length > 0 && (
                          <span className="text-slate-500 dark:text-slate-400 font-mono">
                            {exec.stepResults.length} {exec.stepResults.length === 1 ? 'step' : 'steps'}
                          </span>
                        )}
                      </div>

                      {exec.errorMessage && (
                        <p className="mt-1 text-[11px] text-rose-500 dark:text-rose-400 line-clamp-1 font-mono">
                          {exec.errorMessage}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="shrink-0">
                    {exec.reportPath ? (
                      <button
                        onClick={() => {
                          onViewReport(exec.reportPath!);
                          onClose();
                        }}
                        className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-600/10 dark:bg-emerald-600/20 hover:bg-emerald-600/20 dark:hover:bg-emerald-600/30 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30 text-xs font-semibold transition-all shadow-sm"
                      >
                        <FileText className="w-3.5 h-3.5" />
                        <span>View Report</span>
                        <ChevronRight className="w-3.5 h-3.5 opacity-60" />
                      </button>
                    ) : (
                      <span className="text-xs text-slate-400 dark:text-slate-600 italic">No report</span>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-3 border-t border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/50 text-xs text-slate-500">
          <span>Click "View Report" on any run to inspect the profiling report and Karate summary.</span>
          <button
            onClick={onClose}
            className="px-3.5 py-1.5 rounded-lg bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-800 dark:text-slate-200 text-xs font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
