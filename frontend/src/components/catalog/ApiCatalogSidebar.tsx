import React, { useState, useEffect } from 'react';
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
  GripVertical,
  RefreshCw,
  FolderOpen
} from 'lucide-react';
import { ApiEndpoint, HttpMethod } from '../../types/workflow';
import { catalogApi } from '../../api/client';
import { ImportSpecModal } from './ImportSpecModal';

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
      <aside className="w-80 h-full bg-slate-900 border-r border-slate-800 flex flex-col z-20 shrink-0 select-none">
        {/* Header */}
        <div className="p-4 border-b border-slate-800">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-blue-400" />
              <h2 className="font-semibold text-xs text-slate-200 tracking-wide uppercase">API Catalog</h2>
            </div>
            <button
              onClick={fetchEndpoints}
              disabled={loading}
              title="Refresh Catalog"
              className="p-1 rounded text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-blue-400' : ''}`} />
            </button>
          </div>

          {/* Search bar */}
          <div className="relative mb-3">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter endpoints or tags..."
              className="w-full pl-8 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-blue-500"
            />
          </div>

          {/* Action buttons */}
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => setIsImportModalOpen(true)}
              className="flex items-center justify-center gap-1.5 py-1.5 px-2 bg-blue-600/10 hover:bg-blue-600/20 text-blue-400 border border-blue-500/20 rounded-lg text-[11px] font-medium transition-colors"
            >
              <Plus className="w-3 h-3" />
              Import Spec
            </button>
            <button
              onClick={handleSeedSamples}
              className="flex items-center justify-center gap-1.5 py-1.5 px-2 bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-400 border border-emerald-500/20 rounded-lg text-[11px] font-medium transition-colors"
            >
              <Sparkles className="w-3 h-3" />
              Seed APIs
            </button>
          </div>
        </div>

        {/* Step Node Toolbox */}
        <div className="p-3 border-b border-slate-800 bg-slate-950/40">
          <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block mb-2 px-1">
            Drag Nodes to Canvas
          </span>
          <div className="grid grid-cols-3 gap-2">
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
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-slate-900 border border-slate-800 hover:border-amber-500/40 hover:bg-amber-500/5 cursor-grab active:cursor-grabbing transition-all group"
            >
              <Database className="w-4 h-4 text-amber-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[10px] font-medium text-slate-300">DB Poll</span>
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
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-slate-900 border border-slate-800 hover:border-purple-500/40 hover:bg-purple-500/5 cursor-grab active:cursor-grabbing transition-all group"
            >
              <Radio className="w-4 h-4 text-purple-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[10px] font-medium text-slate-300">Kafka</span>
            </div>

            <div
              draggable
              onDragStart={(e) =>
                handleDragGenericStep(e, 'delay', {
                  name: 'Delay',
                  delayMs: 2000,
                })
              }
              className="flex flex-col items-center justify-center p-2 rounded-lg bg-slate-900 border border-slate-800 hover:border-cyan-500/40 hover:bg-cyan-500/5 cursor-grab active:cursor-grabbing transition-all group"
            >
              <Timer className="w-4 h-4 text-cyan-400 mb-1 group-hover:scale-110 transition-transform" />
              <span className="text-[10px] font-medium text-slate-300">Delay</span>
            </div>
          </div>
        </div>

        {/* Microservice Endpoints Tree */}
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {Object.keys(groupedEndpoints).length === 0 ? (
            <div className="text-center py-10 px-4">
              <FolderOpen className="w-8 h-8 text-slate-600 mx-auto mb-2 opacity-50" />
              <p className="text-xs text-slate-400 font-medium">No endpoints in catalog</p>
              <p className="text-[11px] text-slate-500 mt-1">Import a spec or click "Seed APIs" to load demo services</p>
            </div>
          ) : (
            Object.entries(groupedEndpoints).map(([service, eps]) => {
              const isCollapsed = collapsedServices[service];
              return (
                <div key={service} className="rounded-lg border border-slate-800/80 bg-slate-950/40 overflow-hidden">
                  {/* Service Header */}
                  <button
                    onClick={() => toggleService(service)}
                    className="w-full flex items-center justify-between px-3 py-2 bg-slate-900/60 hover:bg-slate-900 text-left transition-colors"
                  >
                    <div className="flex items-center gap-2">
                      {isCollapsed ? (
                        <ChevronRight className="w-3.5 h-3.5 text-slate-500" />
                      ) : (
                        <ChevronDown className="w-3.5 h-3.5 text-slate-500" />
                      )}
                      <Server className="w-3.5 h-3.5 text-blue-400" />
                      <span className="font-semibold text-xs text-slate-200">{service}</span>
                    </div>
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded-full bg-slate-800 text-slate-400">
                      {eps.length}
                    </span>
                  </button>

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
                            className="group flex items-center justify-between p-2 rounded-md hover:bg-slate-900/80 border border-transparent hover:border-slate-800 cursor-grab active:cursor-grabbing transition-all"
                          >
                            <div className="flex items-center gap-2 min-w-0 flex-1">
                              <GripVertical className="w-3 h-3 text-slate-600 group-hover:text-slate-400 shrink-0" />
                              <span
                                className={`px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wider border shrink-0 ${badgeStyle}`}
                              >
                                {ep.httpMethod}
                              </span>
                              <div className="min-w-0 flex-1">
                                <p className="text-[11px] font-medium text-slate-300 truncate group-hover:text-blue-400 transition-colors">
                                  {ep.summary || ep.operationId || ep.endpointPath}
                                </p>
                                <p className="text-[10px] font-mono text-slate-500 truncate">{ep.endpointPath}</p>
                              </div>
                            </div>
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

      {/* Import Modal */}
      <ImportSpecModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        onSuccess={fetchEndpoints}
      />
    </>
  );
};
