import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Globe, ArrowRight, CheckCircle2, XCircle, Clock, AlertCircle } from 'lucide-react';
import { CanvasNodeData, HttpMethod } from '../../../types/workflow';

const methodColors: Record<HttpMethod, { bg: string; text: string; border: string }> = {
  GET: { bg: 'bg-blue-500/10', text: 'text-blue-400', border: 'border-blue-500/30' },
  POST: { bg: 'bg-emerald-500/10', text: 'text-emerald-400', border: 'border-emerald-500/30' },
  PUT: { bg: 'bg-amber-500/10', text: 'text-amber-400', border: 'border-amber-500/30' },
  DELETE: { bg: 'bg-rose-500/10', text: 'text-rose-400', border: 'border-rose-500/30' },
  PATCH: { bg: 'bg-purple-500/10', text: 'text-purple-400', border: 'border-purple-500/30' },
};

export const HttpRequestNode = memo(({ data, selected }: NodeProps<any>) => {
  const nodeData = data as CanvasNodeData;
  const method = (nodeData.method || 'GET') as HttpMethod;
  const style = methodColors[method] || methodColors.GET;
  const status = nodeData.status;

  return (
    <div
      className={`relative w-72 rounded-xl bg-slate-900/90 border p-3.5 shadow-xl backdrop-blur-md transition-all ${
        selected ? 'border-blue-500 ring-2 ring-blue-500/30' : 'border-slate-800 hover:border-slate-700'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-blue-500 !w-2.5 !h-2.5 !border-slate-900" />

      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="p-1.5 rounded-lg bg-blue-500/10 text-blue-400">
            <Globe className="w-4 h-4" />
          </div>
          <span className="font-semibold text-xs text-slate-200 truncate">{nodeData.name || 'HTTP Request'}</span>
        </div>

        {/* Status Indicator */}
        {status === 'RUNNING' && (
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 text-[10px] font-medium animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-ping" />
            Running
          </div>
        )}
        {status === 'COMPLETED' && (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 text-[10px]">
            <CheckCircle2 className="w-3 h-3" />
            <span>Passed</span>
          </div>
        )}
        {status === 'FAILED' && (
          <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-rose-500/20 text-rose-400 text-[10px]">
            <XCircle className="w-3 h-3" />
            <span>Failed</span>
          </div>
        )}
      </div>

      {/* URL Endpoint Preview */}
      <div className="flex items-center gap-2 p-2 rounded-lg bg-slate-950/60 border border-slate-800/80 mb-2.5">
        <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wide border ${style.bg} ${style.text} ${style.border}`}>
          {method}
        </span>
        <span className="text-[11px] font-mono text-slate-400 truncate flex-1" title={nodeData.url}>
          {nodeData.url || '/api/...'}
        </span>
      </div>

      {/* Footer details: Extractions & Assertions */}
      <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 border-t border-slate-800/60">
        <div className="flex items-center gap-1.5">
          {nodeData.extractions && nodeData.extractions.length > 0 && (
            <span className="px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              +{nodeData.extractions.length} vars
            </span>
          )}
          {nodeData.assertions && nodeData.assertions.length > 0 && (
            <span className="px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
              {nodeData.assertions.length} asserts
            </span>
          )}
        </div>

        {nodeData.durationMs !== undefined && (
          <span className="flex items-center gap-1 font-mono text-slate-500">
            <Clock className="w-2.5 h-2.5" />
            {nodeData.durationMs}ms
          </span>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="!bg-blue-500 !w-2.5 !h-2.5 !border-slate-900" />
    </div>
  );
});
