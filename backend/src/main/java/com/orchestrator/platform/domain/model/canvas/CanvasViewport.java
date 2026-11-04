package com.orchestrator.platform.domain.model.canvas;

import java.io.Serializable;

public class CanvasViewport implements Serializable {
    private double x = 0;
    private double y = 0;
    private double zoom = 1.0;

    public CanvasViewport() {}

    public CanvasViewport(double x, double y, double zoom) {
        this.x = x;
        this.y = y;
        this.zoom = zoom;
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZoom() { return zoom; }
    public void setZoom(double zoom) { this.zoom = zoom; }
}
