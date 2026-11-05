import React, { useState } from 'react';
import { X, Variable, Sparkles, Copy, Check, ChevronRight, Hash, Code, HelpCircle } from 'lucide-react';
import { CanvasNode } from '../../types/workflow';

interface VariableSelectorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectVariable: (variableExpression: string) => void;
  availableNodes: CanvasNode[];
  currentNodeId?: string;
}

export const VariableSelectorModal: React.FC<VariableSelectorModalProps> = ({
  isOpen,
  onClose,
  onSelectVariable,
  availableNodes,
  currentNodeId,
}) => {
  const [selectedTab, setSelectedTab] = useState<'steps' | 'macros'>('steps');
  const [selectedStepId, setSelectedStepId] = useState<string>('');
  const [customJsonPath, setCustomJsonPath] = useState('');
  const [copiedVar, setCopiedVar] = useState<string | null>(null);

  if (!isOpen) return null;

  // Filter out the current node (steps that can supply variables)
  const upstreamSteps = availableNodes.filter((n) => n.id !== currentNodeId);

  const selectedStep = upstreamSteps.find((s) => s.id === selectedStepId) || upstreamSteps[0];

  const handleCopyOrInsert = (expr: string) => {
    onSelectVariable(expr);
    setCopiedVar(expr);
    setTimeout(() => {
      setCopiedVar(null);
      onClose();
    }, 300);
  };

  const dynamicMacros = [
    { name: '${$timestamp}', desc: 'Current epoch timestamp in milliseconds', example: '1730700000000' },
    { name: '${$isoTimestamp}', desc: 'Current ISO-8601 UTC timestamp', example: '2026-11-04T06:30:00Z' },
    { name: '${$randomUuid}', desc: 'Generates a random UUID v4', example: 'a0b1c2d3-e4f5-...' },
    { name: '${$randomInt(1,100)}', desc: 'Random integer between min and max', example: '42' },
    { name: '${$randomString(8)}', desc: 'Random alphanumeric string of N chars', example: 'k7X9p2M1' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh] text-slate-800 dark:text-slate-100 transition-colors">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <Variable className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-900 dark:text-slate-100 text-sm">Variable & Output Expression Selector</h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">Map upstream step outputs or runtime dynamic variables into inputs</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab switcher */}
        <div className="flex border-b border-slate-800 bg-slate-950/40 px-6 pt-2">
          <button
            onClick={() => setSelectedTab('steps')}
            className={`px-4 py-2 text-xs font-medium border-b-2 transition-all ${
              selectedTab === 'steps'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            Upstream Step Outputs ({upstreamSteps.length})
          </button>
          <button
            onClick={() => setSelectedTab('macros')}
            className={`px-4 py-2 text-xs font-medium border-b-2 transition-all ${
              selectedTab === 'macros'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            Dynamic Built-in Functions ({dynamicMacros.length})
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto flex-1">
          {selectedTab === 'steps' ? (
            upstreamSteps.length === 0 ? (
              <div className="text-center py-12 text-slate-400">
                <p className="text-xs">No upstream steps available yet.</p>
                <p className="text-[11px] text-slate-500 mt-1">Add previous steps to the canvas to extract and map variables.</p>
              </div>
            ) : (
              <div className="grid grid-cols-12 gap-4">
                {/* Steps List */}
                <div className="col-span-4 border-r border-slate-800 pr-3 space-y-1">
                  <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-2">
                    Select Upstream Step
                  </span>
                  {upstreamSteps.map((step) => {
                    const isSelected = (selectedStep?.id || '') === step.id;
                    return (
                      <button
                        key={step.id}
                        type="button"
                        onClick={() => setSelectedStepId(step.id)}
                        className={`w-full text-left p-2.5 rounded-lg text-xs font-medium transition-all flex items-center justify-between ${
                          isSelected
                            ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/30'
                            : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200 border border-transparent'
                        }`}
                      >
                        <span className="truncate">{step.data.name || step.id}</span>
                        <ChevronRight className="w-3.5 h-3.5 opacity-60" />
                      </button>
                    );
                  })}
                </div>

                {/* Step Outputs Inspector */}
                <div className="col-span-8 pl-1 space-y-4">
                  {selectedStep && (
                    <>
                      <div>
                        <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-2">
                          Extracted Variables from "{selectedStep.data.name}"
                        </span>
                        {selectedStep.data.extractions && selectedStep.data.extractions.length > 0 ? (
                          <div className="space-y-1.5">
                            {selectedStep.data.extractions.map((ex) => {
                              const expr = `\${${ex.variableName}}`;
                              return (
                                <div
                                  key={ex.variableName}
                                  className="flex items-center justify-between p-2.5 rounded-lg bg-slate-950 border border-slate-800 hover:border-emerald-500/40 transition-colors"
                                >
                                  <div>
                                    <div className="flex items-center gap-2">
                                      <span className="font-mono text-xs font-bold text-emerald-400">{expr}</span>
                                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400">
                                        {ex.source}
                                      </span>
                                    </div>
                                    <span className="text-[10px] font-mono text-slate-500">{ex.jsonPath}</span>
                                  </div>
                                  <button
                                    onClick={() => handleCopyOrInsert(expr)}
                                    className="px-2.5 py-1 rounded bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-300 text-xs font-medium transition-colors"
                                  >
                                    {copiedVar === expr ? 'Inserted!' : 'Insert'}
                                  </button>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <p className="text-xs text-slate-500 italic p-2 bg-slate-950/40 rounded border border-slate-800/50">
                            No explicit extractions defined on this step. You can still access its raw response body below.
                          </p>
                        )}
                      </div>

                      {/* Custom JSONPath Expression helper */}
                      <div className="pt-2 border-t border-slate-800">
                        <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-1.5">
                          Direct JSONPath Expression
                        </span>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            value={customJsonPath}
                            onChange={(e) => setCustomJsonPath(e.target.value)}
                            placeholder="e.g. $.token or $.data.orderId"
                            className="flex-1 px-3 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:border-emerald-500"
                          />
                          <button
                            type="button"
                            onClick={() => {
                              if (customJsonPath.trim()) {
                                const clean = customJsonPath.trim().replace(/^\$\.?/, '');
                                handleCopyOrInsert(`\${${selectedStep.id}.response.body.${clean}}`);
                              }
                            }}
                            disabled={!customJsonPath.trim()}
                            className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium disabled:opacity-50 transition-colors shadow"
                          >
                            Insert
                          </button>
                        </div>
                        <p className="text-[10px] text-slate-500 mt-1">
                          Generates: <code className="text-slate-400 font-mono">{`\${${selectedStep.id}.response.body.${customJsonPath.replace(/^\$\.?/, '') || 'path'}}`}</code>
                        </p>
                      </div>

                      {/* Standard Step Properties */}
                      <div className="pt-2 border-t border-slate-800 space-y-1.5">
                        <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-1">
                          Standard Step Outputs
                        </span>
                        {[
                          { label: 'HTTP Status Code', expr: `\${${selectedStep.id}.status}` },
                          { label: 'Full Response Body Object', expr: `\${${selectedStep.id}.body}` },
                        ].map((item) => (
                          <div
                            key={item.expr}
                            className="flex items-center justify-between p-2 rounded-lg bg-slate-950 border border-slate-800 hover:border-slate-700"
                          >
                            <div>
                              <span className="text-xs text-slate-300 block">{item.label}</span>
                              <span className="text-[10px] font-mono text-emerald-400">{item.expr}</span>
                            </div>
                            <button
                              onClick={() => handleCopyOrInsert(item.expr)}
                              className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
                            >
                              Insert
                            </button>
                          </div>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              </div>
            )
          ) : (
            <div className="space-y-2">
              <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-2">
                Click to Insert Built-in Runtime Function
              </span>
              {dynamicMacros.map((macro) => (
                <div
                  key={macro.name}
                  className="flex items-center justify-between p-3 rounded-lg bg-slate-950 border border-slate-800 hover:border-emerald-500/40 transition-colors group"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded bg-emerald-500/10 text-emerald-400">
                      <Code className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="font-mono text-xs font-bold text-emerald-400 block group-hover:text-emerald-300">
                        {macro.name}
                      </span>
                      <span className="text-xs text-slate-400">{macro.desc}</span>
                      <span className="text-[10px] font-mono text-slate-500 block mt-0.5">Example: {macro.example}</span>
                    </div>
                  </div>
                  <button
                    onClick={() => handleCopyOrInsert(macro.name)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-300 text-xs font-medium transition-colors"
                  >
                    {copiedVar === macro.name ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                    {copiedVar === macro.name ? 'Inserted!' : 'Insert'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
