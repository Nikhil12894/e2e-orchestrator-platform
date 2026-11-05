import React, { useState } from 'react';
import {
  X,
  Variable,
  Sparkles,
  Copy,
  Check,
  ChevronRight,
  Hash,
  Code,
  HelpCircle,
  Globe,
  Lock,
  Eye,
  EyeOff
} from 'lucide-react';
import { CanvasNode } from '../../types/workflow';

interface VariableSelectorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectVariable: (variableExpression: string) => void;
  availableNodes: CanvasNode[];
  currentNodeId?: string;
  envProperties?: Record<string, Record<string, any>>;
  envSecrets?: Record<string, Record<string, string>>;
  secrets?: Record<string, string>;
  activeEnvironment?: string;
}

export const VariableSelectorModal: React.FC<VariableSelectorModalProps> = ({
  isOpen,
  onClose,
  onSelectVariable,
  availableNodes,
  currentNodeId,
  envProperties = {},
  envSecrets = {},
  secrets = {},
  activeEnvironment = 'default',
}) => {
  const [selectedTab, setSelectedTab] = useState<'env' | 'secrets' | 'steps' | 'macros'>('env');
  const [selectedEnvFilter, setSelectedEnvFilter] = useState<'default' | 'staging' | 'production'>(
    (['default', 'staging', 'production'].includes(activeEnvironment) ? activeEnvironment : 'default') as any
  );
  const [selectedStepId, setSelectedStepId] = useState<string>('');
  const [customJsonPath, setCustomJsonPath] = useState('');
  const [copiedVar, setCopiedVar] = useState<string | null>(null);
  const [revealedSecrets, setRevealedSecrets] = useState<Record<string, boolean>>({});

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

  // Resolve current env variables & secrets for selectedEnvFilter
  const currentEnvVars = Object.entries(envProperties[selectedEnvFilter] || {});

  // Compute secrets for selectedEnvFilter (with inheritance from default & fallback to flat secrets)
  const envSecMap = envSecrets[selectedEnvFilter] || {};
  const defaultSecMap = envSecrets['default'] || {};
  const mergedSecMap: Record<string, string> = {
    ...secrets,
    ...defaultSecMap,
    ...envSecMap,
  };
  const currentSecrets = Object.entries(mergedSecMap);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none">
      <div className="w-full max-w-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh] text-slate-800 dark:text-slate-100 transition-colors">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <Variable className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-900 dark:text-slate-100 text-sm">Variable & Secret Selector</h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">Insert environment variables, secrets, step outputs, or runtime functions</p>
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
        <div className="flex border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/40 px-6 pt-2 gap-1 overflow-x-auto">
          <button
            onClick={() => setSelectedTab('env')}
            className={`px-3.5 py-2 text-xs font-medium border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              selectedTab === 'env'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Globe className="w-3.5 h-3.5" />
            Env Variables ({currentEnvVars.length})
          </button>
          <button
            onClick={() => setSelectedTab('secrets')}
            className={`px-3.5 py-2 text-xs font-medium border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              selectedTab === 'secrets'
                ? 'border-rose-500 text-rose-600 dark:text-rose-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Lock className="w-3.5 h-3.5" />
            Secrets ({currentSecrets.length})
          </button>
          <button
            onClick={() => setSelectedTab('steps')}
            className={`px-3.5 py-2 text-xs font-medium border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              selectedTab === 'steps'
                ? 'border-emerald-500 text-emerald-600 dark:text-emerald-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Variable className="w-3.5 h-3.5" />
            Step Outputs ({upstreamSteps.length})
          </button>
          <button
            onClick={() => setSelectedTab('macros')}
            className={`px-3.5 py-2 text-xs font-medium border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              selectedTab === 'macros'
                ? 'border-purple-500 text-purple-600 dark:text-purple-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            Dynamic Built-ins ({dynamicMacros.length})
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto flex-1">
          {/* TAB 1: Environment Variables */}
          {selectedTab === 'env' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  Target Environment
                </span>
                <div className="flex items-center gap-1 p-0.5 bg-slate-100 dark:bg-slate-950 rounded-lg border border-slate-200 dark:border-slate-800">
                  {(['default', 'staging', 'production'] as const).map((env) => (
                    <button
                      key={env}
                      type="button"
                      onClick={() => setSelectedEnvFilter(env)}
                      className={`px-2.5 py-1 text-[11px] font-medium rounded-md transition-all ${
                        selectedEnvFilter === env
                          ? 'bg-blue-600 text-white shadow-sm'
                          : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
                      }`}
                    >
                      {env}
                    </button>
                  ))}
                </div>
              </div>

              {currentEnvVars.length === 0 ? (
                <div className="text-center py-12 text-slate-400 border border-dashed border-slate-200 dark:border-slate-800 rounded-xl">
                  <p className="text-xs font-medium">No environment variables defined for "{selectedEnvFilter}".</p>
                  <p className="text-[11px] text-slate-500 mt-1">Configure them in Workflow Settings &rarr; Env Properties.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {currentEnvVars.map(([key, val]) => {
                    const expr = `\${${key}}`;
                    return (
                      <div
                        key={key}
                        className="flex items-center justify-between p-3 rounded-lg bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 hover:border-blue-500/40 transition-colors"
                      >
                        <div className="min-w-0 pr-3">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-xs font-bold text-blue-600 dark:text-blue-400">{expr}</span>
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-600 dark:text-blue-300 font-medium">
                              {typeof val}
                            </span>
                          </div>
                          <span className="text-xs text-slate-600 dark:text-slate-400 truncate block mt-0.5 font-mono">
                            Value: {String(val)}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleCopyOrInsert(expr)}
                          className="px-3 py-1.5 rounded-lg bg-blue-600/10 hover:bg-blue-600/20 text-blue-600 dark:text-blue-300 text-xs font-medium transition-colors shrink-0"
                        >
                          {copiedVar === expr ? 'Inserted!' : 'Insert'}
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: Secrets */}
          {selectedTab === 'secrets' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  Target Environment
                </span>
                <div className="flex items-center gap-1 p-0.5 bg-slate-100 dark:bg-slate-950 rounded-lg border border-slate-200 dark:border-slate-800">
                  {(['default', 'staging', 'production'] as const).map((env) => (
                    <button
                      key={env}
                      type="button"
                      onClick={() => setSelectedEnvFilter(env)}
                      className={`px-2.5 py-1 text-[11px] font-medium rounded-md transition-all ${
                        selectedEnvFilter === env
                          ? 'bg-rose-600 text-white shadow-sm'
                          : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'
                      }`}
                    >
                      {env}
                    </button>
                  ))}
                </div>
              </div>

              {currentSecrets.length === 0 ? (
                <div className="text-center py-12 text-slate-400 border border-dashed border-slate-200 dark:border-slate-800 rounded-xl">
                  <p className="text-xs font-medium">No secrets configured for "{selectedEnvFilter}".</p>
                  <p className="text-[11px] text-slate-500 mt-1">Configure secrets in Workflow Settings &rarr; Secrets.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {currentSecrets.map(([key, val]) => {
                    const expr = `\${${key}}`;
                    const isRevealed = revealedSecrets[key];
                    return (
                      <div
                        key={key}
                        className="flex items-center justify-between p-3 rounded-lg bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 hover:border-rose-500/40 transition-colors"
                      >
                        <div className="min-w-0 pr-3">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-xs font-bold text-rose-600 dark:text-rose-400">{expr}</span>
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-500/10 text-rose-600 dark:text-rose-300 font-medium flex items-center gap-1">
                              <Lock className="w-2.5 h-2.5" />
                              Log-Redacted
                            </span>
                          </div>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-xs font-mono text-slate-500 dark:text-slate-400">
                              {isRevealed ? val : '••••••••••••'}
                            </span>
                            <button
                              type="button"
                              onClick={() => setRevealedSecrets((prev) => ({ ...prev, [key]: !prev[key] }))}
                              className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                              title={isRevealed ? 'Hide secret' : 'Reveal secret'}
                            >
                              {isRevealed ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                            </button>
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleCopyOrInsert(expr)}
                          className="px-3 py-1.5 rounded-lg bg-rose-600/10 hover:bg-rose-600/20 text-rose-600 dark:text-rose-300 text-xs font-medium transition-colors shrink-0"
                        >
                          {copiedVar === expr ? 'Inserted!' : 'Insert'}
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: Upstream Step Outputs */}
          {selectedTab === 'steps' && (
            upstreamSteps.length === 0 ? (
              <div className="text-center py-12 text-slate-400 border border-dashed border-slate-200 dark:border-slate-800 rounded-xl">
                <p className="text-xs">No upstream steps available yet.</p>
                <p className="text-[11px] text-slate-500 mt-1">Add previous steps to the canvas to extract and map variables.</p>
              </div>
            ) : (
              <div className="grid grid-cols-12 gap-4">
                {/* Steps List */}
                <div className="col-span-4 border-r border-slate-200 dark:border-slate-800 pr-3 space-y-1">
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
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300 border border-emerald-500/30'
                            : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/60 hover:text-slate-900 dark:hover:text-slate-200 border border-transparent'
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
                                  className="flex items-center justify-between p-2.5 rounded-lg bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 hover:border-emerald-500/40 transition-colors"
                                >
                                  <div>
                                    <div className="flex items-center gap-2">
                                      <span className="font-mono text-xs font-bold text-emerald-600 dark:text-emerald-400">{expr}</span>
                                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                                        {ex.source}
                                      </span>
                                    </div>
                                    <span className="text-[10px] font-mono text-slate-500">{ex.jsonPath}</span>
                                  </div>
                                  <button
                                    type="button"
                                    onClick={() => handleCopyOrInsert(expr)}
                                    className="px-2.5 py-1 rounded bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-600 dark:text-emerald-300 text-xs font-medium transition-colors"
                                  >
                                    {copiedVar === expr ? 'Inserted!' : 'Insert'}
                                  </button>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <p className="text-xs text-slate-500 italic">No variables extracted in this step yet.</p>
                        )}
                      </div>

                      {/* Custom JSONPath Expression */}
                      <div className="pt-2 border-t border-slate-200 dark:border-slate-800 space-y-1.5">
                        <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block">
                          Extract Via JSONPath Expression
                        </span>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            placeholder="$.data.user.id"
                            value={customJsonPath}
                            onChange={(e) => setCustomJsonPath(e.target.value)}
                            className="flex-1 px-2.5 py-1.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-xs font-mono text-slate-800 dark:text-slate-200 focus:outline-none focus:border-emerald-500"
                          />
                          <button
                            type="button"
                            disabled={!customJsonPath.trim()}
                            onClick={() => {
                              const path = customJsonPath.trim().replace(/^\$\.?/, '');
                              handleCopyOrInsert(`\${${selectedStep.id}.response.body.${path}}`);
                            }}
                            className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition-colors disabled:opacity-40"
                          >
                            Insert
                          </button>
                        </div>
                      </div>

                      {/* Standard Step Outputs */}
                      <div className="pt-2 border-t border-slate-200 dark:border-slate-800 space-y-1.5">
                        <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-1">
                          Standard Step Outputs
                        </span>
                        {[
                          { label: 'HTTP Status Code', expr: `\${${selectedStep.id}.status}` },
                          { label: 'Full Response Body Object', expr: `\${${selectedStep.id}.body}` },
                        ].map((item) => (
                          <div
                            key={item.expr}
                            className="flex items-center justify-between p-2 rounded-lg bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700"
                          >
                            <div>
                              <span className="text-xs text-slate-700 dark:text-slate-300 block">{item.label}</span>
                              <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-400">{item.expr}</span>
                            </div>
                            <button
                              type="button"
                              onClick={() => handleCopyOrInsert(item.expr)}
                              className="px-2.5 py-1 rounded bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 text-xs font-medium transition-colors"
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
          )}

          {/* TAB 4: Dynamic Built-ins */}
          {selectedTab === 'macros' && (
            <div className="space-y-2">
              <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider block mb-2">
                Click to Insert Built-in Runtime Function
              </span>
              {dynamicMacros.map((macro) => (
                <div
                  key={macro.name}
                  className="flex items-center justify-between p-3 rounded-lg bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 hover:border-emerald-500/40 transition-colors group"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                      <Code className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="font-mono text-xs font-bold text-emerald-600 dark:text-emerald-400 block group-hover:text-emerald-500">
                        {macro.name}
                      </span>
                      <span className="text-xs text-slate-600 dark:text-slate-400">{macro.desc}</span>
                      <span className="text-[10px] font-mono text-slate-500 block mt-0.5">Example: {macro.example}</span>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleCopyOrInsert(macro.name)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-600 dark:text-emerald-300 text-xs font-medium transition-colors shrink-0"
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
