package com.orchestrator.platform.domain.model.canvas;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CanvasNode implements Serializable {
    private String id;
    private String type; // httpRequest, dbPoll, kafkaAssert, delay
    private CanvasPosition position;
    private Map<String, Object> data = new HashMap<>();
    private Boolean selected;

    public CanvasNode() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public CanvasPosition getPosition() { return position; }
    public void setPosition(CanvasPosition position) { this.position = position; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }
}
