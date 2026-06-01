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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * BluetoothFanController — BLE edition
 *
 * Uses standard Java functional interfaces instead of custom callback classes:
 *
 *   connect() accepts three Runnables / Consumers:
 *     Consumer<String>  onConnected    — receives device name
 *     Runnable          onDisconnected
 *     Consumer<String>  onError        — receives error message
 *
 *   setDataCallback() accepts:
 *     Consumer<String>  onRawLine
 *     SensorListener    onSensorData   (4 floats — temp, humidity, gas1, gas2)
 *     Consumer<Boolean> onFanStatus    — true = ON
 *
 *   FanTimerManager.start() accepts:
 *     Consumer<Integer> onTick         — receives secondsRemaining
 *     Runnable          onFinished
 *
 * ── UUIDs (must match ESP32 sketch) ──────────────────────────────────────
 *   SERVICE_UUID  4fafc201-1fb5-459e-8fcc-c5c9c331914b
 *   WRITE_UUID    beb5483e-36e1-4688-b7f5-ea07361b26a8  (app → ESP32)
 *   NOTIFY_UUID   beb5483e-36e1-4688-b7f5-ea07361b26a9  (ESP32 → app)
 *
 * ── ESP32 sends (notify) ──────────────────────────────────────────────────
 *   DATA:27.5,65.0,120,85,1   → temp,hum,gas1,gas2,fanstate(1=ON)
 *   FAN:ON / FAN:OFF
 *
 * ── App sends (write) ────────────────────────────────────────────────────
 *   FAN_ON / FAN_OFF / TIMER_30 / TIMER_CANCEL
 */
@SuppressLint("MissingPermission")
public class BluetoothFanController {

    // ── UUIDs ─────────────────────────────────────────────────────────────────
    private static final UUID SERVICE_UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID WRITE_UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID NOTIFY_UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9");
    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // ── Config ────────────────────────────────────────────────────────────────
    private static final int SCAN_TIMEOUT_MS     = 15_000;
    private static final int RECONNECT_DELAY_MS  = 5_000;
    private static final int MAX_RECONNECT_TRIES = 5;

    // ── State ─────────────────────────────────────────────────────────────────
    private static BluetoothGatt      gatt                  = null;
    private static BluetoothLeScanner scanner               = null;
    private static boolean            connected             = false;
    private static boolean            intentionalDisconnect = false;
    private static int                reconnectCount        = 0;
    private static Context            appContext            = null;
    private static String             targetDeviceName      = null;

    // Connection callbacks
    private static Consumer<String> onConnected    = null;
    private static Runnable         onDisconnected = null;
    private static Consumer<String> onError        = null;

    // Data callbacks
    private static Consumer<String>  onRawLine    = null;
    private static SensorListener    onSensorData = null;
    private static Consumer<Boolean> onFanStatus  = null;

    private static final Handler mainHandler      = new Handler(Looper.getMainLooper());
    private static final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start connecting to the named BLE device.
     *
     * @param context       any Context
     * @param deviceName    BLE advertised name (e.g. "ESP32S3_Config_Node")
     * @param connected     called with device name once fully connected
     * @param disconnected  called when connection drops
     * @param error         called with error message on failure
     */
    public static void connect(Context context,
                               String deviceName,
                               Consumer<String> connected,
                               Runnable disconnected,
                               Consumer<String> error) {
        appContext            = context.getApplicationContext();
        targetDeviceName      = deviceName;
        onConnected           = connected;
        onDisconnected        = disconnected;
        onError               = error;
        intentionalDisconnect = false;
        reconnectCount        = 0;
        startScan();
    }

    /**
     * Register listeners for incoming ESP32 data.
     *
     * @param rawLine    called with every raw text line received
     * @param sensor     called with parsed temp/humidity/gas readings
     * @param fanStatus  called with true=ON / false=OFF fan state
     */
    public static void setDataCallback(Consumer<String> rawLine,
                                       SensorListener sensor,
                                       Consumer<Boolean> fanStatus) {
        onRawLine    = rawLine;
        onSensorData = sensor;
        onFanStatus  = fanStatus;
    }

    public static void sendFanOn()            { write("FAN_ON"); }
    public static void sendFanOff()           { write("FAN_OFF"); }
    public static void sendTimer(int minutes) { write("TIMER_" + minutes); }
    public static void sendTimerCancel()      { write("TIMER_CANCEL"); }
    public static boolean isConnected()       { return connected; }

    public static void disconnect() {
        intentionalDisconnect = true;
        reconnectHandler.removeCallbacksAndMessages(null);
        stopScan();
        connected = false;
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        if (onDisconnected != null) onDisconnected.run();
    }

    // ── BLE scan ──────────────────────────────────────────────────────────────

    private static void startScan() {
        if (appContext == null) return;

        BluetoothManager btManager =
                (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btManager != null ? btManager.getAdapter() : null;

        if (adapter == null || !adapter.isEnabled()) {
            postError("Bluetooth is off or not supported");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            postError("BLE scanner unavailable");
            return;
        }

        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName(targetDeviceName)
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);

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
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            stopScan();
            gatt = device.connectGatt(appContext, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
        }
        @Override
        public void onScanFailed(int errorCode) {
            postError("BLE scan failed — error " + errorCode);
        }
    };

    // ── GATT callbacks ────────────────────────────────────────────────────────

    private static final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                reconnectCount = 0;
                g.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                g.close();
                gatt = null;
                mainHandler.post(() -> {
                    if (onDisconnected != null) onDisconnected.run();
                });
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
                postError("SmartVent service not found on device");
                return;
            }
            BluetoothGattCharacteristic notifyChar =
                    service.getCharacteristic(NOTIFY_UUID);
            if (notifyChar != null) {
                g.setCharacteristicNotification(notifyChar, true);
                BluetoothGattDescriptor descriptor = notifyChar.getDescriptor(CCCD_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(descriptor);
                }
            }
            String name = g.getDevice().getName();
            mainHandler.post(() -> {
                if (onConnected != null)
                    onConnected.accept(name != null ? name : "ESP32");
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g,
                                            BluetoothGattCharacteristic characteristic) {
            String line = new String(characteristic.getValue(),
                    StandardCharsets.UTF_8).trim();
            mainHandler.post(() -> dispatchLine(line));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) { }
    };

    // ── Parse incoming data ───────────────────────────────────────────────────

    private static void dispatchLine(String line) {
        if (onRawLine != null) onRawLine.accept(line);

        try {
            if (line.startsWith("DATA:")) {
                String[] p = line.substring(5).split(",");
                if (p.length >= 4 && onSensorData != null) {
                    float tempC = Float.parseFloat(p[0]);
                    float hum   = Float.parseFloat(p[1]);
                    float gas1  = Float.parseFloat(p[2]);
                    float gas2  = Float.parseFloat(p[3]);
                    onSensorData.onData(tempC, hum, gas1, gas2);
                    if (p.length >= 5 && onFanStatus != null) {
                        onFanStatus.accept("1".equals(p[4]));
                    }
                }
            } else if (line.equals("FAN:ON")) {
                if (onFanStatus != null) onFanStatus.accept(true);
            } else if (line.equals("FAN:OFF")) {
                if (onFanStatus != null) onFanStatus.accept(false);
            }
        } catch (NumberFormatException ignored) {}
    }

    // ── Write to ESP32 ────────────────────────────────────────────────────────

    private static void write(String command) {
        if (!connected || gatt == null) return;
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic writeChar = service.getCharacteristic(WRITE_UUID);
        if (writeChar == null) return;
        writeChar.setValue(command.getBytes(StandardCharsets.UTF_8));
        writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(writeChar);
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private static void scheduleReconnect() {
        if (reconnectCount >= MAX_RECONNECT_TRIES) {
            postError("Could not reach ESP32 after " + MAX_RECONNECT_TRIES + " attempts.");
            return;
        }
        reconnectCount++;
        reconnectHandler.postDelayed(() -> {
            if (!intentionalDisconnect) startScan();
        }, RECONNECT_DELAY_MS);
    }

    private static void postError(String msg) {
        mainHandler.post(() -> {
            if (onError != null) onError.accept(msg);
        });
    }
}