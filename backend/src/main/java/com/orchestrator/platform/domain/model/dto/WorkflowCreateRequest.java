package com.orchestrator.platform.domain.model.dto;

import com.orchestrator.platform.domain.model.ast.WorkflowAst;
import com.orchestrator.platform.domain.model.canvas.CanvasState;
import java.io.Serializable;

public class WorkflowCreateRequest implements Serializable {
    private String name;
    private String description;
    private CanvasState canvasState;
    private WorkflowAst workflowAst;

    public WorkflowCreateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public CanvasState getCanvasState() { return canvasState; }
    public void setCanvasState(CanvasState canvasState) { this.canvasState = canvasState; }

    public WorkflowAst getWorkflowAst() { return workflowAst; }
    public void setWorkflowAst(WorkflowAst workflowAst) { this.workflowAst = workflowAst; }
}
