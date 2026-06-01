package com.example.smartventapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressLint("MissingPermission")
public class BluetoothFanController {

    private static final String TAG = "BLE_Controller";

    // UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int SCAN_TIMEOUT_MS = 15000;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int MAX_RECONNECT_TRIES = 5;

    private static BluetoothGatt gatt = null;
    private static BluetoothLeScanner scanner = null;
    private static boolean connected = false;
    private static boolean intentionalDisconnect = false;
    private static int reconnectCount = 0;
    private static Context appContext = null;
    private static String targetDeviceName = null;

    // Callbacks - all using Consumer<T>
    private static Consumer<String> onConnected = null;
    private static Runnable onDisconnected = null;
    private static Consumer<String> onError = null;
    private static Consumer<String> onRawLine = null;
    private static Consumer<String> onSensorData = null;  // JSON string instead
    private static Consumer<Boolean> onFanStatus = null;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    // ========== PUBLIC API ==========

    public static void connect(Context context, String deviceName,
                               Consumer<String> connected,
                               Runnable disconnected,
                               Consumer<String> error) {
        appContext = context.getApplicationContext();
        targetDeviceName = deviceName;
        onConnected = connected;
        onDisconnected = disconnected;
        onError = error;
        intentionalDisconnect = false;
        reconnectCount = 0;
        startScan();
    }

    public static void setDataCallback(Consumer<String> rawLine,
                                       Consumer<String> sensorDataJson,
                                       Consumer<Boolean> fanStatus) {
        onRawLine = rawLine;
        onSensorData = sensorDataJson;
        onFanStatus = fanStatus;
    }

    public static void sendFanOn() { write("FAN_ON"); }
    public static void sendFanOff() { write("FAN_OFF"); }
    public static void sendTimer(int minutes) { write("TIMER_" + minutes); }
    public static void sendTimerCancel() { write("TIMER_CANCEL"); }
    public static boolean isConnected() { return connected; }

    public static void disconnect() {
        intentionalDisconnect = true;
        reconnectHandler.removeCallbacksAndMessages(null);
        stopScan();
        connected = false;
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting", e);
            }
            gatt = null;
        }
        if (onDisconnected != null) onDisconnected.run();
    }

    // ========== BLE SCAN ==========

    private static void startScan() {
        if (appContext == null) return;

        BluetoothManager btManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btManager != null ? btManager.getAdapter() : null;

        if (adapter == null || !adapter.isEnabled()) {
            postError("Bluetooth is off");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            postError("BLE not available");
            return;
        }

        ScanFilter filter = new ScanFilter.Builder().setDeviceName(targetDeviceName).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        try {
            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        } catch (Exception e) {
            postError("Scan failed: " + e.getMessage());
        }

        mainHandler.postDelayed(() -> {
            stopScan();
            if (!connected && !intentionalDisconnect) scheduleReconnect();
        }, SCAN_TIMEOUT_MS);
    }

    private static void stopScan() {
        if (scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
            scanner = null;
        }
    }

    private static final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            stopScan();
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        }
        @Override
        public void onScanFailed(int errorCode) {
            postError("Scan failed: " + errorCode);
        }
    };

    // ========== GATT CALLBACKS ==========

    private static final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connected = false;
                if (!intentionalDisconnect) scheduleReconnect();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                reconnectCount = 0;
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                try { g.close(); } catch (Exception e) { Log.e(TAG, "Error closing", e); }
                gatt = null;
                mainHandler.post(() -> { if (onDisconnected != null) onDisconnected.run(); });
                if (!intentionalDisconnect) scheduleReconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("Service discovery failed");
                return;
            }

            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                postError("Service not found");
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic == null) {
                postError("Characteristic not found");
                return;
            }

            g.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
            }

            String name = g.getDevice().getName();
            mainHandler.post(() -> {
                if (onConnected != null) onConnected.accept(name != null ? name : "ESP32");
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            String line = new String(characteristic.getValue(), StandardCharsets.UTF_8).trim();
            mainHandler.post(() -> dispatchLine(line));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {}
    };

    // ========== PARSE DATA ==========

    private static void dispatchLine(String line) {
        if (onRawLine != null) onRawLine.accept(line);

        try {
            if (line.startsWith("DATA:")) {
                String[] parts = line.substring(5).split(",");
                if (parts.length >= 4 && onSensorData != null) {
                    // Send as JSON string
                    String json = "{\"temp\":" + parts[0] + ",\"humidity\":" + parts[1] + 
                                  ",\"gas1\":" + parts[2] + ",\"gas2\":" + parts[3] + 
                                  ",\"fan\":" + (parts.length >= 5 ? parts[4] : "0") + "}";
                    onSensorData.accept(json);
                    
                    if (parts.length >= 5 && onFanStatus != null) {
                        onFanStatus.accept("1".equals(parts[4]));
                    }
                }
            } else if (line.equals("FAN:ON") && onFanStatus != null) {
                onFanStatus.accept(true);
            } else if (line.equals("FAN:OFF") && onFanStatus != null) {
                onFanStatus.accept(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + line, e);
        }
    }

    // ========== WRITE ==========

    private static void write(String command) {
        if (!connected || gatt == null) return;

        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) return;

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic == null) return;

        characteristic.setValue(command.getBytes(StandardCharsets.UTF_8));
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(characteristic);
    }

    // ========== RECONNECT ==========

    private static void scheduleReconnect() {
        if (reconnectCount >= MAX_RECONNECT_TRIES) {
            postError("Max reconnect attempts reached");
            return;
        }
        reconnectCount++;
        reconnectHandler.postDelayed(() -> {
            if (!intentionalDisconnect && !connected) startScan();
        }, RECONNECT_DELAY_MS);
    }

    private static void postError(String msg) {
        Log.e(TAG, msg);
        mainHandler.post(() -> { if (onError != null) onError.accept(msg); });
    }
}
