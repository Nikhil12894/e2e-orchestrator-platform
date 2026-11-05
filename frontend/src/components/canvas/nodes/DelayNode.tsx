import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Timer, CheckCircle2, EyeOff } from 'lucide-react';
import { CanvasNodeData } from '../../../types/workflow';

export const DelayNode = memo(({ data, selected }: NodeProps<any>) => {
  const nodeData = data as CanvasNodeData;
  const status = nodeData.status;
  const isMuted = nodeData.enabled === false;

  return (
    <div
      className={`relative w-64 rounded-xl bg-white/95 dark:bg-slate-900/90 border p-3.5 shadow-lg backdrop-blur-md transition-all ${
        isMuted ? 'opacity-60' : ''
      } ${
        selected ? 'border-cyan-500 ring-2 ring-cyan-500/30' : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-cyan-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900" />

      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="p-1.5 rounded-lg bg-cyan-500/10 text-cyan-500 dark:text-cyan-400">
            <Timer className="w-4 h-4" />
          </div>
          <div>
            <span className="font-semibold text-xs text-slate-800 dark:text-slate-200 block truncate">{nodeData.name || 'Delay'}</span>
            <span className="text-[10px] text-slate-400 dark:text-slate-500 font-mono">Pause {nodeData.delayMs || 1000}ms</span>
          </div>
        </div>

        {isMuted ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-500 text-[10px]">
            <EyeOff className="w-3 h-3" />
            <span>Muted</span>
          </div>
        ) : status === 'RUNNING' ? (
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-600 dark:text-cyan-400 text-[10px] font-medium animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-cyan-500 animate-ping" />
            Waiting
          </div>
        ) : status === 'COMPLETED' ? (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-[10px]">
            <CheckCircle2 className="w-3 h-3" />
            <span>Done</span>
          </div>
        ) : null}
      </div>

      <Handle type="source" position={Position.Bottom} className="!bg-cyan-500 !w-2.5 !h-2.5 !border-white dark:!border-slate-900" />
    </div>
  );
});
