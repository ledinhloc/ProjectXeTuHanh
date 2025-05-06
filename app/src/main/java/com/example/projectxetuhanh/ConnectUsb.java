package com.example.projectxetuhanh;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
public class ConnectUsb {
    private static final String TAG = "ConnectUsb";
    private static final int ARDUINO_VENDOR_ID = 0x2341;
    private static final int ARDUINO_PRODUCT_ID = 0x0043;
    private static final int TIMEOUT = 500;

    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpointOut;
    private UsbInterface usbInterface;

    public ConnectUsb(UsbManager usbManager) {
        this.usbManager = usbManager;
    }

    public boolean initialize(UsbDevice device) {
        try {
            // Tìm interface phù hợp
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);

                // Tìm endpoint OUT
                for (int j = 0; j < intf.getEndpointCount(); j++) {
                    UsbEndpoint endpoint = intf.getEndpoint(j);
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = endpoint;
                        usbInterface = intf;
                        break;
                    }
                }

                if (usbInterface != null) break;
            }

            if (usbInterface == null || endpointOut == null) {
                Log.e(TAG, "No OUT endpoint or interface found");
                return false;
            }

            connection = usbManager.openDevice(device);
            if (connection == null) {
                Log.e(TAG, "Failed to open device connection");
                return false;
            }

            if (!connection.claimInterface(usbInterface, true)) {
                Log.e(TAG, "Failed to claim interface");
                connection.close();
                return false;
            }

            Log.d(TAG, "USB initialized successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Initialization error: " + e.getMessage());
            if (connection != null) {
                connection.close();
            }
            return false;
        }
    }

    public boolean isConnected() {
        return connection != null && endpointOut != null;
    }

    public void sendControlCommand(String direction, int ratio) {
        if (!isConnected()) {
            Log.e(TAG, "USB not connected. Call initialize() first");
            return;
        }

        try {
            String data = direction + (direction.equals("W") ? "" : ratio) + "\n";
            byte[] bytes = data.getBytes("UTF-8");

            int transferred = connection.bulkTransfer(
                    endpointOut,
                    bytes,
                    bytes.length,
                    TIMEOUT
            );

            if (transferred < 0) {
                Log.e(TAG, "Transfer failed with code: " + transferred);
            } else {
                Log.d(TAG, "Sent " + transferred + " bytes: " + data.trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "Send command error: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null) {
                connection.releaseInterface(usbInterface);
                connection.close();
                connection = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Close connection error: " + e.getMessage());
        }
    }

    public boolean isArduinoDevice(UsbDevice device) {
        return device.getVendorId() == ARDUINO_VENDOR_ID
                && device.getProductId() == ARDUINO_PRODUCT_ID;
    }
}