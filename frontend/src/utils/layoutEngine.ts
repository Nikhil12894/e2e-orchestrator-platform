import { Node, Edge } from '@xyflow/react';

export interface LayoutOptions {
  direction?: 'LR' | 'TB';
  nodeWidth?: number;
  nodeHeight?: number;
  horizontalSpacing?: number;
  verticalSpacing?: number;
}

/**
 * Pure TypeScript topological hierarchical layout engine for React Flow.
 * Automatically aligns nodes into clean, non-overlapping columns and rows.
 */
export function getAutoLayoutedNodes(
  nodes: Node[],
  edges: Edge[],
  options: LayoutOptions = {}
): Node[] {
  if (nodes.length === 0) return nodes;

  const {
    direction = 'LR',
    horizontalSpacing = 340,
    verticalSpacing = 160,
  } = options;

  const nodeMap = new Map<string, Node>();
  const adj = new Map<string, string[]>();
  const inDegree = new Map<string, number>();

  nodes.forEach((n) => {
    nodeMap.set(n.id, n);
    adj.set(n.id, []);
    inDegree.set(n.id, 0);
  });

  edges.forEach((e) => {
    if (adj.has(e.source) && inDegree.has(e.target)) {
      adj.get(e.source)!.push(e.target);
      inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
    }
  });

  // Calculate hierarchical rank (depth) for each node using topological BFS
  const ranks = new Map<string, number>();
  const queue: string[] = [];

  // Roots have in-degree 0
  inDegree.forEach((deg, id) => {
    if (deg === 0) {
      queue.push(id);
      ranks.set(id, 0);
    }
  });

  // Fallback for cycles: if queue is empty but nodes exist
  if (queue.length === 0 && nodes.length > 0) {
    queue.push(nodes[0].id);
    ranks.set(nodes[0].id, 0);
  }

  const visited = new Set<string>();
  while (queue.length > 0) {
    const curr = queue.shift()!;
    visited.add(curr);
    const currRank = ranks.get(curr) || 0;

    const neighbors = adj.get(curr) || [];
    for (const neighbor of neighbors) {
      const existingRank = ranks.get(neighbor) || 0;
      ranks.set(neighbor, Math.max(existingRank, currRank + 1));

      if (!visited.has(neighbor) && !queue.includes(neighbor)) {
        queue.push(neighbor);
      }
    }
  }

  // Any nodes not reached in traversal get appended
  nodes.forEach((n) => {
    if (!ranks.has(n.id)) {
      ranks.set(n.id, 0);
    }
  });

  // Group nodes by their calculated rank
  const nodesByRank = new Map<number, string[]>();
  ranks.forEach((rank, id) => {
    if (!nodesByRank.has(rank)) {
      nodesByRank.set(rank, []);
    }
    nodesByRank.get(rank)!.push(id);
  });

  // Assign coordinate positions
  const updatedNodes: Node[] = [];
  const startX = 100;
  const startY = 100;

  nodes.forEach((node) => {
    const rank = ranks.get(node.id) || 0;
    const rankList = nodesByRank.get(rank) || [node.id];
    const indexInRank = rankList.indexOf(node.id);

    let x: number;
    let y: number;

    if (direction === 'LR') {
      x = startX + rank * horizontalSpacing;
      // Center column vertically
      const totalColHeight = rankList.length * verticalSpacing;
      y = startY + indexInRank * verticalSpacing - totalColHeight / 2 + 150;
    } else {
      y = startY + rank * verticalSpacing;
      const totalRowWidth = rankList.length * horizontalSpacing;
      x = startX + indexInRank * horizontalSpacing - totalRowWidth / 2 + 200;
    }

    updatedNodes.push({
      ...node,
      position: { x: Math.max(50, Math.round(x)), y: Math.max(50, Math.round(y)) },
    });
  });

  return updatedNodes;
}
