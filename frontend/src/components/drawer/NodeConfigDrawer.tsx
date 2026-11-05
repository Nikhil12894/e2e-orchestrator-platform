import React, { useState, Suspense } from 'react';
import {
  X,
  Settings,
  Globe,
  Database,
  Radio,
  Timer,
  Code2,
  RotateCcw,
  EyeOff,
  Plus,
  Trash2,
  Variable,
  Sliders,
  CheckCircle,
  HelpCircle,
  Ban,
  Check
} from 'lucide-react';
import { CanvasNode, CanvasNodeData, HttpMethod, StepAssertion, VariableExtraction } from '../../types/workflow';

const VariableSelectorModal = React.lazy(() =>
  import('./VariableSelectorModal').then((m) => ({ default: m.VariableSelectorModal }))
);

interface NodeConfigDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  selectedNode: CanvasNode | null;
  onUpdateNodeData: (nodeId: string, newData: Partial<CanvasNodeData>) => void;
  allNodes: CanvasNode[];
  edges?: Array<{ id: string; source: string; target: string }>;
  onDeleteEdge?: (edgeId: string) => void;
  workflowHeaders?: Record<string, string>;
  envProperties?: Record<string, Record<string, any>>;
  envSecrets?: Record<string, Record<string, string>>;
  secrets?: Record<string, string>;
  activeEnvironment?: string;
}

export const NodeConfigDrawer: React.FC<NodeConfigDrawerProps> = ({
  isOpen,
  onClose,
  selectedNode,
  onUpdateNodeData,
  allNodes,
  edges = [],
  onDeleteEdge,
  workflowHeaders = {},
  envProperties = {},
  envSecrets = {},
  secrets = {},
  activeEnvironment = 'default',
}) => {
  const [activeTab, setActiveTab] = useState<'config' | 'extractions' | 'assertions'>('config');
  const [isVarModalOpen, setIsVarModalOpen] = useState(false);
  const [varTargetField, setVarTargetField] = useState<string | null>(null);

  if (!isOpen || !selectedNode) return null;

  const data = selectedNode.data;
  const nodeType = selectedNode.type;

  const update = (patch: Partial<CanvasNodeData>) => {
    onUpdateNodeData(selectedNode.id, patch);
  };

  const inheritGlobal = data.inheritGlobalHeaders !== false;
  const excludedList: string[] = data.excludedGlobalHeaders || [];
  const globalKeys = Object.keys(workflowHeaders || {});

  const toggleExcludeGlobalHeader = (headerName: string) => {
    const isAlreadyExcluded = excludedList.some((h) => h.toLowerCase() === headerName.toLowerCase());
    let newExcluded: string[];
    if (isAlreadyExcluded) {
      newExcluded = excludedList.filter((h) => h.toLowerCase() !== headerName.toLowerCase());
    } else {
      newExcluded = [...excludedList, headerName];
    }
    update({ excludedGlobalHeaders: newExcluded });
  };

  const setInheritMode = (mode: 'all' | 'selective' | 'none') => {
    if (mode === 'none') {
      update({ inheritGlobalHeaders: false });
    } else if (mode === 'all') {
      update({ inheritGlobalHeaders: true, excludedGlobalHeaders: [] });
    } else {
      update({ inheritGlobalHeaders: true });
    }
  };

  // Compute effective headers preview
  const effectiveHeadersList: Array<{
    key: string;
    value: string;
    origin: 'global' | 'overridden' | 'custom' | 'excluded';
  }> = [];

  globalKeys.forEach((k) => {
    const isExcluded = !inheritGlobal || excludedList.some((ex) => ex.toLowerCase() === k.toLowerCase());
    const isOverridden = data.headers && Object.prototype.hasOwnProperty.call(data.headers, k);

    if (isExcluded) {
      effectiveHeadersList.push({
        key: k,
        value: (workflowHeaders || {})[k],
        origin: 'excluded',
      });
    } else if (isOverridden) {
      const customVal = data.headers![k];
      if (customVal && customVal.trim() !== '') {
        effectiveHeadersList.push({
          key: k,
          value: customVal,
          origin: 'overridden',
        });
      } else {
        effectiveHeadersList.push({
          key: k,
          value: (workflowHeaders || {})[k],
          origin: 'excluded',
        });
      }
    } else {
      effectiveHeadersList.push({
        key: k,
        value: (workflowHeaders || {})[k],
        origin: 'global',
      });
    }
  });

  Object.entries(data.headers || {}).forEach(([k, v]) => {
    const isGlobal = globalKeys.some((gk) => gk.toLowerCase() === k.toLowerCase());
    if (!isGlobal && v && v.trim() !== '') {
      effectiveHeadersList.push({
        key: k,
        value: v,
        origin: 'custom',
      });
    }
  });

  const activeEffectiveCount = effectiveHeadersList.filter((h) => h.origin !== 'excluded').length;

  const openVarSelector = (fieldName: string) => {
    setVarTargetField(fieldName);
    setIsVarModalOpen(true);
  };

  const currentEnv = activeEnvironment || 'default';
  const currentEnvVarsMap = envProperties[currentEnv] || {};
  const currentSecretsMap: Record<string, string> = {
    ...secrets,
    ...(envSecrets['default'] || {}),
    ...(envSecrets[currentEnv] || {}),
  };

  const handleInsertVariable = (varExpr: string) => {
    if (!varTargetField) return;

    if (varTargetField === 'url') {
      update({ url: (data.url || '') + varExpr });
    } else if (varTargetField === 'jdbcUrl') {
      update({ jdbcUrl: (data.jdbcUrl || '') + varExpr });
    } else if (varTargetField === 'username') {
      update({ username: (data.username || '') + varExpr });
    } else if (varTargetField === 'password') {
      update({ password: (data.password || '') + varExpr });
    } else if (varTargetField === 'query') {
      update({ query: (data.query || '') + varExpr });
    } else if (varTargetField === 'condition') {
      update({ condition: (data.condition || '') + varExpr });
    } else if (varTargetField.startsWith('header_')) {
      const hKey = varTargetField.substring(7);
      const currentVal = (data.headers || {})[hKey] || '';
      update({
        headers: {
          ...(data.headers || {}),
          [hKey]: currentVal + varExpr,
        },
      });
    } else if (varTargetField === 'body') {
      try {
        const bodyStr = typeof data.body === 'string' ? data.body : JSON.stringify(data.body, null, 2);
        update({ body: bodyStr + varExpr });
      } catch {
        update({ body: varExpr });
      }
    }
  };

  // Extractions handler
  const addExtraction = () => {
    const list: VariableExtraction[] = [...(data.extractions || [])];
    list.push({
      variableName: `var_${list.length + 1}`,
      source: 'RESPONSE_BODY',
      jsonPath: '$.id',
      defaultValue: '',
    });
    update({ extractions: list });
  };

  const removeExtraction = (index: number) => {
    const list = [...(data.extractions || [])];
    list.splice(index, 1);
    update({ extractions: list });
  };

  const updateExtraction = (index: number, patch: Partial<VariableExtraction>) => {
    const list = [...(data.extractions || [])];
    list[index] = { ...list[index], ...patch };
    update({ extractions: list });
  };

  // Assertions handler
  const addAssertion = () => {
    const list: StepAssertion[] = [...(data.assertions || [])];
    list.push({
      field: 'status',
      operator: 'EQUALS',
      expectedValue: '200',
    });
    update({ assertions: list });
  };

  const removeAssertion = (index: number) => {
    const list = [...(data.assertions || [])];
    list.splice(index, 1);
    update({ assertions: list });
  };

  const updateAssertion = (index: number, patch: Partial<StepAssertion>) => {
    const list = [...(data.assertions || [])];
    list[index] = { ...list[index], ...patch };
    update({ assertions: list });
  };

  return (
    <>
      <div className="fixed inset-y-0 right-0 z-40 w-96 bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 shadow-2xl flex flex-col select-none transition-colors">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="p-1.5 rounded-lg bg-blue-500/10 text-blue-500 dark:text-blue-400">
              {nodeType === 'httpRequest' && <Globe className="w-4 h-4" />}
              {nodeType === 'dbPoll' && <Database className="w-4 h-4 text-amber-500 dark:text-amber-400" />}
              {nodeType === 'kafkaAssert' && <Radio className="w-4 h-4 text-purple-500 dark:text-purple-400" />}
              {nodeType === 'delay' && <Timer className="w-4 h-4 text-cyan-500 dark:text-cyan-400" />}
              {nodeType === 'transform' && <Code2 className="w-4 h-4 text-violet-500 dark:text-violet-400" />}
            </div>
            <div className="min-w-0">
              <h3 className="font-semibold text-xs text-slate-800 dark:text-slate-100 truncate">{data.name || 'Step Configuration'}</h3>
              <span className="text-[10px] font-mono text-slate-400 dark:text-slate-500">ID: {selectedNode.id}</span>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="flex border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/30 px-3 pt-1">
          <button
            onClick={() => setActiveTab('config')}
            className={`flex-1 py-2 text-xs font-medium border-b-2 transition-all ${
              activeTab === 'config'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            Config
          </button>
          <button
            onClick={() => setActiveTab('extractions')}
            className={`flex-1 py-2 text-xs font-medium border-b-2 transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'extractions'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            Extractions
            {(data.extractions?.length || 0) > 0 && (
              <span className="px-1.5 py-0.2 rounded-full text-[9px] bg-emerald-500/20 text-emerald-600 dark:text-emerald-400">
                {data.extractions?.length}
              </span>
            )}
          </button>
          <button
            onClick={() => setActiveTab('assertions')}
            className={`flex-1 py-2 text-xs font-medium border-b-2 transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'assertions'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            Assertions
            {(data.assertions?.length || 0) > 0 && (
              <span className="px-1.5 py-0.2 rounded-full text-[9px] bg-blue-500/20 text-blue-600 dark:text-blue-400">
                {data.assertions?.length}
              </span>
            )}
          </button>
        </div>

        {/* Tab Body */}
        <div className="p-4 overflow-y-auto flex-1 space-y-4">
          {activeTab === 'config' && (
            <>
              {/* Step Name */}
              <div>
                <label className="block text-[11px] font-medium text-slate-300 mb-1">Step Name</label>
                <input
                  type="text"
                  value={data.name || ''}
                  onChange={(e) => update({ name: e.target.value })}
                  className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                />
              </div>

              {/* HTTP Request specific config */}
              {nodeType === 'httpRequest' && (
                <>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">Method & URL</label>
                    <div className="flex gap-2">
                      <select
                        value={data.method || 'GET'}
                        onChange={(e) => update({ method: e.target.value as HttpMethod })}
                        className="px-2 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-bold text-slate-200 focus:outline-none focus:border-blue-500"
                      >
                        <option value="GET">GET</option>
                        <option value="POST">POST</option>
                        <option value="PUT">PUT</option>
                        <option value="DELETE">DELETE</option>
                        <option value="PATCH">PATCH</option>
                      </select>
                      <div className="flex-1 relative">
                        <input
                          type="text"
                          value={data.url || ''}
                          onChange={(e) => update({ url: e.target.value })}
                          placeholder="http://localhost:8080/api/..."
                          className="w-full pr-7 pl-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                        <button
                          type="button"
                          onClick={() => openVarSelector('url')}
                          title="Insert Variable"
                          className="absolute right-1.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-emerald-400 transition-colors"
                        >
                          <Variable className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Expected Status */}
                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">Expected HTTP Status</label>
                    <input
                      type="number"
                      value={data.expectedStatus ?? 200}
                      onChange={(e) => update({ expectedStatus: parseInt(e.target.value) || 200 })}
                      className="w-28 px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                    />
                  </div>

                  {/* Retry Policy */}
                  <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <RotateCcw className="w-3.5 h-3.5 text-amber-500" />
                        <label className="text-[11px] font-semibold text-slate-300">Retry Policy</label>
                      </div>
                      <span className="text-[10px] font-mono text-amber-500 font-semibold">
                        {(data.retryCount || 1) > 1 ? `${data.retryCount}x attempts` : '1 attempt (no retry)'}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-2 pt-1">
                      <div>
                        <label className="text-[10px] text-slate-400 block mb-1">Max Attempts (1-5)</label>
                        <input
                          type="number"
                          min="1"
                          max="5"
                          value={data.retryCount ?? 1}
                          onChange={(e) => update({ retryCount: Math.max(1, Math.min(5, parseInt(e.target.value) || 1)) })}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="text-[10px] text-slate-400 block mb-1">Interval (ms)</label>
                        <input
                          type="number"
                          min="100"
                          step="100"
                          value={data.retryIntervalMs ?? 500}
                          onChange={(e) => update({ retryIntervalMs: Math.max(100, parseInt(e.target.value) || 500) })}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Step HTTP Headers & Inheritance Control */}
                  <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-3">
                    <div className="flex items-center justify-between">
                      <div>
                        <label className="text-[11px] font-semibold text-slate-300 block">HTTP Request Headers</label>
                        <span className="text-[10px] text-slate-500">Configure global inheritance, selective exclusions, and custom headers</span>
                      </div>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 font-semibold">
                        {activeEffectiveCount} Active
                      </span>
                    </div>

                    {/* Inheritance Mode Selector */}
                    <div>
                      <span className="text-[10px] uppercase font-semibold text-slate-400 block mb-1.5">
                        Global Header Inheritance
                      </span>
                      <div className="grid grid-cols-3 gap-1.5">
                        <button
                          type="button"
                          onClick={() => setInheritMode('all')}
                          className={`px-2 py-1.5 rounded-lg text-[10px] font-semibold border transition-all ${
                            inheritGlobal && excludedList.length === 0
                              ? 'bg-blue-600/20 border-blue-500 text-blue-300 shadow-sm'
                              : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          Inherit All
                        </button>
                        <button
                          type="button"
                          onClick={() => setInheritMode('selective')}
                          className={`px-2 py-1.5 rounded-lg text-[10px] font-semibold border transition-all ${
                            inheritGlobal && excludedList.length > 0
                              ? 'bg-amber-600/20 border-amber-500 text-amber-300 shadow-sm'
                              : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          Selective Exclude {excludedList.length > 0 ? `(${excludedList.length})` : ''}
                        </button>
                        <button
                          type="button"
                          onClick={() => setInheritMode('none')}
                          className={`px-2 py-1.5 rounded-lg text-[10px] font-semibold border transition-all ${
                            !inheritGlobal
                              ? 'bg-rose-600/20 border-rose-500 text-rose-300 shadow-sm'
                              : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          Do Not Inherit
                        </button>
                      </div>
                    </div>

                    {/* Global Headers Quick-Toggle Pills */}
                    {globalKeys.length > 0 ? (
                      <div>
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-[10px] font-medium text-slate-400">Workflow Global Headers</span>
                          <span className="text-[9px] text-slate-500">Click any pill to toggle exclusion</span>
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {globalKeys.map((gKey) => {
                            const isExcluded = !inheritGlobal || excludedList.some((ex) => ex.toLowerCase() === gKey.toLowerCase());
                            const isOverridden = data.headers && Object.prototype.hasOwnProperty.call(data.headers, gKey) && data.headers[gKey]?.trim() !== '';
                            return (
                              <button
                                key={gKey}
                                type="button"
                                onClick={() => {
                                  if (!inheritGlobal) {
                                    update({ inheritGlobalHeaders: true, excludedGlobalHeaders: globalKeys.filter((k) => k !== gKey) });
                                  } else {
                                    toggleExcludeGlobalHeader(gKey);
                                  }
                                }}
                                title={isExcluded ? `Click to include ${gKey}` : `Click to exclude ${gKey}`}
                                className={`px-2 py-1 rounded-md text-[10px] font-mono font-medium border flex items-center gap-1 transition-all ${
                                  isExcluded
                                    ? 'bg-slate-900/60 border-slate-800 text-slate-500 line-through'
                                    : isOverridden
                                    ? 'bg-amber-500/15 border-amber-500/30 text-amber-300'
                                    : 'bg-blue-500/15 border-blue-500/30 text-blue-300'
                                }`}
                              >
                                {isExcluded ? <Ban className="w-2.5 h-2.5 text-rose-400" /> : <Check className="w-2.5 h-2.5 text-emerald-400" />}
                                <span>{gKey}</span>
                                {isOverridden && <span className="text-[8px] bg-amber-500/30 px-1 rounded text-amber-200">Overridden</span>}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    ) : (
                      <div className="p-2 bg-slate-900/50 border border-slate-800 rounded-lg text-[10px] text-slate-500">
                        No global headers currently configured in workflow settings.
                      </div>
                    )}

                    {/* Step Custom Headers List */}
                    <div className="pt-2 border-t border-slate-800/80">
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="text-[10px] font-medium text-slate-300">Step Custom & Overriding Headers</span>
                        <button
                          type="button"
                          onClick={() => {
                            const current = { ...(data.headers || {}) };
                            current[`Header-${Object.keys(current).length + 1}`] = '';
                            update({ headers: current });
                          }}
                          className="flex items-center gap-1 text-[10px] text-blue-400 hover:text-blue-300 font-medium"
                        >
                          <Plus className="w-3 h-3" />
                          Add Custom Header
                        </button>
                      </div>

                      {Object.keys(data.headers || {}).length === 0 ? (
                        <p className="text-[10px] text-slate-500 italic py-1">No custom headers added for this step.</p>
                      ) : (
                        <div className="space-y-1.5">
                          {Object.entries(data.headers || {}).map(([hKey, hVal], hIdx) => (
                            <div key={hIdx} className="flex items-center gap-1.5">
                              <input
                                type="text"
                                value={hKey}
                                placeholder="Header-Name"
                                onChange={(e) => {
                                  const newKey = e.target.value;
                                  const newHeaders: Record<string, string> = {};
                                  Object.entries(data.headers || {}).forEach(([k, v]) => {
                                    if (k === hKey) {
                                      newHeaders[newKey] = v;
                                    } else {
                                      newHeaders[k] = v;
                                    }
                                  });
                                  update({ headers: newHeaders });
                                }}
                                className="w-1/2 px-2 py-1 bg-slate-950 border border-slate-800 rounded text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                              />
                              <div className="flex-1 relative">
                                <input
                                  type="text"
                                  value={hVal}
                                  placeholder="Header-Value"
                                  onChange={(e) => {
                                    update({
                                      headers: {
                                        ...(data.headers || {}),
                                        [hKey]: e.target.value,
                                      },
                                    });
                                  }}
                                  className="w-full pr-6 px-2 py-1 bg-slate-950 border border-slate-800 rounded text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                                />
                                <button
                                  type="button"
                                  onClick={() => openVarSelector(`header_${hKey}`)}
                                  title="Insert Variable"
                                  className="absolute right-1 top-1/2 -translate-y-1/2 text-slate-500 hover:text-emerald-400"
                                >
                                  <Variable className="w-3 h-3" />
                                </button>
                              </div>
                              <button
                                type="button"
                                onClick={() => {
                                  const newHeaders = { ...(data.headers || {}) };
                                  delete newHeaders[hKey];
                                  update({ headers: newHeaders });
                                }}
                                className="p-1 text-slate-500 hover:text-red-400"
                              >
                                <Trash2 className="w-3 h-3" />
                              </button>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Live Effective Headers Preview */}
                    <div className="pt-2 border-t border-slate-800/80">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-[10px] uppercase font-semibold text-slate-400">
                          Effective Headers Sent On Wire
                        </span>
                        <span className="text-[9px] text-slate-500 font-mono">Real-time preview</span>
                      </div>
                      {effectiveHeadersList.length === 0 ? (
                        <p className="text-[10px] text-slate-500 italic">No headers will be sent with this request.</p>
                      ) : (
                        <div className="space-y-1 p-2 bg-slate-900/60 rounded-lg border border-slate-800 max-h-36 overflow-y-auto font-mono text-[10px]">
                          {effectiveHeadersList.map((item, idx) => (
                            <div key={idx} className="flex items-center justify-between py-0.5 border-b border-slate-800/40 last:border-0">
                              <span className={item.origin === 'excluded' ? 'text-slate-600 line-through' : 'text-slate-300 font-semibold'}>
                                {item.key}: {item.value || '(empty)'}
                              </span>
                              <span
                                className={`text-[8px] px-1.5 py-0.2 rounded font-sans uppercase font-bold shrink-0 ${
                                  item.origin === 'global'
                                    ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                                    : item.origin === 'overridden'
                                    ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                                    : item.origin === 'custom'
                                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                                    : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                                }`}
                              >
                                {item.origin}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* JSON Body editor */}
                  {(data.method === 'POST' || data.method === 'PUT' || data.method === 'PATCH') && (
                    <div>
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-medium text-slate-300">Request Body (JSON)</label>
                        <button
                          type="button"
                          onClick={() => openVarSelector('body')}
                          className="flex items-center gap-1 text-[10px] text-emerald-400 hover:text-emerald-300"
                        >
                          <Variable className="w-3 h-3" />
                          Insert Variable
                        </button>
                      </div>
                      <textarea
                        value={typeof data.body === 'object' ? JSON.stringify(data.body, null, 2) : data.body || ''}
                        onChange={(e) => {
                          try {
                            const parsed = JSON.parse(e.target.value);
                            update({ body: parsed });
                          } catch {
                            update({ body: e.target.value });
                          }
                        }}
                        rows={7}
                        className="w-full p-2 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-300 focus:outline-none focus:border-blue-500"
                      />
                    </div>
                  )}
                </>
              )}

              {/* DB Poll specific config */}
              {nodeType === 'dbPoll' && (
                <>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">Database Preset</label>
                    <div className="grid grid-cols-4 gap-1.5 mb-2">
                      <button
                        type="button"
                        onClick={() => update({
                          driverClassName: 'oracle.jdbc.OracleDriver',
                          jdbcUrl: data.jdbcUrl?.includes('oracle') ? data.jdbcUrl : 'jdbc:oracle:thin:@//localhost:1521/XEPDB1'
                        })}
                        className={`px-2 py-1 rounded text-[10px] font-semibold border transition-all ${
                          (data.driverClassName?.includes('oracle') || data.jdbcUrl?.includes('oracle'))
                            ? 'bg-rose-500/20 text-rose-400 border-rose-500/40'
                            : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        Oracle (Thin)
                      </button>
                      <button
                        type="button"
                        onClick={() => update({
                          driverClassName: 'org.postgresql.Driver',
                          jdbcUrl: data.jdbcUrl?.includes('postgresql') ? data.jdbcUrl : 'jdbc:postgresql://localhost:5432/e2e_orchestrator'
                        })}
                        className={`px-2 py-1 rounded text-[10px] font-semibold border transition-all ${
                          (data.driverClassName?.includes('postgresql') || (!data.driverClassName && !data.jdbcUrl?.includes('oracle')))
                            ? 'bg-blue-500/20 text-blue-400 border-blue-500/40'
                            : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        PostgreSQL
                      </button>
                      <button
                        type="button"
                        onClick={() => update({
                          driverClassName: 'com.mysql.cj.jdbc.Driver',
                          jdbcUrl: data.jdbcUrl?.includes('mysql') ? data.jdbcUrl : 'jdbc:mysql://localhost:3306/mydb'
                        })}
                        className={`px-2 py-1 rounded text-[10px] font-semibold border transition-all ${
                          data.driverClassName?.includes('mysql') || data.jdbcUrl?.includes('mysql')
                            ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                            : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        MySQL
                      </button>
                      <button
                        type="button"
                        onClick={() => update({
                          driverClassName: 'org.h2.Driver',
                          jdbcUrl: data.jdbcUrl?.includes('h2') ? data.jdbcUrl : 'jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1'
                        })}
                        className={`px-2 py-1 rounded text-[10px] font-semibold border transition-all ${
                          data.driverClassName?.includes('h2') || data.jdbcUrl?.includes('h2')
                            ? 'bg-purple-500/20 text-purple-400 border-purple-500/40'
                            : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        H2
                      </button>
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="text-[11px] font-medium text-slate-300">JDBC URL</label>
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-slate-500 font-mono">
                          {(data.jdbcUrl?.includes('oracle') || data.driverClassName?.includes('oracle')) ? 'Oracle ojdbc17' : 'Auto-detected'}
                        </span>
                        <button
                          type="button"
                          onClick={() => openVarSelector('jdbcUrl')}
                          className="text-[10px] text-emerald-400 hover:text-emerald-300 flex items-center gap-0.5"
                          title="Insert Env Variable / Secret"
                        >
                          <Variable className="w-2.5 h-2.5" />
                          Var / Env
                        </button>
                      </div>
                    </div>

                    {(Object.keys(currentEnvVarsMap).length > 0 || Object.keys(currentSecretsMap).length > 0) && (
                      <div className="mb-1.5">
                        <select
                          onChange={(e) => {
                            if (e.target.value) {
                              update({ jdbcUrl: e.target.value });
                              e.target.value = '';
                            }
                          }}
                          defaultValue=""
                          className="w-full text-[11px] py-1 px-2 bg-slate-900 border border-slate-800 rounded-lg text-slate-300 focus:outline-none focus:border-amber-500 cursor-pointer"
                        >
                          <option value="" disabled>⚡ Quick Select URL from Env / Secrets...</option>
                          {Object.keys(currentEnvVarsMap).length > 0 && (
                            <optgroup label="Environment Variables">
                              {Object.keys(currentEnvVarsMap).map((k) => (
                                <option key={k} value={`\${${k}}`}>
                                  🌐 {k} ({`\${${k}}`})
                                </option>
                              ))}
                            </optgroup>
                          )}
                          {Object.keys(currentSecretsMap).length > 0 && (
                            <optgroup label="Secrets">
                              {Object.keys(currentSecretsMap).map((k) => (
                                <option key={k} value={`\${${k}}`}>
                                  🔒 {k} ({`\${${k}}`})
                                </option>
                              ))}
                            </optgroup>
                          )}
                        </select>
                      </div>
                    )}

                    <input
                      type="text"
                      value={data.jdbcUrl || ''}
                      onChange={(e) => update({ jdbcUrl: e.target.value })}
                      placeholder="jdbc:oracle:thin:@//localhost:1521/XEPDB1 or ${DB_URL}"
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-medium text-slate-300">DB Username</label>
                        <button
                          type="button"
                          onClick={() => openVarSelector('username')}
                          className="text-[10px] text-emerald-400 hover:text-emerald-300 flex items-center gap-0.5"
                          title="Insert Secret / Variable"
                        >
                          <Variable className="w-2.5 h-2.5" />
                          Var
                        </button>
                      </div>
                      <input
                        type="text"
                        value={data.username || ''}
                        onChange={(e) => update({ username: e.target.value })}
                        placeholder="postgres / ${DB_USER}"
                        className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                      />
                      {(Object.keys(currentEnvVarsMap).length > 0 || Object.keys(currentSecretsMap).length > 0) && (
                        <select
                          onChange={(e) => {
                            if (e.target.value) {
                              update({ username: e.target.value });
                              e.target.value = '';
                            }
                          }}
                          defaultValue=""
                          className="w-full text-[10px] py-1 px-1.5 bg-slate-900 border border-slate-800 rounded text-slate-400 focus:outline-none cursor-pointer mt-1"
                        >
                          <option value="" disabled>⚡ Bind Username...</option>
                          {Object.keys(currentEnvVarsMap).map((k) => (
                            <option key={k} value={`\${${k}}`}>🌐 {k}</option>
                          ))}
                          {Object.keys(currentSecretsMap).map((k) => (
                            <option key={k} value={`\${${k}}`}>🔒 {k}</option>
                          ))}
                        </select>
                      )}
                    </div>
                    <div>
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-medium text-slate-300">DB Password</label>
                        <button
                          type="button"
                          onClick={() => openVarSelector('password')}
                          className="text-[10px] text-emerald-400 hover:text-emerald-300 flex items-center gap-0.5"
                          title="Insert Secret / Variable"
                        >
                          <Variable className="w-2.5 h-2.5" />
                          Var
                        </button>
                      </div>
                      <input
                        type="password"
                        value={data.password || ''}
                        onChange={(e) => update({ password: e.target.value })}
                        placeholder="${DB_PASS}"
                        className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                      />
                      {Object.keys(currentSecretsMap).length > 0 && (
                        <select
                          onChange={(e) => {
                            if (e.target.value) {
                              update({ password: e.target.value });
                              e.target.value = '';
                            }
                          }}
                          defaultValue=""
                          className="w-full text-[10px] py-1 px-1.5 bg-slate-900 border border-slate-800 rounded text-slate-400 focus:outline-none cursor-pointer mt-1"
                        >
                          <option value="" disabled>⚡ Bind Secret...</option>
                          {Object.keys(currentSecretsMap).map((k) => (
                            <option key={k} value={`\${${k}}`}>🔒 {k}</option>
                          ))}
                        </select>
                      )}
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="text-[11px] font-medium text-slate-300">SQL Query</label>
                      <button
                        type="button"
                        onClick={() => openVarSelector('query')}
                        className="flex items-center gap-1 text-[10px] text-emerald-400 hover:text-emerald-300"
                      >
                        <Variable className="w-3 h-3" />
                        Insert Variable
                      </button>
                    </div>
                    <textarea
                      value={data.query || ''}
                      onChange={(e) => update({ query: e.target.value })}
                      rows={4}
                      placeholder="SELECT status FROM orders WHERE id = '${orderId}'"
                      className="w-full p-2 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-300 focus:outline-none focus:border-amber-500"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">Poll Match Condition</label>
                    <input
                      type="text"
                      value={data.condition || ''}
                      onChange={(e) => update({ condition: e.target.value })}
                      placeholder="row.status == 'COMPLETED' or count > 0"
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                    />
                  </div>

                  {/* Poll & Retry Policy for DB Poll */}
                  <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <RotateCcw className="w-3.5 h-3.5 text-amber-500" />
                        <label className="text-[11px] font-semibold text-slate-300">Poll & Retry Policy</label>
                      </div>
                      <span className="text-[10px] font-mono text-amber-500 font-semibold">
                        {(data.maxAttempts ?? data.retryCount ?? 5)}x attempts • {data.pollIntervalMs ?? 1000}ms interval
                      </span>
                    </div>

                    <div className="grid grid-cols-3 gap-2 pt-1">
                      <div>
                        <label className="text-[10px] text-slate-400 block mb-1">Max Attempts</label>
                        <input
                          type="number"
                          min="1"
                          max="100"
                          value={data.maxAttempts ?? data.retryCount ?? 5}
                          onChange={(e) => {
                            const val = Math.max(1, Math.min(100, parseInt(e.target.value) || 1));
                            update({ maxAttempts: val, retryCount: val });
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                        />
                      </div>
                      <div>
                        <label className="text-[10px] text-slate-400 block mb-1">Interval (ms)</label>
                        <input
                          type="number"
                          min="100"
                          step="250"
                          value={data.pollIntervalMs ?? 1000}
                          onChange={(e) => update({ pollIntervalMs: Math.max(100, parseInt(e.target.value) || 1000) })}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                        />
                      </div>
                      <div>
                        <label className="text-[10px] text-slate-400 block mb-1">Timeout (ms)</label>
                        <input
                          type="number"
                          min="500"
                          step="1000"
                          value={data.timeoutMs ?? 15000}
                          onChange={(e) => update({ timeoutMs: Math.max(500, parseInt(e.target.value) || 15000) })}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                        />
                      </div>
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-slate-500 pt-1 border-t border-slate-800/60">
                      <span>Stops when condition matches, max attempts reached, or timeout elapses.</span>
                    </div>
                  </div>
                </>
              )}

              {/* Kafka Assert config */}
              {nodeType === 'kafkaAssert' && (
                <>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">Kafka Topic</label>
                    <input
                      type="text"
                      value={data.topic || ''}
                      onChange={(e) => update({ topic: e.target.value })}
                      placeholder="order-events"
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-purple-500"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">JSONPath Assertion</label>
                    <input
                      type="text"
                      value={data.jsonPath || ''}
                      onChange={(e) => update({ jsonPath: e.target.value })}
                      placeholder="$.status"
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-purple-500"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-300 mb-1">Expected Value</label>
                    <input
                      type="text"
                      value={data.expectedValue || ''}
                      onChange={(e) => update({ expectedValue: e.target.value })}
                      placeholder="CONFIRMED"
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-purple-500"
                    />
                  </div>
                </>
              )}

              {/* Delay step */}
              {nodeType === 'delay' && (
                <div>
                  <label className="block text-[11px] font-medium text-slate-300 mb-1">Delay Duration (ms)</label>
                  <input
                    type="number"
                    value={data.delayMs || 1000}
                    onChange={(e) => update({ delayMs: parseInt(e.target.value) || 1000 })}
                    className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-cyan-500"
                  />
                </div>
              )}

              {/* Transform Script Step */}
              {nodeType === 'transform' && (
                <div className="space-y-3">
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <label className="text-[11px] font-semibold text-slate-300">JavaScript Transform Function</label>
                      <span className="text-[10px] text-violet-400 font-mono">function(ctx)</span>
                    </div>
                    <p className="text-[10px] text-slate-500 mb-2 leading-tight">
                      Receives workflow context as <code className="text-violet-400 font-mono">ctx</code>. The returned object will be merged into global workflow context for downstream steps.
                    </p>

                    {/* Quick Snippet Templates */}
                    <div className="flex flex-wrap gap-1.5 mb-2.5">
                      <button
                        type="button"
                        onClick={() =>
                          update({
                            script: `function(ctx) {\n  return {\n    timestamp: new Date().toISOString(),\n    nonce: Math.random().toString(36).substring(2, 10)\n  };\n}`,
                          })
                        }
                        className="px-2 py-1 rounded bg-violet-500/10 hover:bg-violet-500/20 text-violet-400 border border-violet-500/20 text-[10px] font-medium transition-colors"
                      >
                        + Timestamp & Nonce
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          update({
                            script: `function(ctx) {\n  var orderId = ctx.orderId || 'ORD-UNKNOWN';\n  return {\n    maskedOrder: orderId.substring(0, 4) + '****',\n    processedAt: Date.now()\n  };\n}`,
                          })
                        }
                        className="px-2 py-1 rounded bg-violet-500/10 hover:bg-violet-500/20 text-violet-400 border border-violet-500/20 text-[10px] font-medium transition-colors"
                      >
                        + Mask & Reshape
                      </button>
                    </div>

                    <textarea
                      rows={8}
                      value={data.script || 'function(ctx) {\n  return {\n    timestamp: new Date().toISOString()\n  };\n}'}
                      onChange={(e) => update({ script: e.target.value })}
                      className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-violet-500 leading-relaxed"
                      placeholder="function(ctx) { return { myVar: 'value' }; }"
                    />
                  </div>
                </div>
              )}

              {/* Step Execution & Reporting Settings */}
              <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-3">
                <span className="text-[11px] font-semibold text-slate-300 block">Execution & Reporting Options</span>

                {/* Step Enabled / Muted */}
                <label className="flex items-start gap-2.5 cursor-pointer pb-2 border-b border-slate-800/80">
                  <input
                    type="checkbox"
                    checked={data.enabled !== false}
                    onChange={(e) => update({ enabled: e.target.checked })}
                    className="w-4 h-4 mt-0.5 rounded border-slate-700 bg-slate-900 text-blue-500 focus:ring-0 cursor-pointer"
                  />
                  <div>
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-medium text-slate-200">Step Active</span>
                      {data.enabled === false && (
                        <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 border border-slate-700 flex items-center gap-1">
                          <EyeOff className="w-2.5 h-2.5" />
                          Muted / Skipped
                        </span>
                      )}
                    </div>
                    <span className="text-[10px] text-slate-500 block leading-tight">
                      When disabled, this step is skipped entirely during workflow execution without deleting its configuration.
                    </span>
                  </div>
                </label>

                {/* Continue on Failure */}
                <label className="flex items-start gap-2.5 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={!!data.continueOnFailure}
                    onChange={(e) => update({ continueOnFailure: e.target.checked })}
                    className="w-4 h-4 mt-0.5 rounded border-slate-700 bg-slate-900 text-blue-500 focus:ring-0 cursor-pointer"
                  />
                  <div>
                    <span className="text-xs font-medium text-slate-200 block">Continue Workflow on Failure</span>
                    <span className="text-[10px] text-slate-500 block leading-tight">
                      Allow downstream steps to continue running even if this step fails.
                    </span>
                  </div>
                </label>

                {/* Exclude from Report */}
                <label className="flex items-start gap-2.5 cursor-pointer pt-2 border-t border-slate-800/80">
                  <input
                    type="checkbox"
                    checked={!!data.excludeFromReport}
                    onChange={(e) => update({ excludeFromReport: e.target.checked })}
                    className="w-4 h-4 mt-0.5 rounded border-slate-700 bg-slate-900 text-amber-500 focus:ring-0 cursor-pointer"
                  />
                  <div>
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-medium text-slate-200">Exclude from Report & Metrics</span>
                      <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">
                        Auxiliary Step
                      </span>
                    </div>
                    <span className="text-[10px] text-slate-500 block leading-tight">
                      Run this step normally for context/variables, but omit it from the report pass/fail scorecard and latency metrics.
                    </span>
                  </div>
                </label>
              </div>

              {/* Step Links & Connections */}
              <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-300">Connections & Links</span>
                  <span className="text-[10px] text-slate-500 font-mono">Modifiable</span>
                </div>

                {/* Predecessors */}
                <div>
                  <span className="text-[10px] text-slate-500 uppercase font-semibold block mb-1">Incoming Links (From)</span>
                  {edges.filter((e) => e.target === selectedNode.id).length === 0 ? (
                    <p className="text-[10px] text-slate-600 italic">No incoming links (entry step)</p>
                  ) : (
                    <div className="space-y-1">
                      {edges
                        .filter((e) => e.target === selectedNode.id)
                        .map((edge) => {
                          const srcNode = allNodes.find((n) => n.id === edge.source);
                          return (
                            <div
                              key={edge.id}
                              className="flex items-center justify-between p-1.5 rounded bg-slate-900 border border-slate-800 text-[11px]"
                            >
                              <span className="truncate text-slate-300">{srcNode?.data?.name || edge.source}</span>
                              {onDeleteEdge && (
                                <button
                                  type="button"
                                  onClick={() => onDeleteEdge(edge.id)}
                                  className="text-[10px] text-rose-400 hover:text-rose-300 hover:underline px-1"
                                >
                                  Disconnect
                                </button>
                              )}
                            </div>
                          );
                        })}
                    </div>
                  )}
                </div>

                {/* Successors */}
                <div>
                  <span className="text-[10px] text-slate-500 uppercase font-semibold block mb-1">Outgoing Links (To)</span>
                  {edges.filter((e) => e.source === selectedNode.id).length === 0 ? (
                    <p className="text-[10px] text-slate-600 italic">No outgoing links (terminal step)</p>
                  ) : (
                    <div className="space-y-1">
                      {edges
                        .filter((e) => e.source === selectedNode.id)
                        .map((edge) => {
                          const targetNode = allNodes.find((n) => n.id === edge.target);
                          return (
                            <div
                              key={edge.id}
                              className="flex items-center justify-between p-1.5 rounded bg-slate-900 border border-slate-800 text-[11px]"
                            >
                              <span className="truncate text-slate-300">{targetNode?.data?.name || edge.target}</span>
                              {onDeleteEdge && (
                                <button
                                  type="button"
                                  onClick={() => onDeleteEdge(edge.id)}
                                  className="text-[10px] text-rose-400 hover:text-rose-300 hover:underline px-1"
                                >
                                  Disconnect
                                </button>
                              )}
                            </div>
                          );
                        })}
                    </div>
                  )}
                </div>
              </div>
            </>
          )}

          {/* Extractions Tab */}
          {activeTab === 'extractions' && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400">Extract fields into runtime variables</span>
                <button
                  type="button"
                  onClick={addExtraction}
                  className="flex items-center gap-1 px-2 py-1 rounded bg-emerald-600/20 text-emerald-400 hover:bg-emerald-600/30 text-[11px] font-medium transition-colors"
                >
                  <Plus className="w-3 h-3" />
                  Add Variable
                </button>
              </div>

              {(data.extractions || []).map((ex, i) => (
                <div key={i} className="p-3 bg-slate-950 border border-slate-800 rounded-lg space-y-2 relative group">
                  <button
                    onClick={() => removeExtraction(i)}
                    className="absolute top-2 right-2 text-slate-500 hover:text-rose-400 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>

                  <div>
                    <label className="block text-[10px] text-slate-400 mb-0.5">Variable Name</label>
                    <input
                      type="text"
                      value={ex.variableName}
                      onChange={(e) => updateExtraction(i, { variableName: e.target.value })}
                      placeholder="e.g. authToken"
                      className="w-full px-2 py-1 bg-slate-900 border border-slate-800 rounded text-xs font-mono text-emerald-400 focus:outline-none focus:border-emerald-500"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[10px] text-slate-400 mb-0.5">Source</label>
                      <select
                        value={ex.source}
                        onChange={(e) => updateExtraction(i, { source: e.target.value as any })}
                        className="w-full px-2 py-1 bg-slate-900 border border-slate-800 rounded text-[11px] text-slate-200 focus:outline-none"
                      >
                        <option value="RESPONSE_BODY">Response Body</option>
                        <option value="RESPONSE_HEADER">Header</option>
                        <option value="RESPONSE_STATUS">Status</option>
                        <option value="DB_ROW_COLUMN">DB Column</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[10px] text-slate-400 mb-0.5">JSONPath</label>
                      <input
                        type="text"
                        value={ex.jsonPath}
                        onChange={(e) => updateExtraction(i, { jsonPath: e.target.value })}
                        placeholder="$.data.token"
                        className="w-full px-2 py-1 bg-slate-900 border border-slate-800 rounded text-xs font-mono text-slate-200 focus:outline-none"
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Assertions Tab */}
          {activeTab === 'assertions' && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400">Validate responses and status</span>
                <button
                  type="button"
                  onClick={addAssertion}
                  className="flex items-center gap-1 px-2 py-1 rounded bg-blue-600/20 text-blue-400 hover:bg-blue-600/30 text-[11px] font-medium transition-colors"
                >
                  <Plus className="w-3 h-3" />
                  Add Assertion
                </button>
              </div>

              {(data.assertions || []).map((as, i) => (
                <div key={i} className="p-3 bg-slate-950 border border-slate-800 rounded-lg space-y-2 relative group">
                  <button
                    onClick={() => removeAssertion(i)}
                    className="absolute top-2 right-2 text-slate-500 hover:text-rose-400 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>

                  <div>
                    <label className="block text-[10px] text-slate-400 mb-0.5">Target Field</label>
                    <input
                      type="text"
                      value={as.field}
                      onChange={(e) => updateAssertion(i, { field: e.target.value })}
                      placeholder="status or body.status"
                      className="w-full px-2 py-1 bg-slate-900 border border-slate-800 rounded text-xs font-mono text-blue-400 focus:outline-none focus:border-blue-500"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[10px] text-slate-400 mb-0.5">Operator</label>
                      <select
                        value={as.operator}
                        onChange={(e) => updateAssertion(i, { operator: e.target.value as any })}
                        className="w-full px-2 py-1 bg-slate-900 border border-slate-800 rounded text-[11px] text-slate-200 focus:outline-none"
                      >
                        <option value="EQUALS">EQUALS</option>
                        <option value="NOT_EQUALS">NOT EQUALS</option>
                        <option value="CONTAINS">CONTAINS</option>
                        <option value="GREATER_THAN">GREATER THAN</option>
                        <option value="LESS_THAN">LESS THAN</option>
                        <option value="IS_NOT_NULL">IS NOT NULL</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[10px] text-slate-400 mb-0.5">Expected Value</label>
                      <input
                        type="text"
                        value={as.expectedValue ?? ''}
                        onChange={(e) => updateAssertion(i, { expectedValue: e.target.value })}
                        placeholder="200 or CONFIRMED"
                        className="w-full px-2 py-1 bg-slate-900 border border-slate-800 rounded text-xs font-mono text-slate-200 focus:outline-none"
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Variable Selector Modal */}
      {isVarModalOpen && (
        <Suspense fallback={null}>
          <VariableSelectorModal
            isOpen={isVarModalOpen}
            onClose={() => setIsVarModalOpen(false)}
            onSelectVariable={handleInsertVariable}
            availableNodes={allNodes}
            currentNodeId={selectedNode.id}
            envProperties={envProperties}
            envSecrets={envSecrets}
            secrets={secrets}
            activeEnvironment={activeEnvironment}
          />
        </Suspense>
      )}
    </>
  );
};
