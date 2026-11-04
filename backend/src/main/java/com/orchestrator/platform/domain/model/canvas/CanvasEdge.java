package com.orchestrator.platform.domain.model.canvas;

import java.io.Serializable;

public class CanvasEdge implements Serializable {
    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;
    private String label;
    private Boolean animated = true;

    public CanvasEdge() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getSourceHandle() { return sourceHandle; }
    public void setSourceHandle(String sourceHandle) { this.sourceHandle = sourceHandle; }

    public String getTargetHandle() { return targetHandle; }
    public void setTargetHandle(String targetHandle) { this.targetHandle = targetHandle; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Boolean getAnimated() { return animated; }
    public void setAnimated(Boolean animated) { this.animated = animated; }
}
