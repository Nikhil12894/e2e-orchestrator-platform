import React, { useState, useEffect } from 'react';
import {
  X,
  Sliders,
  Globe,
  Zap,
  Server,
  Lock,
  Plus,
  Trash2,
  Eye,
  EyeOff,
  Sparkles,
  Info,
  Check
} from 'lucide-react';
import { TestWorkflow } from '../../types/workflow';

interface WorkflowSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  workflow: TestWorkflow | null;
  onSaveSettings: (settings: {
    name: string;
    description: string;
    defaultHeaders: Record<string, string>;
    dynamicHeaders: Record<string, string>;
    envProperties: Record<string, Record<string, any>>;
    secrets: Record<string, string>;
  }) => void;
  onDeleteWorkflow?: (workflowId: string) => void;
}

export const WorkflowSettingsModal: React.FC<WorkflowSettingsModalProps> = ({
  isOpen,
  onClose,
  workflow,
  onSaveSettings,
  onDeleteWorkflow,
}) => {
  const [activeTab, setActiveTab] = useState<'general' | 'defaultHeaders' | 'dynamicHeaders' | 'envProperties' | 'secrets'>('general');

  // Form states
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  // Default Headers key-value list
  const [defaultHeadersList, setDefaultHeadersList] = useState<Array<{ key: string; value: string }>>([]);

  // Dynamic Headers key-value list
  const [dynamicHeadersList, setDynamicHeadersList] = useState<Array<{ key: string; value: string }>>([]);

  // Env Properties
  const [selectedEnv, setSelectedEnv] = useState<'default' | 'staging' | 'production'>('default');
  const [envPropertiesMap, setEnvPropertiesMap] = useState<Record<string, Array<{ key: string; value: string }>>>({
    default: [],
    staging: [],
    production: [],
  });

  // Secrets key-value list & visibility map
  const [secretsList, setSecretsList] = useState<Array<{ key: string; value: string }>>([]);
  const [revealedSecrets, setRevealedSecrets] = useState<Record<number, boolean>>({});

  useEffect(() => {
    if (!workflow) return;

    setName(workflow.name || '');
    setDescription(workflow.description || '');

    // Default headers
    const defH = workflow.canvasState?.defaultHeaders || workflow.workflowAst?.defaultHeaders || {};
    setDefaultHeadersList(Object.entries(defH).map(([key, value]) => ({ key, value })));

    // Dynamic headers
    const dynH = workflow.canvasState?.dynamicHeaders || workflow.workflowAst?.dynamicHeaders || {};
    setDynamicHeadersList(Object.entries(dynH).map(([key, value]) => ({ key, value })));

    // Env properties
    const envP = workflow.canvasState?.envProperties || workflow.workflowAst?.envProperties || {};
    const parsedEnv: Record<string, Array<{ key: string; value: string }>> = {
      default: [],
      staging: [],
      production: [],
    };
    for (const envKey of ['default', 'staging', 'production']) {
      const vals = envP[envKey] || {};
      parsedEnv[envKey] = Object.entries(vals).map(([k, v]) => ({ key: k, value: String(v) }));
    }
    setEnvPropertiesMap(parsedEnv);

    // Secrets
    const sec = workflow.canvasState?.secrets || workflow.workflowAst?.secrets || {};
    setSecretsList(Object.entries(sec).map(([key, value]) => ({ key, value })));
    setRevealedSecrets({});
  }, [workflow, isOpen]);

  if (!isOpen || !workflow) return null;

  const handleSave = () => {
    // Transform arrays back to objects
    const defaultHeaders: Record<string, string> = {};
    for (const item of defaultHeadersList) {
      if (item.key.trim()) defaultHeaders[item.key.trim()] = item.value;
    }

    const dynamicHeaders: Record<string, string> = {};
    for (const item of dynamicHeadersList) {
      if (item.key.trim()) dynamicHeaders[item.key.trim()] = item.value;
    }

    const envProperties: Record<string, Record<string, any>> = {};
    for (const [env, items] of Object.entries(envPropertiesMap)) {
      const map: Record<string, any> = {};
      for (const item of items) {
        if (item.key.trim()) {
          // Attempt numeric conversion if applicable
          const val = item.value.trim();
          map[item.key.trim()] = /^-?\d+(\.\d+)?$/.test(val) ? Number(val) : val;
        }
      }
      envProperties[env] = map;
    }

    const secrets: Record<string, string> = {};
    for (const item of secretsList) {
      if (item.key.trim()) secrets[item.key.trim()] = item.value;
    }

    onSaveSettings({
      name: name.trim() || workflow.name,
      description: description.trim(),
      defaultHeaders,
      dynamicHeaders,
      envProperties,
      secrets,
    });
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none">
      <div className="w-full max-w-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-2xl flex flex-col max-h-[88vh] overflow-hidden animate-in fade-in zoom-in-95 duration-150 text-slate-800 dark:text-slate-100 transition-colors">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-500 dark:text-indigo-400">
              <Sliders className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                Workflow Settings & Variables
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                  v{workflow.version}
                </span>
              </h2>
              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                Configure workflow identity, global headers, dynamic correlation IDs, env variables, and secrets
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/40 px-6 pt-1 gap-1">
          <button
            onClick={() => setActiveTab('general')}
            className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
              activeTab === 'general'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400 font-semibold'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            General
          </button>

          <button
            onClick={() => setActiveTab('defaultHeaders')}
            className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
              activeTab === 'defaultHeaders'
                ? 'border-blue-500 text-blue-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Globe className="w-3.5 h-3.5" />
            Global Headers
            {defaultHeadersList.length > 0 && (
              <span className="px-1.5 py-0.2 rounded-full text-[9px] bg-blue-500/20 text-blue-400 font-mono">
                {defaultHeadersList.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('dynamicHeaders')}
            className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
              activeTab === 'dynamicHeaders'
                ? 'border-amber-500 text-amber-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Zap className="w-3.5 h-3.5" />
            Dynamic Headers
            {dynamicHeadersList.length > 0 && (
              <span className="px-1.5 py-0.2 rounded-full text-[9px] bg-amber-500/20 text-amber-400 font-mono">
                {dynamicHeadersList.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('envProperties')}
            className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
              activeTab === 'envProperties'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Server className="w-3.5 h-3.5" />
            Env Variables
          </button>

          <button
            onClick={() => setActiveTab('secrets')}
            className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-all ${
              activeTab === 'secrets'
                ? 'border-rose-500 text-rose-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Lock className="w-3.5 h-3.5" />
            Secrets
            {secretsList.length > 0 && (
              <span className="px-1.5 py-0.2 rounded-full text-[9px] bg-rose-500/20 text-rose-400 font-mono">
                {secretsList.length}
              </span>
            )}
          </button>
        </div>

        {/* Tab Content Body */}
        <div className="p-6 overflow-y-auto flex-1 space-y-5">
          {/* TAB 1: General */}
          {activeTab === 'general' && (
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">Workflow Name</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Order Processing & Verification"
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">Description</label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  placeholder="Explain what endpoints and business flows this workflow exercises..."
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800 text-[11px] space-y-1.5 text-slate-400">
                <div className="flex justify-between">
                  <span>Workflow UUID:</span>
                  <span className="font-mono text-slate-300">{workflow.id}</span>
                </div>
                <div className="flex justify-between">
                  <span>Execution Engine:</span>
                  <span className="font-semibold text-emerald-400">Karate 2.1.4 (In-Memory Runtime)</span>
                </div>
                <div className="flex justify-between">
                  <span>Created At:</span>
                  <span className="font-mono text-slate-300">{new Date(workflow.createdAt).toLocaleString()}</span>
                </div>
              </div>

              {/* Danger Zone: Delete Workflow */}
              {onDeleteWorkflow && (
                <div className="p-4 rounded-xl border border-rose-500/30 bg-rose-500/5 space-y-2">
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="text-xs font-semibold text-rose-300">Danger Zone</h4>
                      <p className="text-[11px] text-slate-400 mt-0.5">
                        Permanently delete this workflow and all associated execution reports.
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        if (confirm(`Are you sure you want to permanently delete workflow "${workflow.name}"?`)) {
                          onDeleteWorkflow(workflow.id);
                          onClose();
                        }
                      }}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-500/30 text-xs font-semibold transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Delete Workflow</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: Global Default Headers */}
          {activeTab === 'defaultHeaders' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-xs font-medium text-slate-200">Workflow-Level HTTP Headers</h4>
                  <p className="text-[11px] text-slate-400">
                    Headers sent on every HTTP call across this workflow. Step-level headers can override these.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setDefaultHeadersList((prev) => [...prev, { key: '', value: '' }])}
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-blue-600/10 hover:bg-blue-600/20 text-blue-400 border border-blue-500/20 text-xs font-medium transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Header
                </button>
              </div>

              {defaultHeadersList.length === 0 ? (
                <div className="p-8 text-center border border-dashed border-slate-800 rounded-lg bg-slate-950/30">
                  <p className="text-xs text-slate-500 mb-2">No global headers configured yet.</p>
                  <button
                    type="button"
                    onClick={() =>
                      setDefaultHeadersList([
                        { key: 'Accept', value: 'application/json' },
                        { key: 'Content-Type', value: 'application/json' },
                      ])
                    }
                    className="text-xs text-blue-400 hover:underline"
                  >
                    + Add standard JSON headers (Accept & Content-Type)
                  </button>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-12 gap-2 text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-1">
                    <span className="col-span-5">Header Key</span>
                    <span className="col-span-6">Header Value</span>
                    <span className="col-span-1 text-right">Action</span>
                  </div>
                  {defaultHeadersList.map((item, idx) => (
                    <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                      <div className="col-span-5">
                        <input
                          type="text"
                          value={item.key}
                          placeholder="e.g. Accept, User-Agent"
                          onChange={(e) => {
                            const val = e.target.value;
                            setDefaultHeadersList((prev) =>
                              prev.map((h, i) => (i === idx ? { ...h, key: val } : h))
                            );
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div className="col-span-6">
                        <input
                          type="text"
                          value={item.value}
                          placeholder="e.g. application/json"
                          onChange={(e) => {
                            const val = e.target.value;
                            setDefaultHeadersList((prev) =>
                              prev.map((h, i) => (i === idx ? { ...h, value: val } : h))
                            );
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div className="col-span-1 text-right">
                        <button
                          type="button"
                          onClick={() =>
                            setDefaultHeadersList((prev) => prev.filter((_, i) => i !== idx))
                          }
                          className="p-1.5 text-slate-500 hover:text-red-400 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: Dynamic Headers (Evaluated once per run) */}
          {activeTab === 'dynamicHeaders' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-xs font-medium text-slate-200">Execution-Time Dynamic Headers</h4>
                  <p className="text-[11px] text-slate-400">
                    Evaluated once at run start and reused consistently across all steps in that workflow execution.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setDynamicHeadersList((prev) => [...prev, { key: '', value: '' }])}
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-amber-600/10 hover:bg-amber-600/20 text-amber-400 border border-amber-500/20 text-xs font-medium transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Dynamic Header
                </button>
              </div>

              {/* Macro helper pills */}
              <div className="p-3 bg-amber-500/5 border border-amber-500/20 rounded-lg space-y-1.5">
                <span className="text-[11px] font-semibold text-amber-300 flex items-center gap-1">
                  <Sparkles className="w-3 h-3" /> Available Dynamic Macros:
                </span>
                <div className="flex flex-wrap gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => {
                      setDynamicHeadersList((prev) => [
                        ...prev,
                        { key: 'X-Request-Id', value: '${$randomUuid}' },
                      ]);
                    }}
                    className="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[10px] font-mono text-amber-300 hover:border-amber-500/50"
                  >
                    + X-Request-Id: {'${$randomUuid}'}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setDynamicHeadersList((prev) => [
                        ...prev,
                        { key: 'X-Correlation-Id', value: '${$randomUuid}' },
                      ]);
                    }}
                    className="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[10px] font-mono text-amber-300 hover:border-amber-500/50"
                  >
                    + X-Correlation-Id: {'${$randomUuid}'}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setDynamicHeadersList((prev) => [
                        ...prev,
                        { key: 'X-Timestamp', value: '${$timestamp}' },
                      ]);
                    }}
                    className="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[10px] font-mono text-amber-300 hover:border-amber-500/50"
                  >
                    + X-Timestamp: {'${$timestamp}'}
                  </button>
                </div>
              </div>

              {dynamicHeadersList.length === 0 ? (
                <div className="p-8 text-center border border-dashed border-slate-800 rounded-lg bg-slate-950/30">
                  <p className="text-xs text-slate-500">No dynamic headers configured.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-12 gap-2 text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-1">
                    <span className="col-span-5">Header Key</span>
                    <span className="col-span-6">Expression / Macro</span>
                    <span className="col-span-1 text-right">Action</span>
                  </div>
                  {dynamicHeadersList.map((item, idx) => (
                    <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                      <div className="col-span-5">
                        <input
                          type="text"
                          value={item.key}
                          placeholder="e.g. X-Request-Id"
                          onChange={(e) => {
                            const val = e.target.value;
                            setDynamicHeadersList((prev) =>
                              prev.map((h, i) => (i === idx ? { ...h, key: val } : h))
                            );
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-amber-500"
                        />
                      </div>
                      <div className="col-span-6">
                        <input
                          type="text"
                          value={item.value}
                          placeholder="${$randomUuid}"
                          onChange={(e) => {
                            const val = e.target.value;
                            setDynamicHeadersList((prev) =>
                              prev.map((h, i) => (i === idx ? { ...h, value: val } : h))
                            );
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-amber-300 focus:outline-none focus:border-amber-500"
                        />
                      </div>
                      <div className="col-span-1 text-right">
                        <button
                          type="button"
                          onClick={() =>
                            setDynamicHeadersList((prev) => prev.filter((_, i) => i !== idx))
                          }
                          className="p-1.5 text-slate-500 hover:text-red-400 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 4: Environment Properties */}
          {activeTab === 'envProperties' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-xs font-medium text-slate-200">Environment Scoped Properties</h4>
                  <p className="text-[11px] text-slate-400">
                    Values resolve automatically depending on the selected execution environment.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <select
                    value={selectedEnv}
                    onChange={(e) => setSelectedEnv(e.target.value as any)}
                    className="px-2.5 py-1 bg-slate-950 border border-slate-800 rounded-lg text-xs font-medium text-slate-200 focus:outline-none focus:border-emerald-500 cursor-pointer"
                  >
                    <option value="default">Env: Local (Mock)</option>
                    <option value="staging">Env: Staging</option>
                    <option value="production">Env: Production</option>
                  </select>

                  <button
                    type="button"
                    onClick={() => {
                      setEnvPropertiesMap((prev) => ({
                        ...prev,
                        [selectedEnv]: [...(prev[selectedEnv] || []), { key: '', value: '' }],
                      }));
                    }}
                    className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-400 border border-emerald-500/20 text-xs font-medium transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Add Property
                  </button>
                </div>
              </div>

              <div className="p-2.5 bg-emerald-500/5 border border-emerald-500/20 rounded-lg text-[11px] text-slate-300">
                Variables configured here can be referenced in step URLs, headers, and request bodies as{' '}
                <code className="text-emerald-400 font-mono">{'${propertyName}'}</code> or{' '}
                <code className="text-emerald-400 font-mono">{'${env.propertyName}'}</code>.
              </div>

              {(envPropertiesMap[selectedEnv] || []).length === 0 ? (
                <div className="p-8 text-center border border-dashed border-slate-800 rounded-lg bg-slate-950/30">
                  <p className="text-xs text-slate-500 mb-2">
                    No variables configured for environment: <span className="text-emerald-400 font-semibold">{selectedEnv}</span>
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      setEnvPropertiesMap((prev) => ({
                        ...prev,
                        [selectedEnv]: [
                          { key: 'baseUrl', value: selectedEnv === 'production' ? 'https://api.prod.example.com' : 'http://localhost:8080' },
                        ],
                      }));
                    }}
                    className="text-xs text-emerald-400 hover:underline"
                  >
                    + Add standard baseUrl property
                  </button>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-12 gap-2 text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-1">
                    <span className="col-span-5">Property Name</span>
                    <span className="col-span-6">Value ({selectedEnv})</span>
                    <span className="col-span-1 text-right">Action</span>
                  </div>
                  {envPropertiesMap[selectedEnv].map((item, idx) => (
                    <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                      <div className="col-span-5">
                        <input
                          type="text"
                          value={item.key}
                          placeholder="e.g. baseUrl, timeoutMs"
                          onChange={(e) => {
                            const val = e.target.value;
                            setEnvPropertiesMap((prev) => ({
                              ...prev,
                              [selectedEnv]: prev[selectedEnv].map((p, i) =>
                                i === idx ? { ...p, key: val } : p
                              ),
                            }));
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-emerald-500"
                        />
                      </div>
                      <div className="col-span-6">
                        <input
                          type="text"
                          value={item.value}
                          placeholder="Value for this environment"
                          onChange={(e) => {
                            const val = e.target.value;
                            setEnvPropertiesMap((prev) => ({
                              ...prev,
                              [selectedEnv]: prev[selectedEnv].map((p, i) =>
                                i === idx ? { ...p, value: val } : p
                              ),
                            }));
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-emerald-500"
                        />
                      </div>
                      <div className="col-span-1 text-right">
                        <button
                          type="button"
                          onClick={() =>
                            setEnvPropertiesMap((prev) => ({
                              ...prev,
                              [selectedEnv]: prev[selectedEnv].filter((_, i) => i !== idx),
                            }))
                          }
                          className="p-1.5 text-slate-500 hover:text-red-400 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 5: Secrets */}
          {activeTab === 'secrets' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-xs font-medium text-slate-200">Secrets Management</h4>
                  <p className="text-[11px] text-slate-400">
                    Sensitive tokens and passwords. Redacted as [REDACTED] in logs, console, and HTML reports.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSecretsList((prev) => [...prev, { key: '', value: '' }])}
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-rose-600/10 hover:bg-rose-600/20 text-rose-400 border border-rose-500/20 text-xs font-medium transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Secret
                </button>
              </div>

              <div className="p-3 bg-rose-500/10 border border-rose-500/30 rounded-lg flex items-start gap-2.5">
                <Lock className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <div className="text-[11px] text-rose-200 leading-relaxed">
                  <span className="font-semibold text-rose-300">Automatic Log Redaction Active: </span>
                  Any secret value registered here will never leak in plaintext. MeterFlow automatically sanitizes
                  live console outputs and Karate execution summaries. Reference them via{' '}
                  <code className="text-rose-300 font-mono">{'${secretName}'}</code>.
                </div>
              </div>

              {secretsList.length === 0 ? (
                <div className="p-8 text-center border border-dashed border-slate-800 rounded-lg bg-slate-950/30">
                  <p className="text-xs text-slate-500">No secrets configured for this workflow.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-12 gap-2 text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-1">
                    <span className="col-span-5">Secret Key</span>
                    <span className="col-span-6">Secret Value</span>
                    <span className="col-span-1 text-right">Action</span>
                  </div>
                  {secretsList.map((item, idx) => (
                    <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                      <div className="col-span-5">
                        <input
                          type="text"
                          value={item.key}
                          placeholder="e.g. apiKey, clientSecret"
                          onChange={(e) => {
                            const val = e.target.value;
                            setSecretsList((prev) =>
                              prev.map((s, i) => (i === idx ? { ...s, key: val } : s))
                            );
                          }}
                          className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-rose-500"
                        />
                      </div>
                      <div className="col-span-6 relative">
                        <input
                          type={revealedSecrets[idx] ? 'text' : 'password'}
                          value={item.value}
                          placeholder="••••••••"
                          onChange={(e) => {
                            const val = e.target.value;
                            setSecretsList((prev) =>
                              prev.map((s, i) => (i === idx ? { ...s, value: val } : s))
                            );
                          }}
                          className="w-full pr-8 px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-rose-300 focus:outline-none focus:border-rose-500"
                        />
                        <button
                          type="button"
                          onClick={() =>
                            setRevealedSecrets((prev) => ({ ...prev, [idx]: !prev[idx] }))
                          }
                          className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                        >
                          {revealedSecrets[idx] ? (
                            <EyeOff className="w-3.5 h-3.5" />
                          ) : (
                            <Eye className="w-3.5 h-3.5" />
                          )}
                        </button>
                      </div>
                      <div className="col-span-1 text-right">
                        <button
                          type="button"
                          onClick={() =>
                            setSecretsList((prev) => prev.filter((_, i) => i !== idx))
                          }
                          className="p-1.5 text-slate-500 hover:text-red-400 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-end gap-2.5 px-6 py-3.5 border-t border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60">
          <button
            type="button"
            onClick={onClose}
            className="px-3.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-lg shadow-blue-600/20 transition-all"
          >
            <Check className="w-3.5 h-3.5" />
            Save Settings
          </button>
        </div>
      </div>
    </div>
  );
};
