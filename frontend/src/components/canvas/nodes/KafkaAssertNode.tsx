import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Radio, CheckCircle2, XCircle, Clock } from 'lucide-react';
import { CanvasNodeData } from '../../../types/workflow';

export const KafkaAssertNode = memo(({ data, selected }: NodeProps<any>) => {
  const nodeData = data as CanvasNodeData;
  const status = nodeData.status;

  return (
    <div
      className={`relative w-72 rounded-xl bg-slate-900/90 border p-3.5 shadow-xl backdrop-blur-md transition-all ${
        selected ? 'border-purple-500 ring-2 ring-purple-500/30' : 'border-slate-800 hover:border-slate-700'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-purple-500 !w-2.5 !h-2.5 !border-slate-900" />

      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="p-1.5 rounded-lg bg-purple-500/10 text-purple-400">
            <Radio className="w-4 h-4" />
          </div>
          <span className="font-semibold text-xs text-slate-200 truncate">{nodeData.name || 'Kafka Assertion'}</span>
        </div>

        {/* Status Indicator */}
        {status === 'RUNNING' && (
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-400 text-[10px] font-medium animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-ping" />
            Listening
          </div>
        )}
        {status === 'COMPLETED' && (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 text-[10px]">
            <CheckCircle2 className="w-3 h-3" />
            <span>Received</span>
          </div>
        )}
        {status === 'FAILED' && (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-rose-500/20 text-rose-400 text-[10px]">
            <XCircle className="w-3 h-3" />
            <span>Timeout</span>
          </div>
        )}
      </div>

      {/* Topic Preview */}
      <div className="flex items-center gap-2 p-2 rounded-lg bg-slate-950/60 border border-slate-800/80 mb-2.5">
        <span className="text-[10px] font-bold text-purple-400 uppercase tracking-wide">Topic:</span>
        <span className="text-[11px] font-mono text-slate-400 truncate flex-1" title={nodeData.topic}>
          {nodeData.topic || 'order-events'}
        </span>
      </div>

      {/* Footer Details */}
      <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 border-t border-slate-800/60">
        <span className="font-mono text-purple-300/80 truncate max-w-[180px]">
          {nodeData.jsonPath ? `${nodeData.jsonPath} == ${nodeData.expectedValue}` : 'Match any message'}
        </span>

        {nodeData.durationMs !== undefined && (
          <span className="flex items-center gap-1 font-mono text-slate-500">
            <Clock className="w-2.5 h-2.5" />
            {nodeData.durationMs}ms
          </span>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="!bg-purple-500 !w-2.5 !h-2.5 !border-slate-900" />
    </div>
  );
});
