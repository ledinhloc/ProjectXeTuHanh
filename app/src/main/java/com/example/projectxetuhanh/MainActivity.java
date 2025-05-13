package com.example.projectxetuhanh;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Size;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.felhr.usbserial.SerialOutputStream;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.GpuDelegate;

import android.graphics.Rect;


public class MainActivity extends AppCompatActivity implements ArduinoUsbController.ConnectionCallback {
    private PreviewView previewView;
    private FaceOverlayView overlayView;
    private CascadeClassifier faceCascade;
    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();
    private static final float CONFIDENCE_THRESHOLD = 0.8f;

    // Model input parameters
    private int inputWidth;
    private int inputHeight;
    private int inputChannels;

    private Mat yuvMat, rgbMat, grayMat, faceMat;
    private ByteBuffer imgBuffer;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ProcessCameraProvider cameraProvider;
    ImageButton btnSwitch, btnCar, btnStop ;
    ImageButton btnDown, btnUp, btnRight, btnLeft;
    //bo qua khung hinh
    private AtomicInteger frameCount = new AtomicInteger(0);
    private static final int FRAME_SKIP_RATE = 4;

    private ArduinoUsbController arduinoController;

    private static final String TAG = "MainActivity";
    private int currentMode = 1; // Thêm biến theo dõi mode hiện tại

    private LogAdapter logAdapter;
    private RecyclerView logRecyclerView;
    Spinner spinnerMode;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
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
        hideSystemUI();

        initView();
        // Khởi tạo OpenCV
        OpenCVLoader.initLocal();
        initializeApp();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void initView() {
        //preview
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlay);
        btnSwitch = findViewById(R.id.btnSwitchCamera);
        btnCar = findViewById(R.id.btnCar);
        spinnerMode = findViewById(R.id.spinnerMode);
        btnStop = findViewById(R.id.btnStop);

        btnUp = findViewById(R.id.btnUp);
        btnDown = findViewById(R.id.btnDown);
        btnRight = findViewById(R.id.btnRight);
        btnLeft = findViewById(R.id.btnLeft);

        btnSwitch.setOnClickListener(v -> {
            // Đổi selector
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            }
            // Khởi động lại camera với selector mới
            startCamera();
        });

        //btnCar
        btnCar.setOnClickListener(v->{
            if (arduinoController.isConnected()){
                //huy connect
                if (!arduinoController.disconnect()) {
                    addLogEntry("huy ket noi that bai");
                    return;
                };
                btnCar.setImageResource(R.drawable.car);
                btnCar.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            }else {
                //connect
                if (!arduinoController.connect()) {
                    addLogEntry("ket noi that bai");
                    return;
                };
                btnCar.setImageResource(R.drawable.carstart);
                btnCar.setImageTintList(null);
            }
        });

        // Khởi tạo log console
        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter();
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);

        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedMode = parent.getItemAtPosition(position).toString();
                Toast.makeText(MainActivity.this, "Chọn: " + selectedMode, Toast.LENGTH_SHORT).show();
                // Cập nhật mode hiện tại
                if(position == 0){
                    sendCommand("1");
                    currentMode = 1; 
                }
                else if(position == 1){
                    sendCommand("2");
                    currentMode = 2; 
                }
                else {
                    sendCommand("0");
                    currentMode = 0;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendCommand("0");
                currentMode = 0;
                spinnerMode.setSelection(2);// chuyen sang item Dung
                Toast.makeText(MainActivity.this, "Đã chọn Stop", Toast.LENGTH_SHORT).show();
            }
        });

        btnUp.setOnClickListener(view -> {sendCommand("F");});
        btnDown.setOnClickListener(view -> {sendCommand("B");});
        btnLeft.setOnClickListener(view -> {sendCommand("R");});
        btnRight.setOnClickListener(view -> {sendCommand("L");});
    }

    private void addLogEntry(String message) {
        runOnUiThread(() -> {
            logAdapter.addLog(message);
            logRecyclerView.smoothScrollToPosition(logAdapter.getItemCount() - 1);
        });
    }

    private void initializeApp() {
        loadCascade();
        loadModel();
        startCamera();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arduinoController = new ArduinoUsbController(this, this);
        }
    }

    @SuppressLint("NewApi")
    private void loadModel() {
        try {
            // Load TFLite model
            tflite = new Interpreter(loadModelFile("face_model.tflite"));
            Tensor inputTensor = tflite.getInputTensor(0);
            int[] inputShape = inputTensor.shape();
            inputWidth = inputShape[1];
            inputHeight = inputShape[2];
            inputChannels = inputShape[3];

            //load labels
            try {
                InputStream is = getAssets().open("labels.txt");
                byte[] bytes = is.readAllBytes();
                String content = new String(bytes, StandardCharsets.UTF_8);
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
                cameraProvider = future.get();
                // Trước khi bind lại, unbind hết các use-cases cũ
                cameraProvider.unbindAll();

                // Tạo và cấu hình Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Tạo ImageAnalysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, this::analyzeImage);

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
            // Chỉ xử lý ảnh khi mode là 0 (Chạy theo người)
            if (currentMode != 1) {
                imageProxy.close();
                return;
            }

            // 1. Skip frame
            if (frameCount.getAndIncrement() % FRAME_SKIP_RATE != 0) {
                imageProxy.close();
                return;
            }

            // 2. Chuyển ImageProxy -> RGB Mat
            rgbMat = yuv420ToRgbMat(Objects.requireNonNull(imageProxy.getImage()));

            // Mirror ảnh nếu là camera trước
            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                Core.flip(rgbMat, rgbMat, 1);
            }

            // 3. Xử lý xoay ảnh và tính toán tọa độ
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            boolean isLandscape = (rotation % 180 == 90);
            int originalWidth = rgbMat.cols();
            int originalHeight = rgbMat.rows();

            // Xoay ảnh gốc
            switch (rotation) {
                case 90:
                    Core.rotate(rgbMat, rgbMat, Core.ROTATE_90_CLOCKWISE);
                    break;
                case 180:
                    Core.rotate(rgbMat, rgbMat, Core.ROTATE_180);
                    break;
                case 270:
                    Core.rotate(rgbMat, rgbMat, Core.ROTATE_90_COUNTERCLOCKWISE);
                    break;
            }

            // 4. Lấy kích thước sau xoay
            int rotatedWidth = rgbMat.cols();
            int rotatedHeight = rgbMat.rows();

            // 5. Phát hiện khuôn mặt
            grayMat = new Mat();
            Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);
            MatOfRect faces = new MatOfRect();
            faceCascade.detectMultiScale(grayMat, faces, 1.1, 3, 0, new Size(100, 100), new Size(400, 400));

            // 6. Xử lý từng khuôn mặt
            List<FaceResult> results = new ArrayList<>();
            for (org.opencv.core.Rect rect : faces.toArray()) {
                // Điều chỉnh tọa độ và kích thước theo hướng xoay
                int adjustedX = rect.x;
                int adjustedY = rect.y;
                int adjustedWidth = rect.width;
                int adjustedHeight = rect.height;

                // Hoán đổi width/height nếu ở chế độ ngang
                if (isLandscape) {
                    adjustedWidth = rect.height;
                    adjustedHeight = rect.width;
                    adjustedY = rotatedHeight - rect.y - rect.height; // Điều chỉnh tọa độ Y
                }

                // Tạo Rect đã điều chỉnh
                org.opencv.core.Rect adjustedRect = new org.opencv.core.Rect(
                        adjustedX,
                        adjustedY,
                        adjustedWidth,
                        adjustedHeight
                );

                // ... Phần inference và xử lý nhãn
                faceMat = preprocessFace(rgbMat.submat(adjustedRect));
                ByteBuffer buffer = convertMatToBuffer(faceMat);

                // Inference
                byte[][] output = new byte[1][labels.size()];
                tflite.run(buffer, output);

                // Xử lý kết quả
                int classId = getMaxIndex(output[0]);
                float confidence = output[0][classId];
                String label = (confidence > CONFIDENCE_THRESHOLD) ?
                        labels.get(classId) : "Unknown";

                // Xử lý hướng "Loc"
                if (label.equals("Loc")) {
                    int faceCenterX = adjustedX + adjustedWidth / 2;
                    int imageCenterX = rotatedWidth / 2;
                    int deviation = faceCenterX - imageCenterX;

                    String direction = "F";
                    if (deviation != 0) {
                        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                            direction = deviation < 0 ? "R" : "L";
                        } else {
                            direction = deviation < 0 ? "R" : "L";
                        }
                    }
                    sendCommand(direction);
                }

                results.add(new FaceResult(
                        new Rect(adjustedX, adjustedY, adjustedX + adjustedWidth, adjustedY + adjustedHeight),
                        label,
                        confidence
                ));
            }

            // 7. Cập nhật overlay
            overlayView.setFaces(results, rotatedWidth, rotatedHeight);
        } catch (Exception e) {
            Log.e("FaceRecognition", "Error", e);
        } finally {
            imageProxy.close();
        }
    }

    private void sendCommand(String direction) {
        if (arduinoController.isConnected()) {
            arduinoController.sendControlCommand(direction);
            addLogEntry("Đã gửi lệnh: " + direction);
        } else {
            addLogEntry("Chưa kết nối thiết bị. Lệnh: "+ direction);
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
        yuvMat = new Mat(height + height/2, width, CvType.CV_8UC1);
        yuvMat.put(0, 0, yuvBytes); // Copy toàn bộ dữ liệu vào Mat

        // Chuyển YUV → RGB
        rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);
        image.close();
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
        imgBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3);
        imgBuffer.order(ByteOrder.nativeOrder());

        // Điền dữ liệu byte (0-255)
        byte[] pixelData = new byte[(int) mat.total() * mat.channels()];
        mat.get(0, 0, pixelData);
        imgBuffer.put(pixelData);
        imgBuffer.rewind();

        return imgBuffer;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        arduinoController.release();
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

    @Override
    public void onConnectionStatusChange(String status) {
        addLogEntry("status:" + status);
    }

    @Override
    public void onDataReceived(String data) {
        addLogEntry("data: " + data);
    }

    @Override
    public void onConnectionError(String error) {
        addLogEntry("con err" + error);
    }
}