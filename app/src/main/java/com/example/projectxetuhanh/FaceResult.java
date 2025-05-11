package com.example.projectxetuhanh;

import android.graphics.Rect;

public class FaceResult {
    public final Rect rect;
    public final String label;
    public final float confidence;

    public FaceResult(Rect rect, String label, float confidence) {
        this.rect = rect;
        this.label = label;
        this.confidence = confidence;
    }
} 