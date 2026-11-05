import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Code2, CheckCircle2, XCircle, EyeOff } from 'lucide-react';
import { CanvasNodeData } from '../../../types/workflow';

export const TransformNode = memo(({ data, selected }: NodeProps<any>) => {
  const nodeData = data as CanvasNodeData;
  const status = nodeData.status;
  const isMuted = nodeData.enabled === false;

  return (
    <div
      className={`relative w-72 rounded-xl bg-white/95 dark:bg-slate-900/90 border p-3.5 shadow-lg backdrop-blur-md transition-all ${
        isMuted ? 'opacity-60' : ''
      } ${
        selected
          ? 'border-violet-500 ring-2 ring-violet-500/30'
          : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-violet-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900"
      />

      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="p-1.5 rounded-lg bg-violet-500/10 text-violet-500 dark:text-violet-400">
            <Code2 className="w-4 h-4" />
          </div>
          <div className="min-w-0">
            <span className="font-semibold text-xs text-slate-800 dark:text-slate-200 block truncate">
              {nodeData.name || 'JS Transform'}
            </span>
            <span className="text-[10px] text-violet-600 dark:text-violet-400 font-mono">
              JavaScript Script
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
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-violet-500/20 text-violet-600 dark:text-violet-400 text-[10px] font-medium animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-violet-500 animate-ping" />
            Executing
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

      {/* Code preview snippet */}
      <div className="p-2 rounded-lg bg-slate-50 dark:bg-slate-950/70 border border-slate-200 dark:border-slate-800/80 mb-2 font-mono text-[10px] text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed">
        {nodeData.script || 'function(ctx) { return { timestamp: Date.now() }; }'}
      </div>

      {/* Footer details */}
      <div className="flex items-center justify-between text-[10px] text-slate-400 dark:text-slate-500 pt-1 border-t border-slate-100 dark:border-slate-800/60">
        <span>Evaluates in Karate JS context</span>
        {nodeData.durationMs !== undefined && (
          <span className="font-mono">{nodeData.durationMs}ms</span>
        )}
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-violet-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900"
      />
    </div>
  );
});
