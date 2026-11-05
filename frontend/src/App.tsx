import React, { useState, useEffect, useCallback, Suspense } from 'react';
import {
  Node,
  Edge,
  applyNodeChanges,
  applyEdgeChanges,
  OnNodesChange,
  OnEdgesChange,
  Connection,
  addEdge,
  reconnectEdge,
} from '@xyflow/react';

import { Navbar } from './components/navbar/Navbar';
import { ApiCatalogSidebar } from './components/catalog/ApiCatalogSidebar';
import { FlowCanvas } from './components/canvas/FlowCanvas';
import { NodeConfigDrawer } from './components/drawer/NodeConfigDrawer';
import { LiveExecutionConsole } from './components/console/LiveExecutionConsole';
import { workflowApi, executionApi } from './api/client';
import {
  TestWorkflow,
  WorkflowExecution,
  ExecutionEventDto,
  CanvasNode,
  CanvasEdge,
  CanvasNodeData,
} from './types/workflow';

const CreateWorkflowModal = React.lazy(() =>
  import('./components/navbar/CreateWorkflowModal').then((m) => ({ default: m.CreateWorkflowModal }))
);
const WorkflowSettingsModal = React.lazy(() =>
  import('./components/navbar/WorkflowSettingsModal').then((m) => ({ default: m.WorkflowSettingsModal }))
);
const ReportViewerModal = React.lazy(() =>
  import('./components/console/ReportViewerModal').then((m) => ({ default: m.ReportViewerModal }))
);

export function App() {
  const [workflows, setWorkflows] = useState<TestWorkflow[]>([]);
  const [selectedWorkflow, setSelectedWorkflow] = useState<TestWorkflow | null>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);

  const [selectedNode, setSelectedNode] = useState<CanvasNode | null>(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isConsoleOpen, setIsConsoleOpen] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);

  // Execution state
  const [environment, setEnvironment] = useState('default');
  const [isRunning, setIsRunning] = useState(false);
  const [activeExecution, setActiveExecution] = useState<WorkflowExecution | null>(null);
  const [workflowExecutions, setWorkflowExecutions] = useState<WorkflowExecution[]>([]);
  const [executionEvents, setExecutionEvents] = useState<ExecutionEventDto[]>([]);

  // Report Modal state
  const [reportModalUrl, setReportModalUrl] = useState<string | null>(null);

  // Delete edge handler (used by RemovableEdge midpoint button & NodeConfigDrawer)
  const handleDeleteEdge = useCallback((edgeId: string) => {
    setEdges((eds) => eds.filter((e) => e.id !== edgeId));
  }, []);

  // 1. Load workflows on mount
  const loadWorkflows = async () => {
    try {
      const list = await workflowApi.getAllWorkflows();
      setWorkflows(list);
      if (list.length > 0 && !selectedWorkflow) {
        selectWorkflow(list[0]);
      }
    } catch (err) {
      console.error('Failed to load workflows', err);
    }
  };

  useEffect(() => {
    loadWorkflows();
  }, []);

  const selectWorkflow = (wf: TestWorkflow) => {
    setSelectedWorkflow(wf);
    const canvas = wf.canvasState || { nodes: [], edges: [] };

    // Format nodes for React Flow
    const flowNodes: Node[] = (canvas.nodes || []).map((n) => ({
      id: n.id,
      type: n.type,
      position: n.position || { x: 100, y: 100 },
      data: n.data || {},
    }));

    // Format edges for React Flow with custom removable edge type and delete callback
    const flowEdges: Edge[] = (canvas.edges || []).map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      type: 'removable',
      animated: e.animated ?? true,
      style: { stroke: '#475569', strokeWidth: 2 },
      data: { onDelete: handleDeleteEdge },
    }));

    setNodes(flowNodes);
    setEdges(flowEdges);
    setSelectedNode(null);
    setIsDrawerOpen(false);

    // Fetch executions history for this workflow
    executionApi
      .getExecutions(wf.id)
      .then((runs) => {
        setWorkflowExecutions(runs);
        if (runs.length > 0) {
          setActiveExecution(runs[0]);
        } else {
          setActiveExecution(null);
        }
      })
      .catch((err) => {
        console.warn('Failed to load workflow execution history', err);
      });
  };

  // Node & Edge change handlers
  const onNodesChange: OnNodesChange<Node> = useCallback(
    (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
    []
  );

  const onEdgesChange: OnEdgesChange<Edge> = useCallback(
    (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
    []
  );

  // Reconnect edge when user drags edge endpoint
  const onReconnect = useCallback(
    (oldEdge: Edge, newConnection: Connection) =>
      setEdges((eds) => reconnectEdge(oldEdge, newConnection, eds)),
    []
  );

  const onConnect = useCallback(
    (connection: Connection) =>
      setEdges((eds) =>
        addEdge(
          {
            ...connection,
            type: 'removable',
            animated: true,
            style: { stroke: '#475569', strokeWidth: 2 },
            data: { onDelete: handleDeleteEdge },
          },
          eds
        )
      ),
    [handleDeleteEdge]
  );

  const handleNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    setSelectedNode({
      id: node.id,
      type: node.type || 'httpRequest',
      position: node.position,
      data: (node.data as unknown) as CanvasNodeData,
    });
    setIsDrawerOpen(true);
  }, []);

  const handleUpdateNodeData = (nodeId: string, patch: Partial<CanvasNodeData>) => {
    setNodes((nds) =>
      nds.map((n) => {
        if (n.id === nodeId) {
          const updated = { ...n.data, ...patch };
          if (selectedNode?.id === nodeId) {
            setSelectedNode((prev) => (prev ? { ...prev, data: updated as CanvasNodeData } : null));
          }
          return { ...n, data: updated };
        }
        return n;
      })
    );
  };

  // Drag-and-drop new node to canvas
  const handleDropNewNode = (type: string, position: { x: number; y: number }, initialData: Record<string, any>) => {
    const newNodeId = `step_${Date.now().toString().slice(-6)}`;
    const newNode: Node = {
      id: newNodeId,
      type,
      position,
      data: {
        name: initialData.name || `New ${type}`,
        ...initialData,
      },
    };

    setNodes((prev) => [...prev, newNode]);

    // Automatically connect with previous node if there's only 1 preceding node
    if (nodes.length > 0) {
      const lastNode = nodes[nodes.length - 1];
      const newEdge: Edge = {
        id: `e_${lastNode.id}_${newNodeId}`,
        source: lastNode.id,
        target: newNodeId,
        type: 'removable',
        animated: true,
        style: { stroke: '#475569', strokeWidth: 2 },
        data: { onDelete: handleDeleteEdge },
      };
      setEdges((prev) => [...prev, newEdge]);
    }
  };

  // Save current workflow
  const handleSaveWorkflow = async (extraPatch?: Partial<TestWorkflow>) => {
    if (!selectedWorkflow) return;

    const canvasNodes: CanvasNode[] = nodes.map((n) => ({
      id: n.id,
      type: n.type || 'httpRequest',
      position: n.position,
      data: (n.data as unknown) as CanvasNodeData,
    }));

    const canvasEdges: CanvasEdge[] = edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      animated: e.animated,
    }));

    const currentCanvas = selectedWorkflow.canvasState || {
      nodes: [],
      edges: [],
      viewport: { x: 0, y: 0, zoom: 1 },
    };

    try {
      const updated = await workflowApi.updateWorkflow(selectedWorkflow.id, {
        name: extraPatch?.name ?? selectedWorkflow.name,
        description: extraPatch?.description ?? selectedWorkflow.description,
        canvasState: {
          nodes: canvasNodes,
          edges: canvasEdges,
          viewport: currentCanvas.viewport || { x: 0, y: 0, zoom: 1 },
          variables: currentCanvas.variables,
          defaultHeaders: extraPatch?.canvasState?.defaultHeaders ?? currentCanvas.defaultHeaders,
          dynamicHeaders: extraPatch?.canvasState?.dynamicHeaders ?? currentCanvas.dynamicHeaders,
          envProperties: extraPatch?.canvasState?.envProperties ?? currentCanvas.envProperties,
          secrets: extraPatch?.canvasState?.secrets ?? currentCanvas.secrets,
        },
      });

      setSelectedWorkflow(updated);
      setWorkflows((prev) => prev.map((w) => (w.id === updated.id ? updated : w)));
      return updated;
    } catch (err) {
      console.error('Failed to save workflow', err);
    }
  };

  // Delete workflow
  const handleDeleteWorkflow = async (workflowId: string) => {
    try {
      await workflowApi.deleteWorkflow(workflowId);
      const remaining = workflows.filter((w) => w.id !== workflowId);
      setWorkflows(remaining);
      if (selectedWorkflow?.id === workflowId) {
        if (remaining.length > 0) {
          selectWorkflow(remaining[0]);
        } else {
          setSelectedWorkflow(null);
          setNodes([]);
          setEdges([]);
        }
      }
      setActiveExecution(null);
      setExecutionEvents([]);
      setWorkflowExecutions([]);
    } catch (err) {
      console.error('Failed to delete workflow', err);
      alert('Failed to delete workflow. Please check server logs.');
    }
  };

  // Delete a single execution run (and its disk report)
  const handleDeleteExecution = async (id: string) => {
    try {
      await executionApi.deleteExecution(id);
      setWorkflowExecutions((prev) => {
        const remaining = prev.filter((e) => e.id !== id);
        if (activeExecution?.id === id) {
          setActiveExecution(remaining.length > 0 ? remaining[0] : null);
        }
        return remaining;
      });
    } catch (err) {
      console.error('Failed to delete execution', err);
    }
  };

  // Clear all execution runs for current workflow (and all disk reports)
  const handleClearWorkflowExecutions = async (workflowId: string) => {
    try {
      await executionApi.clearWorkflowExecutions(workflowId);
      setWorkflowExecutions([]);
      setActiveExecution(null);
      setExecutionEvents([]);
    } catch (err) {
      console.error('Failed to clear workflow executions', err);
    }
  };

  // Import workflow from JSON
  const handleImportWorkflow = async (workflowData: Partial<TestWorkflow>) => {
    try {
      const imported = await workflowApi.importWorkflow(workflowData);
      setWorkflows((prev) => [imported, ...prev]);
      selectWorkflow(imported);
    } catch (err) {
      console.error('Failed to import workflow', err);
      alert('Failed to import workflow. Please ensure JSON format is valid.');
    }
  };

  // Cancel running execution
  const handleCancelExecution = async (executionId: string) => {
    try {
      const cancelled = await executionApi.cancelExecution(executionId);
      setActiveExecution(cancelled);
      setWorkflowExecutions((prev) =>
        prev.map((e) => (e.id === executionId ? cancelled : e))
      );
      setIsRunning(false);
    } catch (err) {
      console.error('Failed to cancel execution', err);
    }
  };

  // Create new workflow with custom name and template
  const handleCreateCustomWorkflow = async (
    name: string,
    description: string,
    template: 'blank' | 'ecommerce'
  ) => {
    try {
      let initNodes: CanvasNode[] = [];
      let initEdges: CanvasEdge[] = [];

      if (template === 'ecommerce') {
        initNodes = [
          {
            id: 'step_auth',
            type: 'httpRequest',
            position: { x: 150, y: 150 },
            data: {
              name: 'User Authentication',
              method: 'POST',
              url: 'http://localhost:8080/api/v1/mock/auth/login',
              body: { username: 'testuser@example.com', password: 'password123' },
              expectedStatus: 200,
              extractions: [
                { variableName: 'authToken', source: 'RESPONSE_BODY', jsonPath: '$.token' },
                { variableName: 'userId', source: 'RESPONSE_BODY', jsonPath: '$.userId' },
              ],
            },
          },
          {
            id: 'step_order',
            type: 'httpRequest',
            position: { x: 150, y: 320 },
            data: {
              name: 'Submit New Order',
              method: 'POST',
              url: 'http://localhost:8080/api/v1/mock/orders',
              headers: { Authorization: 'Bearer ${authToken}' },
              body: { userId: '${userId}', items: ['SKU-882'], totalAmount: 149.99 },
              expectedStatus: 201,
              extractions: [
                { variableName: 'orderId', source: 'RESPONSE_BODY', jsonPath: '$.orderId' },
              ],
            },
          },
        ];
        initEdges = [
          { id: 'e_auth_order', source: 'step_auth', target: 'step_order', animated: true },
        ];
      }

      const created = await workflowApi.createWorkflow({
        name,
        description: description || 'Custom end-to-end automated test workflow',
        canvasState: {
          nodes: initNodes,
          edges: initEdges,
          viewport: { x: 0, y: 0, zoom: 1 },
          defaultHeaders: { Accept: 'application/json', 'Content-Type': 'application/json' },
          dynamicHeaders: { 'X-Request-Id': '${$randomUuid}' },
          envProperties: {
            default: { baseUrl: 'http://localhost:8080' },
            staging: { baseUrl: 'https://staging.internal' },
            production: { baseUrl: 'https://api.internal' },
          },
          secrets: {},
        },
      });

      setWorkflows((prev) => [created, ...prev]);
      selectWorkflow(created);
    } catch (err) {
      console.error('Failed to create workflow', err);
    }
  };

  // Handle saving workflow settings from WorkflowSettingsModal
  const handleSaveWorkflowSettings = async (settings: {
    name: string;
    description: string;
    defaultHeaders: Record<string, string>;
    dynamicHeaders: Record<string, string>;
    envProperties: Record<string, Record<string, any>>;
    envSecrets: Record<string, Record<string, string>>;
    secrets: Record<string, string>;
  }) => {
    if (!selectedWorkflow) return;
    await handleSaveWorkflow({
      name: settings.name,
      description: settings.description,
      canvasState: {
        ...(selectedWorkflow.canvasState || { nodes: [], edges: [], viewport: { x: 0, y: 0, zoom: 1 } }),
        defaultHeaders: settings.defaultHeaders,
        dynamicHeaders: settings.dynamicHeaders,
        envProperties: settings.envProperties,
        envSecrets: settings.envSecrets,
        secrets: settings.secrets,
      },
    });
  };

  // Run Workflow execution
  const handleRunWorkflow = async () => {
    if (!selectedWorkflow) return;

    // First auto-save workflow to ensure AST is compiled
    await handleSaveWorkflow();

    setIsRunning(true);
    setIsConsoleOpen(true);
    setExecutionEvents([]);

    // Clear previous step statuses on canvas
    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: {
          ...n.data,
          status: undefined,
          durationMs: undefined,
        },
      }))
    );

    try {
      const execution = await executionApi.triggerExecution(selectedWorkflow.id, {}, environment);
      setActiveExecution(execution);

      // Resilient Polling Fallback (1-second interval):
      // Guarantees UI completion even if SSE drops, lags, or buffers
      let isDone = false;
      const pollInterval = setInterval(async () => {
        if (isDone) return;
        try {
          const fresh = await executionApi.getExecutionById(execution.id);
          if (fresh.status === 'COMPLETED' || fresh.status === 'FAILED') {
            isDone = true;
            clearInterval(pollInterval);
            setIsRunning(false);
            setActiveExecution(fresh);
            setWorkflowExecutions((prev) => [fresh, ...prev.filter((e) => e.id !== fresh.id)]);

            // Sync canvas node statuses
            if (fresh.stepResults) {
              setNodes((nds) =>
                nds.map((n) => {
                  const stepRes = fresh.stepResults?.find((s) => s.stepId === n.id);
                  if (stepRes) {
                    return {
                      ...n,
                      data: {
                        ...n.data,
                        status: stepRes.status,
                        durationMs: stepRes.durationMs,
                      },
                    };
                  }
                  return n;
                })
              );
            }
          }
        } catch (e) {
          console.warn('Execution status poll check error', e);
        }
      }, 1000);

      // Subscribe to real-time SSE event stream
      const unsubscribe = executionApi.subscribeToExecutionEvents(
        execution.id,
        (event) => {
          setExecutionEvents((prev) => [...prev, event]);

          // Transition status to RUNNING as soon as execution starts
          if (event.eventType === 'WORKFLOW_STARTED') {
            setActiveExecution((prev) => (prev ? { ...prev, status: 'RUNNING' } : prev));
          }

          // Update node status on canvas in real time
          if (event.stepId) {
            setNodes((nds) =>
              nds.map((n) => {
                if (n.id === event.stepId) {
                  return {
                    ...n,
                    data: {
                      ...n.data,
                      status: event.status,
                      durationMs: event.details?.durationMs ?? n.data.durationMs,
                    },
                  };
                }
                return n;
              })
            );
          }

          if (event.eventType === 'WORKFLOW_FINISHED') {
            isDone = true;
            clearInterval(pollInterval);
            setIsRunning(false);

            // Hydrate immediately from event.details
            if (event.details && (event.details as any).id) {
              const finishedExec = event.details as WorkflowExecution;
              setActiveExecution(finishedExec);
              setWorkflowExecutions((prev) => [finishedExec, ...prev.filter((e) => e.id !== finishedExec.id)]);
            } else {
              executionApi.getExecutionById(execution.id).then((fresh) => {
                setActiveExecution(fresh);
                setWorkflowExecutions((prev) => [fresh, ...prev.filter((e) => e.id !== fresh.id)]);
              });
            }
            unsubscribe();
          }
        },
        (err) => {
          console.warn('SSE stream disconnected; relying on polling fallback', err);
        }
      );
    } catch (err) {
      console.error('Failed to trigger execution', err);
      setIsRunning(false);
    }
  };

  // Convert React Flow nodes to CanvasNode format for drawer
  const allCanvasNodes: CanvasNode[] = nodes.map((n) => ({
    id: n.id,
    type: n.type || 'httpRequest',
    position: n.position,
    data: (n.data as unknown) as CanvasNodeData,
  }));

  return (
    <div className="w-screen h-screen flex flex-col bg-slate-50 dark:bg-slate-950 overflow-hidden text-slate-900 dark:text-slate-100 font-sans transition-colors duration-150">
      {/* Top Navbar */}
      <Navbar
        workflows={workflows}
        selectedWorkflow={selectedWorkflow}
        onSelectWorkflow={selectWorkflow}
        onCreateNewWorkflow={() => setIsCreateModalOpen(true)}
        onOpenSettings={() => setIsSettingsModalOpen(true)}
        onSaveWorkflow={handleSaveWorkflow}
        onDeleteWorkflow={handleDeleteWorkflow}
        onRunWorkflow={handleRunWorkflow}
        isRunning={isRunning}
        environment={environment}
        onEnvironmentChange={setEnvironment}
        latestExecution={activeExecution}
        workflowExecutions={workflowExecutions}
        onViewReport={(url) => setReportModalUrl(url)}
        onImportWorkflow={handleImportWorkflow}
        onDeleteExecution={handleDeleteExecution}
        onClearWorkflowExecutions={handleClearWorkflowExecutions}
        isSidebarOpen={isSidebarOpen}
        onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
        isConsoleOpen={isConsoleOpen}
        onToggleConsole={() => setIsConsoleOpen(!isConsoleOpen)}
      />

      {/* Main Workspace: Sidebar + Canvas */}
      <div className="flex-1 flex overflow-hidden relative">
        <ApiCatalogSidebar
          isOpen={isSidebarOpen}
          onClose={() => setIsSidebarOpen(false)}
          onAddCustomNode={(type, initData) =>
            handleDropNewNode(type, { x: 250, y: 150 + nodes.length * 120 }, initData || {})
          }
        />

        <main className="flex-1 h-full relative">
          <FlowCanvas
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onSetNodes={setNodes}
            onConnect={onConnect}
            onReconnect={onReconnect}
            onNodeClick={handleNodeClick}
            onDropNewNode={handleDropNewNode}
          />
        </main>

        {/* Right slide-over Node Config Drawer */}
        <NodeConfigDrawer
          isOpen={isDrawerOpen}
          onClose={() => setIsDrawerOpen(false)}
          selectedNode={selectedNode}
          onUpdateNodeData={handleUpdateNodeData}
          allNodes={allCanvasNodes}
          edges={edges.map((e) => ({ id: e.id, source: e.source, target: e.target }))}
          onDeleteEdge={handleDeleteEdge}
          workflowHeaders={{
            ...(selectedWorkflow?.canvasState?.defaultHeaders || {}),
            ...(selectedWorkflow?.canvasState?.dynamicHeaders || {}),
          }}
          envProperties={selectedWorkflow?.canvasState?.envProperties}
          envSecrets={selectedWorkflow?.canvasState?.envSecrets}
          secrets={selectedWorkflow?.canvasState?.secrets}
          activeEnvironment={environment}
          currentWorkflowId={selectedWorkflow?.id}
        />
      </div>

      {/* Bottom Live Execution Console */}
      <LiveExecutionConsole
        execution={activeExecution}
        events={executionEvents}
        isOpen={isConsoleOpen}
        onToggle={() => setIsConsoleOpen(!isConsoleOpen)}
        onViewReport={(url) => setReportModalUrl(url)}
        onCancelExecution={handleCancelExecution}
      />

      {/* Lazy Modals loaded on-demand */}
      <Suspense fallback={null}>
        {/* Embedded Karate HTML & Profiling Report Modal */}
        {reportModalUrl && (
          <ReportViewerModal
            isOpen={!!reportModalUrl}
            onClose={() => setReportModalUrl(null)}
            reportUrl={reportModalUrl}
            workflowName={selectedWorkflow?.name || 'Workflow Run'}
          />
        )}

        {/* Create New Workflow Modal */}
        {isCreateModalOpen && (
          <CreateWorkflowModal
            isOpen={isCreateModalOpen}
            onClose={() => setIsCreateModalOpen(false)}
            onCreate={handleCreateCustomWorkflow}
          />
        )}

        {/* Workflow Settings & Variables Modal */}
        {isSettingsModalOpen && (
          <WorkflowSettingsModal
            isOpen={isSettingsModalOpen}
            onClose={() => setIsSettingsModalOpen(false)}
            workflow={selectedWorkflow}
            onSaveSettings={handleSaveWorkflowSettings}
            onDeleteWorkflow={handleDeleteWorkflow}
          />
        )}
      </Suspense>
    </div>
  );
}

export default App;
