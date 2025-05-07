package com.example.projectxetuhanh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

//View vẽ khung bao quanh nhan
public class FaceOverlayView extends View {
    //danh sach doi tuong
    private List<FaceResult> faces = new ArrayList<>();
    private int imageWidth, imageHeight;
    private Paint paint = new Paint();
    private Paint textPaint = new Paint();

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    //khoi tao
    private void init() {
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);

        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(40f);
    }

    public void setFaces(List<FaceResult> faces, int imageWidth, int imageHeight) {
        this.faces = faces;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        postInvalidate();
    }

    //onDraw được gọi khi View cần vẽ lại.
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (imageWidth == 0 || imageHeight == 0) return;

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (FaceResult face : faces) {
            Rect rect = face.rect;
            float left = rect.left * scaleX;
            float top = rect.top * scaleY;
            float right = rect.right * scaleX;
            float bottom = rect.bottom * scaleY;

            //Vẽ khung chữ nhật quanh khuôn mặt bằng paint
            canvas.drawRect(left, top, right, bottom, paint);
            canvas.drawText(face.label + " (" + String.format("%.2f", face.confidence) + ")",
                    left, top - 10, textPaint);
        }
    }
}
//
class FaceResult {
    // vùng khuôn mặt
    Rect rect;
    //ten label
    String label;
    //mức độ chắc chắn
    float confidence;

    public FaceResult(Rect rect, String label, float confidence) {
        this.rect = rect;
        this.label = label;
        this.confidence = confidence;
    }
}