package com.example.projectxetuhanh;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.FaceDetector;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import org.opencv.core.Size;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.felhr.usbserial.UsbSerialDevice;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import android.Manifest;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import android.graphics.Rect;


public class MainActivity extends AppCompatActivity {
    private UsbSerialDevice usbDevice;
    private FaceClassifier faceClassifier;
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;

    private FaceOverlayView overlayView;
    private CascadeClassifier faceCascade;
    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();
    private static final float CONFIDENCE_THRESHOLD = 0.7f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        //preview
        previewView = findViewById(R.id.previewView);

        // Kiểm tra quyền camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    100
            );
        }

        // Khởi động camera
       // startCamera();

        OpenCVLoader.initDebug();
        loadCascade();
        loadModel();
      //  setupCamera();
    }

    private void loadModel() {
        try {
            // Load TFLite model
            tflite = new Interpreter(loadModelFile("face_model.tflite"));
            // Load labels
            InputStream is = getAssets().open("labels.txt");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String[] labelArray = new String(buffer).split("\n");
            for (String label : labelArray) labels.add(label.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCascade() {
        try {
            InputStream is = getAssets().open("haarcascade_frontalface_default.xml");
            File cascadeFile = new File(getCacheDir(), "temp.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            faceCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ByteBuffer loadModelFile(String modelName) throws Exception {
        try (InputStream is = getAssets().open(modelName)) {
            byte[] modelData = new byte[is.available()];
            is.read(modelData);
            return ByteBuffer.allocateDirect(modelData.length).put(modelData);
        }
    }

    //xu ly sau khi duoc cap quyen
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Cần cấp quyền camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Khởi tạo camera provider
                cameraProvider = cameraProviderFuture.get();

                // Tạo Preview use case
                Preview preview = new Preview.Builder().build();

                // Liên kết với PreviewView
                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                // Chọn camera sau
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    imageAnalysis.setAnalyzer(getMainExecutor(), this::processImage);
                }
                // Bind các use case
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Lỗi khởi tạo camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processImage(ImageProxy imageProxy) {
        Mat mat = new Mat();
        Bitmap bitmap = Bitmap.createBitmap(
                imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.bitmapToMat(bitmap, mat);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

        // Detect faces
        MatOfRect faces = new MatOfRect();
        faceCascade.detectMultiScale(mat, faces, 1.1, 4);

        List<FaceResult> faceResults = new ArrayList<>();
        for (org.opencv.core.Rect rect : faces.toArray()) {
            Rect androidRect = new Rect(
                    rect.x,                 // left
                    rect.y,                 // top
                    rect.x + rect.width,    // right
                    rect.y + rect.height   // bottom
            );
            // Preprocess và inference
            Mat faceMat = new Mat(mat, rect);
            faceMat = preprocessFace(faceMat);
            float[][] output = new float[1][labels.size()];
            tflite.run(convertMatToBuffer(faceMat), output);

            int classId = getMaxClass(output[0]);
            float confidence = output[0][classId];
            String label = confidence > CONFIDENCE_THRESHOLD ?
                    labels.get(classId) : "Unknown";

            faceResults.add(new FaceResult(androidRect, label, confidence));
        }

        overlayView.setFaces(faceResults, imageProxy.getWidth(), imageProxy.getHeight());
        imageProxy.close();
    }

    private Mat preprocessFace(Mat faceMat) {
        Mat resized = new Mat();
        Imgproc.resize(faceMat, resized, new Size(100, 100)); // Thay đổi kích thước theo model
        return resized;
    }

    private ByteBuffer convertMatToBuffer(Mat mat) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(mat.rows() * mat.cols() * 1); // Grayscale
        buffer.order(ByteOrder.nativeOrder());
        for (int i = 0; i < mat.rows(); i++) {
            for (int j = 0; j < mat.cols(); j++) {
                buffer.put((byte) mat.get(i, j)[0]);
            }
        }
        return buffer;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private int getMaxClass(float[] output) {
        int maxIndex = 0;
        for (int i = 1; i < output.length; i++) {
            if (output[i] > output[maxIndex]) maxIndex = i;
        }
        return maxIndex;
    }

}