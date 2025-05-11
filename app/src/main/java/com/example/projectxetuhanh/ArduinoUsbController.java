package com.example.projectxetuhanh;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.browse.MediaBrowser;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArduinoUsbController implements SerialInputOutputManager.Listener {
    private static final int BAUD_RATE = 115200;
    private static final int WRITE_WAIT_MS = 2000;

    private Context context;
    private UsbManager usbManager;
    private UsbSerialPort port;
    private SerialInputOutputManager usbIoManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConnectionCallback connectionCallback;
    private UsbDeviceConnection connection;

    private static final String ACTION_USB_PERMISSION = "com.example.USB_PERMISSION";
    private PendingIntent permissionIntent;

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            // Permission granted, thử kết nối lại
                            connect();
                        }
                    } else {
                        connectionCallback.onConnectionError("Permission denied for device " + device);
                    }
                }
            }
        }
    };
    public interface ConnectionCallback {
        void onConnectionStatusChange(String status);

        void onDataReceived(String data);

        void onConnectionError(String error);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public ArduinoUsbController(Context context, ConnectionCallback callback) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.connectionCallback = callback;

        permissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    public boolean connect() {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        // Kiểm tra thiết bị
        if (availableDrivers.isEmpty()) {
            showToast("Không tìm thấy thiết bị Arduino");
            return false;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // Kiểm tra quyền truy cập
        if (!usbManager.hasPermission(device)) {
            showToast("Yêu cầu quyền truy cập USB...");
            usbManager.requestPermission(device, permissionIntent);
            return true;
        }

        // Thực hiện kết nối trong luồng phụ
        new Thread(() -> {
            connection = usbManager.openDevice(device);
            if (connection == null) {
                showToast("Không thể mở kết nối");
                return;
            }

            try {
                port = driver.getPorts().get(0);
                port.open(connection);
                port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                usbIoManager = new SerialInputOutputManager(port, this);
                usbIoManager.start();

                showToast("Đã kết nối với Arduino"); // Toast khi kết nối thành công
            } catch (IOException e) {
                showToast("Lỗi kết nối: " + e.getMessage());
                disconnect();
            }
        }).start();

        return true;
    }

    // Phương thức hiển thị Toast
    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }

    public boolean disconnect() {
        boolean disconnectedSuccessfully = false;

        try {
            // Dừng I/O manager
            if (usbIoManager != null) {
                usbIoManager.stop();
                usbIoManager = null;
            }

            // Đóng port
            if (port != null) {
                port.close();
                port = null;
            }

            // Đóng kết nối USB
            if (connection != null) {
                connection.close();
                connection = null;
            }

            disconnectedSuccessfully = true;
        } catch (IOException e) {
            Log.e("USB", "Lỗi khi ngắt kết nối: " + e.getMessage());
            disconnectedSuccessfully = false;
        } finally {
            // Luôn cập nhật trạng thái dù có lỗi hay không
            connectionCallback.onConnectionStatusChange(disconnectedSuccessfully
                    ? "Đã ngắt kết nối thành công"
                    : "Lỗi khi ngắt kết nối");

            // Reset mọi trạng thái
            usbIoManager = null;
            port = null;
            connection = null;
        }

        return disconnectedSuccessfully;
    }

    public boolean isConnected() {
        return port != null && connection != null && port.isOpen();
    }

    public void sendControlCommand(String direction) {
        if (port == null) {
            connectionCallback.onConnectionError("Not connected to device");
            return;
        }

        try {
            port.write(direction.getBytes(), WRITE_WAIT_MS);
        } catch (IOException e) {
            connectionCallback.onConnectionError("Send failed: " + e.getMessage());
        }
    }

    @Override
    public void onNewData(byte[] data) {
        String receivedData = new String(data);
        connectionCallback.onDataReceived(receivedData);
    }

    @Override
    public void onRunError(Exception e) {
        Log.e("USB_COMM", "Lỗi truyền dữ liệu: " + e.getClass().getSimpleName() + ": " + e.getMessage());

        // Kiểm tra loại lỗi
        if (e instanceof IOException) {
            connectionCallback.onConnectionError("Mất kết nối vật lý");
        } else {
            connectionCallback.onConnectionError("Lỗi không xác định: " + e.getMessage());
        }

        disconnect();
    }

    public void release() {
        disconnect();
        context.unregisterReceiver(usbPermissionReceiver);
        executor.shutdown();
    }
}