package com.example.projectxetuhanh;

import android.graphics.Rect;

public class DetectionResult {
    private Rect rect;
    private String label;
    private float confidence;

    public DetectionResult(Rect rect, String label, float confidence) {
        this.rect = rect;
        this.label = label;
        this.confidence = confidence;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Rect getRect() {
        return rect;
    }

    public void setRect(Rect rect) {
        this.rect = rect;
    }
}