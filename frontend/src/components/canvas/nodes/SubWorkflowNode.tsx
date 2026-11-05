import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Workflow, CheckCircle2, XCircle, EyeOff, Layers, ArrowRight } from 'lucide-react';
import { CanvasNodeData } from '../../../types/workflow';

export const SubWorkflowNode = memo(({ data, selected }: NodeProps<any>) => {
  const nodeData = data as CanvasNodeData;
  const status = nodeData.status;
  const isMuted = nodeData.enabled === false;
  const targetWfName = nodeData.workflowName || (nodeData.workflowId ? `Workflow [${nodeData.workflowId.substring(0, 8)}...]` : null);
  const inputCount = Object.keys(nodeData.inputVariables || {}).length;
  const extractionCount = (nodeData.extractions || []).length;

  return (
    <div
      className={`relative w-72 rounded-xl bg-white/95 dark:bg-slate-900/90 border p-3.5 shadow-lg backdrop-blur-md transition-all ${
        isMuted ? 'opacity-60' : ''
      } ${
        selected
          ? 'border-indigo-500 ring-2 ring-indigo-500/30'
          : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-indigo-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900"
      />

      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-500 dark:text-indigo-400">
            <Workflow className="w-4 h-4" />
          </div>
          <div className="min-w-0">
            <span className="font-semibold text-xs text-slate-800 dark:text-slate-200 block truncate">
              {nodeData.name || 'Sub-Workflow Call'}
            </span>
            <span className="text-[10px] text-indigo-600 dark:text-indigo-400 font-mono">
              Nested Workflow
            </span>
          </div>
        </div>

        {/* Status or Muted indicator */}
        {isMuted ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-500 text-[10px]">
            <EyeOff className="w-3 h-3" />
            <span>Muted</span>
          </div>
        ) : status === 'RUNNING' ? (
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-600 dark:text-indigo-400 text-[10px] font-medium animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-ping" />
            Running
          </div>
        ) : status === 'COMPLETED' ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-[10px]">
            <CheckCircle2 className="w-3 h-3" />
            <span>Done</span>
          </div>
        ) : status === 'FAILED' ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-rose-500/20 text-rose-600 dark:text-rose-400 text-[10px]">
            <XCircle className="w-3 h-3" />
            <span>Failed</span>
          </div>
        ) : null}
      </div>

      {/* Target Workflow banner */}
      <div className="p-2 rounded-lg bg-indigo-50/50 dark:bg-indigo-950/30 border border-indigo-200/50 dark:border-indigo-800/40 mb-2">
        <div className="flex items-center gap-1.5 text-[11px] font-medium text-indigo-700 dark:text-indigo-300 truncate">
          <Layers className="w-3.5 h-3.5 shrink-0 text-indigo-500" />
          <span className="truncate">{targetWfName || 'Select target workflow...'}</span>
        </div>
      </div>

      {/* Footer details & badges */}
      <div className="flex items-center justify-between text-[10px] text-slate-500 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800/60">
        <div className="flex items-center gap-2">
          {inputCount > 0 && (
            <span className="px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 font-mono text-[9px] text-slate-600 dark:text-slate-300">
              {inputCount} in
            </span>
          )}
          {extractionCount > 0 && (
            <span className="px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-mono text-[9px]">
              {extractionCount} out
            </span>
          )}
        </div>
        {nodeData.durationMs !== undefined && (
          <span className="font-mono text-slate-400">{nodeData.durationMs}ms</span>
        )}
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-indigo-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900"
      />
    </div>
  );
});
