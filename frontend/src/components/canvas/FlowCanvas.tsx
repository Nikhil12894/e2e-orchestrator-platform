import React, { useCallback, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  Connection,
  Edge,
  Node,
  OnNodesChange,
  OnEdgesChange,
  useReactFlow,
  ReactFlowProvider,
  BackgroundVariant,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { HttpRequestNode } from './nodes/HttpRequestNode';
import { DbPollNode } from './nodes/DbPollNode';
import { KafkaAssertNode } from './nodes/KafkaAssertNode';
import { DelayNode } from './nodes/DelayNode';
import { TransformNode } from './nodes/TransformNode';
import { SubWorkflowNode } from './nodes/SubWorkflowNode';
import { RemovableEdge } from './edges/RemovableEdge';
import { CanvasNode, CanvasEdge } from '../../types/workflow';
import { useTheme } from '../../context/ThemeContext';
import { getAutoLayoutedNodes } from '../../utils/layoutEngine';
import { Sparkles } from 'lucide-react';

const nodeTypes: any = {
  httpRequest: HttpRequestNode,
  dbPoll: DbPollNode,
  kafkaAssert: KafkaAssertNode,
  delay: DelayNode,
  transform: TransformNode,
  subWorkflow: SubWorkflowNode,
};

const edgeTypes: any = {
  default: RemovableEdge,
  removable: RemovableEdge,
};

interface FlowCanvasInnerProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: OnNodesChange<Node>;
  onEdgesChange: OnEdgesChange<Edge>;
  onSetNodes?: (nodes: Node[]) => void;
  onConnect: (connection: Connection) => void;
  onReconnect?: (oldEdge: Edge, newConnection: Connection) => void;
  onNodeClick: (event: React.MouseEvent, node: Node) => void;
  onDropNewNode: (type: string, position: { x: number; y: number }, initialData: Record<string, any>) => void;
}

const FlowCanvasInner: React.FC<FlowCanvasInnerProps> = ({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onSetNodes,
  onConnect,
  onReconnect,
  onNodeClick,
  onDropNewNode,
}) => {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition, fitView } = useReactFlow();
  const { resolvedTheme } = useTheme();

  const handleAutoLayout = useCallback(() => {
    if (!onSetNodes || nodes.length === 0) return;
    const layouted = getAutoLayoutedNodes(nodes, edges, { direction: 'LR' });
    onSetNodes(layouted);
    setTimeout(() => {
      fitView({ duration: 400 });
    }, 50);
  }, [nodes, edges, onSetNodes, fitView]);

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();

      const raw = event.dataTransfer.getData('application/reactflow');
      if (!raw) return;

      try {
        const payload = JSON.parse(raw);
        const position = screenToFlowPosition({
          x: event.clientX,
          y: event.clientY,
        });

        onDropNewNode(payload.type, position, payload.data || {});
      } catch (err) {
        console.error('Failed to parse dropped node payload', err);
      }
    },
    [screenToFlowPosition, onDropNewNode]
  );

  return (
    <div ref={reactFlowWrapper} className="w-full h-full relative" onDragOver={onDragOver} onDrop={onDrop}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onReconnect={onReconnect}
        edgesReconnectable={true}
        onNodeClick={onNodeClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        defaultEdgeOptions={{
          type: 'removable',
          animated: true,
        }}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        className="bg-slate-50 dark:bg-slate-950 transition-colors"
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} color={resolvedTheme === 'dark' ? '#1e293b' : '#cbd5e1'} />
        <Controls className="!bg-white dark:!bg-slate-900 !border-slate-200 dark:!border-slate-800 !shadow-lg [&>button]:!border-slate-200 dark:[&>button]:!border-slate-800 [&>button]:!text-slate-600 dark:[&>button]:!text-slate-300 [&>button:hover]:!bg-slate-100 dark:[&>button:hover]:!bg-slate-800" />
        
        {/* Floating Auto-Layout Toolbar */}
        {onSetNodes && nodes.length > 0 && (
          <div className="absolute top-4 right-4 z-10 flex items-center gap-2 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border border-slate-200 dark:border-slate-800 rounded-xl p-1 shadow-lg">
            <button
              onClick={handleAutoLayout}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-blue-600 dark:text-blue-400 hover:bg-blue-500/10 transition-colors"
              title="Automatically align and arrange canvas nodes hierarchically"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Auto Align</span>
            </button>
          </div>
        )}

        <MiniMap
          nodeColor={(n) => {
            if (n.type === 'httpRequest') return '#3b82f6';
            if (n.type === 'dbPoll') return '#f59e0b';
            if (n.type === 'kafkaAssert') return '#a855f7';
            if (n.type === 'delay') return '#06b6d4';
            if (n.type === 'transform') return '#8b5cf6';
            return '#64748b';
          }}
          maskColor={resolvedTheme === 'dark' ? 'rgba(11, 15, 25, 0.8)' : 'rgba(241, 245, 249, 0.85)'}
          className="!bg-white dark:!bg-slate-900 !border-slate-200 dark:!border-slate-800 !rounded-xl !overflow-hidden !shadow-lg"
        />
      </ReactFlow>
    </div>
  );
};

interface FlowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: OnNodesChange<Node>;
  onEdgesChange: OnEdgesChange<Edge>;
  onSetNodes?: (nodes: Node[]) => void;
  onConnect: (connection: Connection) => void;
  onReconnect?: (oldEdge: Edge, newConnection: Connection) => void;
  onNodeClick: (event: React.MouseEvent, node: Node) => void;
  onDropNewNode: (type: string, position: { x: number; y: number }, initialData: Record<string, any>) => void;
}

export const FlowCanvas: React.FC<FlowCanvasProps> = (props) => {
  return (
    <ReactFlowProvider>
      <FlowCanvasInner {...props} />
    </ReactFlowProvider>
  );
};
