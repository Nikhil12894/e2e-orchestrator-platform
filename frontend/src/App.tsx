import React, { useState, useEffect, useCallback } from 'react';
import {
  Node,
  Edge,
  applyNodeChanges,
  applyEdgeChanges,
  OnNodesChange,
  OnEdgesChange,
  Connection,
  addEdge,
} from '@xyflow/react';

import { Navbar } from './components/navbar/Navbar';
import { ApiCatalogSidebar } from './components/catalog/ApiCatalogSidebar';
import { FlowCanvas } from './components/canvas/FlowCanvas';
import { NodeConfigDrawer } from './components/drawer/NodeConfigDrawer';
import { LiveExecutionConsole } from './components/console/LiveExecutionConsole';
import { ReportViewerModal } from './components/console/ReportViewerModal';
import { workflowApi, executionApi } from './api/client';
import {
  TestWorkflow,
  WorkflowExecution,
  ExecutionEventDto,
  CanvasNode,
  CanvasEdge,
  CanvasNodeData,
} from './types/workflow';

export function App() {
  const [workflows, setWorkflows] = useState<TestWorkflow[]>([]);
  const [selectedWorkflow, setSelectedWorkflow] = useState<TestWorkflow | null>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);

  const [selectedNode, setSelectedNode] = useState<CanvasNode | null>(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isConsoleOpen, setIsConsoleOpen] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  // Execution state
  const [environment, setEnvironment] = useState('default');
  const [isRunning, setIsRunning] = useState(false);
  const [activeExecution, setActiveExecution] = useState<WorkflowExecution | null>(null);
  const [executionEvents, setExecutionEvents] = useState<ExecutionEventDto[]>([]);

  // Report Modal state
  const [reportModalUrl, setReportModalUrl] = useState<string | null>(null);

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

    // Format edges for React Flow
    const flowEdges: Edge[] = (canvas.edges || []).map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      animated: e.animated ?? true,
      style: { stroke: '#475569', strokeWidth: 2 },
    }));

    setNodes(flowNodes);
    setEdges(flowEdges);
    setSelectedNode(null);
    setIsDrawerOpen(false);
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

  const onConnect = useCallback(
    (connection: Connection) =>
      setEdges((eds) =>
        addEdge(
          {
            ...connection,
            animated: true,
            style: { stroke: '#475569', strokeWidth: 2 },
          },
          eds
        )
      ),
    []
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
        animated: true,
        style: { stroke: '#475569', strokeWidth: 2 },
      };
      setEdges((prev) => [...prev, newEdge]);
    }
  };

  // Save current workflow
  const handleSaveWorkflow = async () => {
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

    try {
      const updated = await workflowApi.updateWorkflow(selectedWorkflow.id, {
        name: selectedWorkflow.name,
        description: selectedWorkflow.description,
        canvasState: {
          nodes: canvasNodes,
          edges: canvasEdges,
          viewport: { x: 0, y: 0, zoom: 1 },
        },
      });

      setSelectedWorkflow(updated);
      setWorkflows((prev) => prev.map((w) => (w.id === updated.id ? updated : w)));
    } catch (err) {
      console.error('Failed to save workflow', err);
    }
  };

  // Create new workflow
  const handleCreateNewWorkflow = async () => {
    try {
      const created = await workflowApi.createWorkflow({
        name: `New Test Flow ${workflows.length + 1}`,
        description: 'Visual E2E workflow built with React Flow and Karate execution engine',
        canvasState: {
          nodes: [],
          edges: [],
          viewport: { x: 0, y: 0, zoom: 1 },
        },
      });

      setWorkflows((prev) => [created, ...prev]);
      selectWorkflow(created);
    } catch (err) {
      console.error('Failed to create workflow', err);
    }
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

      // Subscribe to real-time SSE event stream
      const unsubscribe = executionApi.subscribeToExecutionEvents(
        execution.id,
        (event) => {
          setExecutionEvents((prev) => [...prev, event]);

          // Update node status on canvas in real time!
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
            setIsRunning(false);
            // Refresh execution object to obtain stepResults and reportPath
            executionApi.getExecutionById(execution.id).then((fresh) => {
              setActiveExecution(fresh);
            });
            unsubscribe();
          }
        },
        (err) => {
          console.error('SSE Error', err);
          setIsRunning(false);
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
    <div className="w-screen h-screen flex flex-col bg-slate-950 overflow-hidden text-slate-100 font-sans">
      {/* Top Navbar */}
      <Navbar
        workflows={workflows}
        selectedWorkflow={selectedWorkflow}
        onSelectWorkflow={selectWorkflow}
        onCreateNewWorkflow={handleCreateNewWorkflow}
        onSaveWorkflow={handleSaveWorkflow}
        onRunWorkflow={handleRunWorkflow}
        isRunning={isRunning}
        environment={environment}
        onEnvironmentChange={setEnvironment}
        latestExecution={activeExecution}
        onViewReport={(url) => setReportModalUrl(url)}
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
            onConnect={onConnect}
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
        />
      </div>

      {/* Bottom Live Execution Console */}
      <LiveExecutionConsole
        execution={activeExecution}
        events={executionEvents}
        isOpen={isConsoleOpen}
        onToggle={() => setIsConsoleOpen(!isConsoleOpen)}
        onViewReport={(url) => setReportModalUrl(url)}
      />

      {/* Embedded Karate HTML Report Modal */}
      <ReportViewerModal
        isOpen={!!reportModalUrl}
        onClose={() => setReportModalUrl(null)}
        reportUrl={reportModalUrl}
        workflowName={selectedWorkflow?.name || 'Workflow Run'}
      />
    </div>
  );
}

export default App;
