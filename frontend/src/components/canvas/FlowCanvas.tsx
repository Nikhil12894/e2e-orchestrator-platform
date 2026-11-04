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
import { CanvasNode, CanvasEdge } from '../../types/workflow';

const nodeTypes: any = {
  httpRequest: HttpRequestNode,
  dbPoll: DbPollNode,
  kafkaAssert: KafkaAssertNode,
  delay: DelayNode,
};

interface FlowCanvasInnerProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: OnNodesChange<Node>;
  onEdgesChange: OnEdgesChange<Edge>;
  onConnect: (connection: Connection) => void;
  onNodeClick: (event: React.MouseEvent, node: Node) => void;
  onDropNewNode: (type: string, position: { x: number; y: number }, initialData: Record<string, any>) => void;
}

const FlowCanvasInner: React.FC<FlowCanvasInnerProps> = ({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onNodeClick,
  onDropNewNode,
}) => {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();

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
        onNodeClick={onNodeClick}
        nodeTypes={nodeTypes}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        className="bg-slate-950"
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#1e293b" />
        <Controls className="!bg-slate-900 !border-slate-800 !shadow-xl [&>button]:!border-slate-800 [&>button]:!text-slate-300 [&>button:hover]:!bg-slate-800" />
        <MiniMap
          nodeColor={(n) => {
            if (n.type === 'httpRequest') return '#3b82f6';
            if (n.type === 'dbPoll') return '#f59e0b';
            if (n.type === 'kafkaAssert') return '#a855f7';
            if (n.type === 'delay') return '#06b6d4';
            return '#64748b';
          }}
          maskColor="rgba(11, 15, 25, 0.8)"
          className="!bg-slate-900 !border-slate-800 !rounded-xl !overflow-hidden !shadow-2xl"
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
  onConnect: (connection: Connection) => void;
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
