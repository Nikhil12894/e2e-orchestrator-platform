import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Database, CheckCircle2, XCircle, Clock, EyeOff } from 'lucide-react';
import { CanvasNodeData } from '../../../types/workflow';

export const DbPollNode = memo(({ data, selected }: NodeProps<any>) => {
  const nodeData = data as CanvasNodeData;
  const status = nodeData.status;
  const isMuted = nodeData.enabled === false;

  return (
    <div
      className={`relative w-72 rounded-xl bg-white/95 dark:bg-slate-900/90 border p-3.5 shadow-lg backdrop-blur-md transition-all ${
        isMuted ? 'opacity-60' : ''
      } ${
        selected ? 'border-amber-500 ring-2 ring-amber-500/30' : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-amber-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900" />

      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="p-1.5 rounded-lg bg-amber-500/10 text-amber-500 dark:text-amber-400">
            <Database className="w-4 h-4" />
          </div>
          <span className="font-semibold text-xs text-slate-800 dark:text-slate-200 truncate">{nodeData.name || 'DB SQL Poll'}</span>
        </div>

        {/* Status or Muted Indicator */}
        {isMuted ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-500 text-[10px]">
            <EyeOff className="w-3 h-3" />
            <span>Muted</span>
          </div>
        ) : status === 'RUNNING' ? (
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-600 dark:text-amber-400 text-[10px] font-medium animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
            Polling
          </div>
        ) : status === 'COMPLETED' ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-[10px]">
            <CheckCircle2 className="w-3 h-3" />
            <span>Matched</span>
          </div>
        ) : status === 'FAILED' ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-rose-500/20 text-rose-600 dark:text-rose-400 text-[10px]">
            <XCircle className="w-3 h-3" />
            <span>Timeout</span>
          </div>
        ) : null}
      </div>

      {/* SQL Query Preview */}
      <div className="p-2 rounded-lg bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800/80 mb-2.5">
        <span className="text-[10px] font-mono text-amber-600 dark:text-amber-400/90 block mb-1">SQL Query:</span>
        <p className="text-[11px] font-mono text-slate-600 dark:text-slate-400 truncate" title={nodeData.query}>
          {nodeData.query || 'SELECT status FROM orders...'}
        </p>
      </div>

      {/* Footer Details: Condition & Duration */}
      <div className="flex items-center justify-between text-[10px] text-slate-500 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800/60">
        <span className="px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-600 dark:text-amber-300/90 border border-amber-500/20 font-mono truncate max-w-[170px]">
          {nodeData.condition || 'count > 0'}
        </span>

        {nodeData.durationMs !== undefined && (
          <span className="flex items-center gap-1 font-mono text-slate-400 dark:text-slate-500">
            <Clock className="w-2.5 h-2.5" />
            {nodeData.durationMs}ms
          </span>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="!bg-amber-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900" />
    </div>
  );
});
