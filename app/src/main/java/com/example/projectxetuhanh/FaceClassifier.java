package com.example.projectxetuhanh;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import org.tensorflow.lite.support.image.TensorImage;


public class FaceClassifier {
    private Interpreter tflite;
    private static final float THRESHOLD = 0.7f; // Ngưỡng xác suất

    public FaceClassifier(MappedByteBuffer model) {
        tflite = new Interpreter(model);
    }

    // Kiểm tra xem ảnh có phải là khuôn mặt của bạn không
    public boolean isMyFace(Bitmap faceImage) {
        // Tiền xử lý ảnh (resize + normalize)
        Bitmap resized = Bitmap.createScaledBitmap(faceImage, 112, 112, true);
        TensorImage input = TensorImage.fromBitmap(resized);

        // Chạy inference
        float[][] output = new float[1][1]; // Output shape [1,1] (xác suất)
        tflite.run(input.getBuffer(), output);

        // Kiểm tra ngưỡng
        return output[0][0] > THRESHOLD;
    }
}