import React, { useState, useEffect, useRef } from 'react';
import {
  Terminal,
  ChevronDown,
  ChevronUp,
  CheckCircle2,
  XCircle,
  Clock,
  FileText,
  Trash2,
  Maximize2,
  Globe,
  Database,
  Radio,
  Timer,
  AlertTriangle
} from 'lucide-react';
import { WorkflowExecution, ExecutionEventDto, ExecutionStepResult } from '../../types/workflow';

interface LiveExecutionConsoleProps {
  execution: WorkflowExecution | null;
  events: ExecutionEventDto[];
  isOpen: boolean;
  onToggle: () => void;
  onViewReport: (url: string) => void;
}

export const LiveExecutionConsole: React.FC<LiveExecutionConsoleProps> = ({
  execution,
  events,
  isOpen,
  onToggle,
  onViewReport,
}) => {
  const [activeTab, setActiveTab] = useState<'steps' | 'logs' | 'context'>('steps');
  const [selectedStepResult, setSelectedStepResult] = useState<ExecutionStepResult | null>(null);
  const logEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (activeTab === 'logs' && logEndRef.current) {
      logEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [events, activeTab]);

  const isRunning = execution?.status === 'RUNNING' || execution?.status === 'PENDING';
  const isCompleted = execution?.status === 'COMPLETED';
  const isFailed = execution?.status === 'FAILED';

  const getStatusBadgeClass = (status?: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
      case 'FAILED':
        return 'bg-rose-500/20 text-rose-400 border border-rose-500/30';
      case 'RUNNING':
        return 'bg-blue-500/20 text-blue-400 border border-blue-500/30 animate-pulse';
      case 'PENDING':
        return 'bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse';
      default:
        return 'bg-slate-800 text-slate-400 border border-slate-700';
    }
  };

  if (!isOpen) {
    return (
      <div className="fixed bottom-0 right-6 z-30">
        <button
          onClick={onToggle}
          className="flex items-center gap-2 px-4 py-2 bg-slate-900 border border-slate-800 rounded-t-xl text-xs font-semibold text-slate-200 hover:text-white shadow-2xl transition-all"
        >
          <Terminal className="w-4 h-4 text-emerald-400" />
          <span>Execution Console</span>
          {execution && (
            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${getStatusBadgeClass(execution.status)}`}>
              {execution.status}
            </span>
          )}
          <ChevronUp className="w-3.5 h-3.5 text-slate-400" />
        </button>
      </div>
    );
  }

  return (
    <div className="fixed bottom-0 inset-x-0 z-30 h-80 bg-slate-900 border-t border-slate-800 shadow-2xl flex flex-col select-none">
      {/* Console Header */}
      <div className="flex items-center justify-between px-5 py-2.5 border-b border-slate-800 bg-slate-950/80">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-emerald-400" />
            <h3 className="font-semibold text-xs text-slate-100">Live Execution Console</h3>
          </div>

          {execution && (
            <div className="flex items-center gap-2 font-mono text-[11px]">
              <span className="text-slate-500">Run:</span>
              <span className="text-slate-300 font-bold">{execution.id.substring(0, 8)}...</span>
              <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${getStatusBadgeClass(execution.status)}`}>
                {execution.status}
              </span>
              {execution.durationMs !== undefined && execution.durationMs !== null && (
                <span className="text-slate-400 flex items-center gap-1 font-mono">
                  <Clock className="w-3 h-3 text-slate-500" />
                  {execution.durationMs}ms
                </span>
              )}
            </div>
          )}
        </div>

        {/* Console Action Bar */}
        <div className="flex items-center gap-2">
          {execution?.reportPath && (
            <button
              onClick={() => onViewReport(execution.reportPath!)}
              className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-emerald-600/30 hover:bg-emerald-600/40 text-emerald-200 border border-emerald-500/40 text-xs font-semibold transition-all shadow-lg shadow-emerald-900/30"
            >
              <FileText className="w-3.5 h-3.5 text-emerald-400" />
              View Execution & Profiling Report
            </button>
          )}

          <div className="flex items-center bg-slate-900 rounded-lg border border-slate-800 p-0.5">
            <button
              onClick={() => setActiveTab('steps')}
              className={`px-3 py-1 rounded text-xs font-medium transition-colors ${
                activeTab === 'steps' ? 'bg-slate-800 text-blue-400' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Steps ({execution?.stepResults?.length || 0})
            </button>
            <button
              onClick={() => setActiveTab('logs')}
              className={`px-3 py-1 rounded text-xs font-medium transition-colors ${
                activeTab === 'logs' ? 'bg-slate-800 text-blue-400' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Event Log ({events.length})
            </button>
            <button
              onClick={() => setActiveTab('context')}
              className={`px-3 py-1 rounded text-xs font-medium transition-colors ${
                activeTab === 'context' ? 'bg-slate-800 text-blue-400' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Context
            </button>
          </div>

          <button
            onClick={onToggle}
            className="p-1 rounded text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
          >
            <ChevronDown className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Console Body */}
      <div className="flex-1 overflow-hidden flex bg-slate-950/40">
        {activeTab === 'steps' && (
          <div className="flex-1 flex overflow-hidden">
            {/* Step list */}
            <div className="w-1/2 border-r border-slate-800 p-3 overflow-y-auto space-y-1.5">
              {!execution?.stepResults || execution.stepResults.length === 0 ? (
                <div className="text-center py-10 text-slate-500 text-xs">
                  {isRunning ? 'Execution in progress... waiting for step results' : 'No steps recorded for this execution'}
                </div>
              ) : (
                execution.stepResults.map((step) => {
                  const isSelected = selectedStepResult?.stepId === step.stepId;
                  return (
                    <div
                      key={step.stepId}
                      onClick={() => setSelectedStepResult(step)}
                      className={`flex items-center justify-between p-2.5 rounded-lg border cursor-pointer transition-all ${
                        isSelected
                          ? 'bg-blue-500/10 border-blue-500/40 text-blue-300'
                          : 'bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        {step.status === 'COMPLETED' ? (
                          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                        ) : (
                          <XCircle className="w-4 h-4 text-rose-400 shrink-0" />
                        )}
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="font-semibold text-xs block truncate">{step.stepName}</span>
                            {step.excludeFromReport && (
                              <span className="text-[9px] font-mono px-1 py-0.2 rounded bg-amber-500/15 text-amber-400 border border-amber-500/30 shrink-0">
                                Auxiliary
                              </span>
                            )}
                          </div>
                          <span className="text-[10px] font-mono text-slate-500">{step.stepType}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 text-right">
                        {step.httpStatus && (
                          <span
                            className={`px-1.5 py-0.5 rounded text-[10px] font-mono font-bold ${
                              step.httpStatus < 400 ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'
                            }`}
                          >
                            {step.httpStatus}
                          </span>
                        )}
                        <span className="text-[10px] font-mono text-slate-500">{step.durationMs}ms</span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Selected step details */}
            <div className="w-1/2 p-3 overflow-y-auto font-mono text-xs">
              {selectedStepResult ? (
                <div className="space-y-3">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                        Step: {selectedStepResult.stepName} ({selectedStepResult.status})
                      </span>
                      {selectedStepResult.excludeFromReport && (
                        <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-amber-500/15 text-amber-400 border border-amber-500/30">
                          Auxiliary (Uncounted in Report)
                        </span>
                      )}
                    </div>
                    {selectedStepResult.errorMessage && (
                      <div className="p-2 rounded bg-rose-500/10 border border-rose-500/30 text-rose-400 text-[11px] mb-2">
                        {selectedStepResult.errorMessage}
                      </div>
                    )}
                  </div>

                  {/* Assertion details */}
                  {selectedStepResult.assertionDetails && selectedStepResult.assertionDetails.length > 0 && (
                    <div>
                      <span className="text-[10px] text-slate-500 uppercase block mb-1">Assertions Evaluated:</span>
                      <div className="space-y-1">
                        {selectedStepResult.assertionDetails.map((det, i) => (
                          <div
                            key={i}
                            className={`p-1.5 rounded text-[11px] ${
                              det.includes('[PASS]') ? 'bg-emerald-500/10 text-emerald-300' : 'bg-rose-500/10 text-rose-300'
                            }`}
                          >
                            {det}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Extracted Variables */}
                  {selectedStepResult.extractedVariables && Object.keys(selectedStepResult.extractedVariables).length > 0 && (
                    <div>
                      <span className="text-[10px] text-slate-500 uppercase block mb-1">Extracted Variables:</span>
                      <pre className="p-2 rounded bg-slate-900 border border-slate-800 text-emerald-400 text-[11px] overflow-x-auto">
                        {JSON.stringify(selectedStepResult.extractedVariables, null, 2)}
                      </pre>
                    </div>
                  )}

                  {/* Response Payload */}
                  {selectedStepResult.responsePayload && (
                    <div>
                      <span className="text-[10px] text-slate-500 uppercase block mb-1">Response Payload:</span>
                      <pre className="p-2 rounded bg-slate-900 border border-slate-800 text-slate-300 text-[11px] overflow-x-auto max-h-48">
                        {JSON.stringify(selectedStepResult.responsePayload, null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center py-10 text-slate-500 text-xs">
                  Click on any step on the left to inspect its response payload and assertion details
                </div>
              )}
            </div>
          </div>
        )}

        {/* Real-time Event Log */}
        {activeTab === 'logs' && (
          <div className="flex-1 p-3 overflow-y-auto font-mono text-xs space-y-1">
            {events.length === 0 ? (
              <div className="text-center py-10 text-slate-500">Waiting for live events...</div>
            ) : (
              events.map((evt, idx) => (
                <div key={idx} className="flex items-start gap-2 py-0.5 leading-relaxed hover:bg-slate-900/40 px-1 rounded">
                  <span className="text-slate-600 text-[11px] shrink-0">
                    {new Date(evt.timestamp).toLocaleTimeString()}
                  </span>
                  <span
                    className={`px-1.5 py-0.2 rounded text-[9px] font-bold shrink-0 ${
                      evt.eventType === 'WORKFLOW_STARTED'
                        ? 'bg-purple-500/20 text-purple-400'
                        : evt.eventType === 'STEP_STARTED'
                        ? 'bg-blue-500/20 text-blue-400'
                        : evt.eventType === 'STEP_FINISHED'
                        ? evt.status === 'COMPLETED'
                          ? 'bg-emerald-500/20 text-emerald-400'
                          : 'bg-rose-500/20 text-rose-400'
                        : evt.eventType === 'WORKFLOW_FINISHED'
                        ? 'bg-emerald-500/20 text-emerald-400'
                        : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    {evt.eventType}
                  </span>
                  <span className="text-slate-300 break-all">{evt.message}</span>
                </div>
              ))
            )}
            <div ref={logEndRef} />
          </div>
        )}

        {/* Context Snapshot */}
        {activeTab === 'context' && (
          <div className="flex-1 p-3 overflow-y-auto font-mono text-xs">
            {execution?.contextSnapshot && Object.keys(execution.contextSnapshot).length > 0 ? (
              <pre className="p-3 bg-slate-900 border border-slate-800 rounded-lg text-emerald-400 text-xs overflow-x-auto">
                {JSON.stringify(execution.contextSnapshot, null, 2)}
              </pre>
            ) : (
              <div className="text-center py-10 text-slate-500">No runtime variables in context snapshot</div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
