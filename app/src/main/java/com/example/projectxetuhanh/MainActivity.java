package com.example.projectxetuhanh;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Size;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import android.graphics.Rect;


public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private FaceOverlayView overlayView;
    private ProcessCameraProvider cameraProvider;
    private CascadeClassifier faceCascade;
    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();
    private static final float CONFIDENCE_THRESHOLD = 0.8f;

    // Model input parameters
    private int inputWidth;
    private int inputHeight;
    private int inputChannels;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
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
        overlayView = findViewById(R.id.overlay);

        // Khởi tạo OpenCV
        OpenCVLoader.initLocal();
        initializeApp();
    }

    private void initializeApp() {
        loadCascade();
        loadModel();
        startCamera();
    }

    @SuppressLint("NewApi")
    private void loadModel() {
        try {
            // Load TFLite model
            tflite = new Interpreter(loadModelFile("face_model.tflite"));

            // Lấy thông số đầu vào
            Tensor inputTensor = tflite.getInputTensor(0);
            int[] inputShape = inputTensor.shape();
            inputWidth = inputShape[1];
            inputHeight = inputShape[2];
            inputChannels = inputShape[3];

            try {
                InputStream is = getAssets().open("labels.txt");
                byte[] bytes = is.readAllBytes();
                String content = new String(bytes, StandardCharsets.UTF_8);
                // split cả CRLF (\r\n) lẫn LF (\n)
                labels = Arrays.asList(content.split("\\r?\\n"));
                is.close();
            } catch (IOException e) {
                Log.e("FaceRecognition", "Error reading labels.txt", e);
            }
        } catch (Exception e) {
            Log.e("FaceRecognition", "Error loading model", e);
        }
    }

    private void loadCascade() {
        try {
            InputStream is = getAssets().open("haarcascade_frontalface_default.xml");
            File cascadeFile = new File(getCacheDir(), "temp.xml");

            try (FileOutputStream os = new FileOutputStream(cascadeFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            faceCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (faceCascade.empty()) {
                Log.e("FaceRecognition", "Failed to load cascade classifier");
            }

        } catch (Exception e) {
            Log.e("FaceRecognition", "Error loading cascade", e);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                // Tạo và cấu hình Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Tạo ImageAnalysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, this::analyzeImage);

                // Chọn camera sau (back camera)
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Bind các use cases vào vòng đời
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Error binding camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        try {
            // Chuyển đổi ImageProxy sang RGB Mat
            Mat rgbMat = yuv420ToRgbMat(Objects.requireNonNull(imageProxy.getImage()));

            // Xử lý xoay ảnh
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation == 90 || rotation == 270) {
                Core.rotate(rgbMat, rgbMat, Core.ROTATE_90_CLOCKWISE);
            }

            // Phát hiện khuôn mặt
            Mat grayMat = new Mat();
            Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);

            MatOfRect faces = new MatOfRect();
            faceCascade.detectMultiScale(
                    grayMat, faces,
                    1.1, 4, 0,
                    new Size(100, 100),
                    new Size(1000, 1000)
            );

            // Xử lý từng khuôn mặt
            List<FaceResult> results = new ArrayList<>();
            for (org.opencv.core.Rect rect : faces.toArray()) {
                Mat faceMat = preprocessFace(rgbMat.submat(rect));
                ByteBuffer buffer = convertMatToBuffer(faceMat);

                // Inference
                byte[][] output = new byte[1][labels.size()];
                tflite.run(buffer, output);

                // Xử lý kết quả
                int classId = getMaxIndex(output[0]);
                float confidence = output[0][classId];
                String label = (confidence > CONFIDENCE_THRESHOLD) ?
                        labels.get(classId) : "Unknown";

                results.add(new FaceResult(
                        new Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height),
                        label,
                        confidence
                ));
            }

            overlayView.setFaces(results, rgbMat.cols(), rgbMat.rows());

        } catch (Exception e) {
            Log.e("FaceRecognition", "Analysis error", e);
        } finally {
            imageProxy.close();
        }
    }

    private Mat yuv420ToRgbMat(Image image) {
        // Lấy thông số ảnh
        int width = image.getWidth();
        int height = image.getHeight();

        // Lấy các planes YUV
        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        // Chuẩn bị buffer và mảng byte
        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        // Tạo mảng byte cho YUV data (NV21 format)
        byte[] yuvBytes = new byte[yBuffer.remaining() + vBuffer.remaining() + uBuffer.remaining()];

        // Điền dữ liệu Y
        yBuffer.get(yuvBytes, 0, yBuffer.remaining());

        // Điền dữ liệu V và U (NV21: Y + VU)
        vBuffer.get(yuvBytes, yBuffer.remaining(), vBuffer.remaining());
        uBuffer.get(yuvBytes, yBuffer.remaining() + vBuffer.remaining(), uBuffer.remaining());

        // Tạo Mat YUV
        Mat yuvMat = new Mat(height + height/2, width, CvType.CV_8UC1);
        yuvMat.put(0, 0, yuvBytes); // Copy toàn bộ dữ liệu vào Mat

        // Chuyển YUV → RGB
        Mat rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);

        return rgbMat;
    }

    private Mat preprocessFace(Mat faceRoi) {
        Mat resized = new Mat();
        Imgproc.resize(faceRoi, resized, new Size(inputWidth, inputHeight));

        // Đảm bảo định dạng uint8
        if (resized.type() != CvType.CV_8UC3) {
            resized.convertTo(resized, CvType.CV_8UC3);
        }
        return resized;
    }

    // Sửa hàm convertMatToBuffer
    private ByteBuffer convertMatToBuffer(Mat mat) {
        // Đảm bảo Mat là UINT8
        if (mat.type() != CvType.CV_8UC3) {
            mat.convertTo(mat, CvType.CV_8UC3);
        }

        // Tạo buffer với kiểu UINT8
        ByteBuffer buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3);
        buffer.order(ByteOrder.nativeOrder());

        // Điền dữ liệu byte (0-255)
        byte[] pixelData = new byte[(int) mat.total() * mat.channels()];
        mat.get(0, 0, pixelData);
        buffer.put(pixelData);
        buffer.rewind();

        return buffer;
    }

    private int getMaxIndex(byte[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) maxIndex = i;
        }
        return maxIndex;
    }

    private ByteBuffer loadModelFile(String modelName) throws IOException {
        try (InputStream is = getAssets().open(modelName)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(is.available());
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            buffer.put(bytes);
            buffer.rewind();
            return buffer;
        }
    }

//    @OptIn(markerClass = ExperimentalGetImage.class)
//    private Mat imageProxyToMat(ImageProxy imageProxy) {
//        Image image = imageProxy.getImage();
//        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//        byte[] data = new byte[buffer.remaining()];
//        buffer.get(data);
//
//        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3); // Định dạng phụ thuộc vào camera
//        mat.put(0, 0, data);
//        return mat;
//    }

//    private void processImage(ImageProxy imageProxy) {
//        Mat mat = imageProxyToMat(imageProxy);
//        Bitmap bitmap = Bitmap.createBitmap(
//                imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
//
////        Utils.bitmapToMat(bitmap, mat);
//        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
//
//        // Detect faces
//        MatOfRect faces = new MatOfRect();
//        faceCascade.detectMultiScale(mat, faces, 1.1, 4);
//        List<FaceResult> faceResults = new ArrayList<>();
//
//        for (org.opencv.core.Rect rect : faces.toArray()) {
//            Rect androidRect = new Rect(
//                    rect.x,                 // left
//                    rect.y,                 // top
//                    rect.x + rect.width,    // right
//                    rect.y + rect.height   // bottom
//            );
//            // Preprocess và inference
//            Mat faceMat = new Mat(mat, rect);
//            faceMat = preprocessFace(faceMat);
//            float[][] output = new float[1][labels.size()];
//            tflite.run(convertMatToBuffer(faceMat), output);
//
//            int classId = getMaxClass(output[0]);
//            float confidence = output[0][classId];
//            String label = confidence > CONFIDENCE_THRESHOLD ?
//                    labels.get(classId) : "Unknown";
//
//            faceResults.add(new FaceResult(androidRect, label, confidence));
//
//        }
//        faceResults.add(new FaceResult(
//                new Rect(200, 150, 400, 350), // left, top, right, bottom
//                "Elon Musk",
//                0.85f
//        ));
//        overlayView.setFaces(faceResults, imageProxy.getWidth(), imageProxy.getHeight());
//        imageProxy.close();
//    }




//    private Mat preprocessFace(Mat faceMat) {
//        Mat resized = new Mat();
//        Imgproc.resize(faceMat, resized, new Size(100, 100)); // Thay đổi kích thước theo model
//        return resized;
//    }
//
//    private ByteBuffer convertMatToBuffer(Mat mat) {
//        int height = mat.rows(), width = mat.cols(), channels = 1;
//        int byteSize = 1 * height * width * channels; // 1 batch
//        ByteBuffer buffer = ByteBuffer.allocateDirect(byteSize);
//        buffer.order(ByteOrder.nativeOrder());
//
//        for (int i = 0; i < height; i++) {
//            for (int j = 0; j < width; j++) {
//                int pixel = (int) mat.get(i, j)[0]; // 0–255
//                buffer.put((byte) pixel);
//            }
//        }
//
//        buffer.rewind();
//        return buffer;
//    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        executor.shutdown();
    }

    private int getMaxClass(float[] output) {
        int maxIndex = 0;
        for (int i = 1; i < output.length; i++) {
            if (output[i] > output[maxIndex]) maxIndex = i;
        }
        return maxIndex;
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

}