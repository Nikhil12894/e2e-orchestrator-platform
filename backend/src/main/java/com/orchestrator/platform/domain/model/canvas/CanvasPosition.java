package com.orchestrator.platform.domain.model.canvas;

import java.io.Serializable;

public class CanvasPosition implements Serializable {
    private double x;
    private double y;

    public CanvasPosition() {}

    public CanvasPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
}
