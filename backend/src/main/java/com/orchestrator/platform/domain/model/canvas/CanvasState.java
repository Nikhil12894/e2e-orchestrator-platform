package com.orchestrator.platform.domain.model.canvas;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CanvasState implements Serializable {
    private List<CanvasNode> nodes = new ArrayList<>();
    private List<CanvasEdge> edges = new ArrayList<>();
    private CanvasViewport viewport = new CanvasViewport();

    public CanvasState() {}

    public List<CanvasNode> getNodes() { return nodes; }
    public void setNodes(List<CanvasNode> nodes) { this.nodes = nodes; }

    public List<CanvasEdge> getEdges() { return edges; }
    public void setEdges(List<CanvasEdge> edges) { this.edges = edges; }

    public CanvasViewport getViewport() { return viewport; }
    public void setViewport(CanvasViewport viewport) { this.viewport = viewport; }
}
