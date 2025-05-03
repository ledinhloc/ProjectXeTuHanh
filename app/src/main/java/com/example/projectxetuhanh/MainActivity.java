package com.example.projectxetuhanh;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.FaceDetector;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.felhr.usbserial.UsbSerialDevice;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class MainActivity extends AppCompatActivity {
    private UsbSerialDevice usbDevice;
    private FaceClassifier faceClassifier;

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

        // Khởi tạo model TFLite
        try {
            MappedByteBuffer model = loadModelFile("face_model.tflite");
            faceClassifier = new FaceClassifier(model);
        } catch (IOException e) {
            Log.e("FaceRecognition", "Lỗi tải model", e);
        }

        // Khởi tạo kết nối USB (tham khảo code trước)
        initUSB();

        // Khởi động camera
        startCamera();
    }
    private void startCamera() {
        ProcessCameraProvider cameraProvider = ...; // Sử dụng CameraX
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .build();

        analysis.setAnalyzer(executor, imageProxy -> {
            Bitmap bitmap = imageProxy.toBitmap();

            // Phát hiện khuôn mặt bằng ML Kit
            FaceDetector detector = FaceDetection.getClient();
            detector.process(InputImage.fromBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees()))
                    .addOnSuccessListener(faces -> {
                        if (!faces.isEmpty()) {
                            Rect bounds = faces.get(0).getBoundingBox();
                            Bitmap faceCrop = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height());

                            // Kiểm tra khuôn mặt
                            if (faceClassifier.isMyFace(faceCrop)) {
                                float centerX = bounds.centerX();
                                sendMovementCommand(centerX, imageProxy.getWidth());
                            }
                        }
                    });
            imageProxy.close();
        });

        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis);
    }

    private void sendMovementCommand(float faceX, int imageWidth) {
        String cmd;
        if (faceX < imageWidth * 0.4) cmd = "L";
        else if (faceX > imageWidth * 0.6) cmd = "R";
        else cmd = "F";

        if (usbDevice != null) {
            usbDevice.write(cmd.getBytes());
        }
    }

    // Tải model từ assets
    private MappedByteBuffer loadModelFile(String filename) throws IOException {
        AssetFileDescriptor fd = getAssets().openFd(filename);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        return fis.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }


    private void initUSB() {
        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
        UsbDevice arduino = null;

        // Tìm thiết bị Arduino qua Vendor ID
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x2341) { // Vendor ID của Arduino
                arduino = device;
                break;
            }
        }

        if (arduino != null) {
            // Tạo PendingIntent đầy đủ tham số
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(UsbManager.ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_MUTABLE // Bắt buộc dùng FLAG_MUTABLE
            );
            usbManager.requestPermission(arduino, permissionIntent);

            // Mở kết nối serial
            usbDevice = UsbSerialDevice.createUsbSerialDevice(arduino, usbManager.openDevice(arduino));
            if (usbDevice != null) {
                usbDevice.open();
                usbDevice.setBaudRate(9600); // Cấu hình baud rate
            }
        }
    }
}