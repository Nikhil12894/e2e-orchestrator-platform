import React, { useState, useEffect, Suspense } from 'react';
import {
  ChevronDown,
  ChevronRight,
  Search,
  Plus,
  Server,
  Layers,
  Sparkles,
  Database,
  Radio,
  Timer,
  Code2,
  GripVertical,
  RefreshCw,
  FolderOpen,
  Trash2,
  AlertTriangle,
  X
} from 'lucide-react';
import { ApiEndpoint, HttpMethod } from '../../types/workflow';
import { catalogApi } from '../../api/client';

const ImportSpecModal = React.lazy(() =>
  import('./ImportSpecModal').then((m) => ({ default: m.ImportSpecModal }))
);

const methodBadges: Record<HttpMethod, string> = {
  GET: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
  POST: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
  PUT: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
  DELETE: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
  PATCH: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
};

interface ApiCatalogSidebarProps {
  isOpen: boolean;
  onClose: () => void;
  onAddCustomNode: (type: string, initialData?: Record<string, any>) => void;
}

export const ApiCatalogSidebar: React.FC<ApiCatalogSidebarProps> = ({
  isOpen,
  onClose,
  onAddCustomNode,
}) => {
  const [endpoints, setEndpoints] = useState<ApiEndpoint[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [collapsedServices, setCollapsedServices] = useState<Record<string, boolean>>({});
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);

  // Deletion confirmation states
  const [serviceToDelete, setServiceToDelete] = useState<{ name: string; count: number } | null>(null);
  const [isClearConfirmOpen, setIsClearConfirmOpen] = useState(false);

  const fetchEndpoints = async () => {
    setLoading(true);
    try {
      const data = await catalogApi.getEndpoints(undefined, search);
      setEndpoints(data);
    } catch (err) {
      console.error('Failed to fetch endpoints', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEndpoints();
  }, [search]);

  const toggleService = (service: string) => {
    setCollapsedServices((prev) => ({ ...prev, [service]: !prev[service] }));
  };

  const handleSeedSamples = async () => {
    setLoading(true);
    try {
      await catalogApi.seedSampleServices();
      await fetchEndpoints();
    } catch (err) {
      console.error('Failed to seed samples', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteService = async () => {
    if (!serviceToDelete) return;
    setLoading(true);
    try {
      await catalogApi.deleteService(serviceToDelete.name);
      setServiceToDelete(null);
      await fetchEndpoints();
    } catch (err) {
      console.error('Failed to delete service', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteEndpoint = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    try {
      await catalogApi.deleteEndpoint(id);
      await fetchEndpoints();
    } catch (err) {
      console.error('Failed to delete endpoint', err);
    }
  };

  const handleClearCatalog = async () => {
    setLoading(true);
    try {
      await catalogApi.clearCatalog();
      setIsClearConfirmOpen(false);
      await fetchEndpoints();
    } catch (err) {
      console.error('Failed to clear catalog', err);
    } finally {
      setLoading(false);
    }
  };

  // Group endpoints by microservice
  const groupedEndpoints = endpoints.reduce((acc, ep) => {
    const service = ep.serviceName || 'General';
    if (!acc[service]) acc[service] = [];
    acc[service].push(ep);
    return acc;
  }, {} as Record<string, ApiEndpoint[]>);

  const handleDragStart = (e: React.DragEvent, endpoint: ApiEndpoint) => {
    const nodePayload = {
      type: 'httpRequest',
      data: {
        name: endpoint.summary || endpoint.operationId || `${endpoint.httpMethod} ${endpoint.endpointPath}`,
        method: endpoint.httpMethod,
        url: `http://localhost:8080${endpoint.endpointPath}`,
        headers: { 'Content-Type': 'application/json' },
        body: endpoint.sampleRequestPayload && Object.keys(endpoint.sampleRequestPayload).length > 0
          ? endpoint.sampleRequestPayload
          : undefined,
        expectedStatus: endpoint.httpMethod === 'POST' ? 201 : 200,
        extractions: [],
        assertions: [],
      },
    };
    e.dataTransfer.setData('application/reactflow', JSON.stringify(nodePayload));
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleDragGenericStep = (e: React.DragEvent, type: string, defaultData: Record<string, any>) => {
    const payload = {
      type,
      data: defaultData,
    };
    e.dataTransfer.setData('application/reactflow', JSON.stringify(payload));
    e.dataTransfer.effectAllowed = 'move';
  };

  if (!isOpen) return null;

  return (
    <>
      <aside className="w-80 h-full bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 flex flex-col z-20 shrink-0 select-none transition-colors">
        {/* Header */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-blue-500 dark:text-blue-400" />
              <h2 className="font-semibold text-xs text-slate-800 dark:text-slate-200 tracking-wide uppercase">API Catalog</h2>
            </div>
            <button
              onClick={fetchEndpoints}
              disabled={loading}
              title="Refresh Catalog"
              className="p-1 rounded text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-blue-500' : ''}`} />
            </button>
          </div>

          {/* Search bar */}
          <div className="relative mb-3">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter endpoints or tags..."
              className="w-full pl-8 pr-3 py-1.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-xs text-slate-800 dark:text-slate-200 placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:border-blue-500"
            />
          </div>

          {/* Action buttons */}
          <div className="grid grid-cols-3 gap-1.5">
            <button
              onClick={() => setIsImportModalOpen(true)}
              className="flex items-center justify-center gap-1 py-1.5 px-2 bg-blue-600/10 hover:bg-blue-600/20 text-blue-600 dark:text-blue-400 border border-blue-500/20 rounded-lg text-[10px] font-medium transition-colors"
            >
              <Plus className="w-3 h-3" />
              Import
            </button>
            <button
              onClick={handleSeedSamples}
              className="flex items-center justify-center gap-1 py-1.5 px-2 bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 rounded-lg text-[10px] font-medium transition-colors"
            >
              <Sparkles className="w-3 h-3" />
              Seed
            </button>
            <button
              onClick={() => setIsClearConfirmOpen(true)}
              disabled={endpoints.length === 0}
              className="flex items-center justify-center gap-1 py-1.5 px-2 bg-rose-600/10 hover:bg-rose-600/20 text-rose-600 dark:text-rose-400 border border-rose-500/20 rounded-lg text-[10px] font-medium transition-colors disabled:opacity-40"
            >
              <Trash2 className="w-3 h-3" />
              Clear
            </button>
          </div>
        </div>

        {/* Step Node Toolbox */}
        <div className="p-3 border-b border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-950/40">
          <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block mb-2 px-1">
            Drag Nodes to Canvas
          </span>
          <div className="grid grid-cols-4 gap-1.5">
            <div
              draggable
              onDragStart={(e) =>
                handleDragGenericStep(e, 'dbPoll', {
                  name: 'DB SQL Poll',
                  jdbcUrl: 'jdbc:postgresql://localhost:5432/e2e_orchestrator',
                  query: "SELECT status FROM orders WHERE id = '${orderId}'",
                  condition: "row.status == 'COMPLETED'",
                  timeoutMs: 15000,
                })
              }
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 hover:border-amber-500/40 hover:bg-amber-500/5 cursor-grab active:cursor-grabbing transition-all group shadow-sm"
              title="Poll database via SQL query until condition is met"
            >
              <Database className="w-4 h-4 text-amber-500 dark:text-amber-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[9px] font-medium text-slate-700 dark:text-slate-300">DB Poll</span>
            </div>

            <div
              draggable
              onDragStart={(e) =>
                handleDragGenericStep(e, 'transform', {
                  name: 'JS Transform',
                  script: "function(ctx) {\n  return {\n    timestamp: new Date().toISOString()\n  };\n}",
                })
              }
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 hover:border-violet-500/40 hover:bg-violet-500/5 cursor-grab active:cursor-grabbing transition-all group shadow-sm"
              title="Execute custom JavaScript function and return transformed context variables"
            >
              <Code2 className="w-4 h-4 text-violet-500 dark:text-violet-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[9px] font-medium text-slate-700 dark:text-slate-300">Transform</span>
            </div>

            <div
              draggable
              onDragStart={(e) =>
                handleDragGenericStep(e, 'kafkaAssert', {
                  name: 'Kafka Assert',
                  bootstrapServers: 'localhost:9092',
                  topic: 'order-events',
                  jsonPath: '$.status',
                  expectedValue: 'CONFIRMED',
                })
              }
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 hover:border-purple-500/40 hover:bg-purple-500/5 cursor-grab active:cursor-grabbing transition-all group shadow-sm"
              title="Assert message delivery on Kafka topic"
            >
              <Radio className="w-4 h-4 text-purple-500 dark:text-purple-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[9px] font-medium text-slate-700 dark:text-slate-300">Kafka</span>
            </div>

            <div
              draggable
              onDragStart={(e) =>
                handleDragGenericStep(e, 'delay', {
                  name: 'Delay',
                  delayMs: 2000,
                })
              }
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 hover:border-cyan-500/40 hover:bg-cyan-500/5 cursor-grab active:cursor-grabbing transition-all group shadow-sm"
              title="Pause execution flow for async processing"
            >
              <Timer className="w-4 h-4 text-cyan-500 dark:text-cyan-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[9px] font-medium text-slate-700 dark:text-slate-300">Delay</span>
            </div>
          </div>
        </div>

        {/* Microservice Endpoints Tree */}
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {Object.keys(groupedEndpoints).length === 0 ? (
            <div className="text-center py-10 px-4">
              <FolderOpen className="w-8 h-8 text-slate-400 dark:text-slate-600 mx-auto mb-2 opacity-50" />
              <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">No endpoints in catalog</p>
              <p className="text-[11px] text-slate-400 dark:text-slate-500 mt-1">Import a spec or click "Seed APIs" to load demo services</p>
            </div>
          ) : (
            Object.entries(groupedEndpoints).map(([service, eps]) => {
              const isCollapsed = collapsedServices[service];
              return (
                <div key={service} className="rounded-lg border border-slate-200 dark:border-slate-800/80 bg-slate-50/50 dark:bg-slate-950/40 overflow-hidden shadow-sm">
                  {/* Service Header */}
                  <div
                    onClick={() => toggleService(service)}
                    className="w-full flex items-center justify-between px-3 py-2 bg-slate-100/70 dark:bg-slate-900/60 hover:bg-slate-200/60 dark:hover:bg-slate-900 text-left transition-colors cursor-pointer select-none"
                  >
                    <div className="flex items-center gap-2">
                      {isCollapsed ? (
                        <ChevronRight className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" />
                      ) : (
                        <ChevronDown className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" />
                      )}
                      <Server className="w-3.5 h-3.5 text-blue-500 dark:text-blue-400" />
                      <span className="font-semibold text-xs text-slate-800 dark:text-slate-200">{service}</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="text-[10px] font-mono px-1.5 py-0.5 rounded-full bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                        {eps.length}
                      </span>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setServiceToDelete({ name: service, count: eps.length });
                        }}
                        title={`Delete ${service} spec & endpoints`}
                        className="p-1 rounded text-slate-400 hover:text-rose-500 hover:bg-rose-500/10 transition-colors"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  </div>

                  {/* Endpoints List */}
                  {!isCollapsed && (
                    <div className="p-1 space-y-1">
                      {eps.map((ep) => {
                        const badgeStyle = methodBadges[ep.httpMethod] || methodBadges.GET;
                        return (
                          <div
                            key={ep.id}
                            draggable
                            onDragStart={(e) => handleDragStart(e, ep)}
                            className="group flex items-center justify-between p-2 rounded-md hover:bg-slate-100 dark:hover:bg-slate-900/80 border border-transparent hover:border-slate-200 dark:hover:border-slate-800 cursor-grab active:cursor-grabbing transition-all"
                          >
                            <div className="flex items-center gap-2 min-w-0 flex-1">
                              <GripVertical className="w-3 h-3 text-slate-400 dark:text-slate-600 group-hover:text-slate-500 dark:group-hover:text-slate-400 shrink-0" />
                              <span
                                className={`px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wider border shrink-0 ${badgeStyle}`}
                              >
                                {ep.httpMethod}
                              </span>
                              <div className="min-w-0 flex-1">
                                <p className="text-[11px] font-medium text-slate-700 dark:text-slate-300 truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                                  {ep.summary || ep.operationId || ep.endpointPath}
                                </p>
                                <p className="text-[10px] font-mono text-slate-400 dark:text-slate-500 truncate">{ep.endpointPath}</p>
                              </div>
                            </div>
                            <button
                              onClick={(e) => handleDeleteEndpoint(e, ep.id)}
                              title="Delete endpoint from catalog"
                              className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 hover:bg-rose-500/10 transition-all"
                            >
                              <Trash2 className="w-3 h-3" />
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </aside>

      {/* Delete Microservice Confirmation Modal */}
      {serviceToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 select-none">
          <div className="w-full max-w-sm bg-slate-900 border border-slate-800 rounded-xl shadow-2xl p-5 space-y-4 animate-in fade-in zoom-in-95">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400 shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-slate-100">Remove Microservice?</h3>
                <p className="text-xs text-slate-400">
                  Delete <span className="text-rose-300 font-semibold">{serviceToDelete.name}</span> and all{' '}
                  <span className="font-mono text-slate-800 dark:text-slate-200">{serviceToDelete.count}</span> endpoints from the catalog.
                </p>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
              <button
                onClick={() => setServiceToDelete(null)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteService}
                className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold shadow-lg shadow-rose-600/20 transition-all"
              >
                Delete Service
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Clear Entire Catalog Confirmation Modal */}
      {isClearConfirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none">
          <div className="w-full max-w-sm bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-2xl p-5 space-y-4 animate-in fade-in zoom-in-95 text-slate-800 dark:text-slate-200">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-500 shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">Clear Entire Catalog?</h3>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  This will remove all <span className="font-semibold text-rose-500 dark:text-rose-300">{endpoints.length}</span> endpoints across all services from the catalog.
                </p>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
              <button
                onClick={() => setIsClearConfirmOpen(false)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleClearCatalog}
                className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold shadow-lg shadow-rose-600/20 transition-all"
              >
                Clear All
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Import Modal */}
      {isImportModalOpen && (
        <Suspense fallback={null}>
          <ImportSpecModal
            isOpen={isImportModalOpen}
            onClose={() => setIsImportModalOpen(false)}
            onSuccess={fetchEndpoints}
          />
        </Suspense>
      )}
    </>
  );
};
