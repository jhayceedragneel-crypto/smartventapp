package com.example.smartventapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("MissingPermission")
public class ConnectionActivity extends AppCompatActivity {

    private static final int SCAN_TIMEOUT_MS = 15_000;

    private boolean btConnected   = false;
    private boolean scanning      = false;
    private String  connectedName = "";

    private TextView     tvBtStatus, tvBtSub, tvBtLastCmd, btnBtConnect, btnScan;
    private LinearLayout scanList;

    private BluetoothLeScanner bleScanner;
    private final Handler      scanHandler = new Handler(Looper.getMainLooper());

    private final List<String> foundNames = new ArrayList<>();

    private ActivityResultLauncher<String[]> permLauncher;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_connection);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.activity_connection_root), (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                    return insets;
                });

        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean ok =
                            Boolean.TRUE.equals(result.get(Manifest.permission.BLUETOOTH_SCAN))
                                    || Boolean.TRUE.equals(result.get(Manifest.permission.BLUETOOTH_CONNECT));
                    if (ok) startBleScan();
                    else Toast.makeText(this,
                            "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
                });

        bindViews();

        btConnected = BluetoothFanController.isConnected();
        updateStatusUI(btConnected ? connectedName : null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        btConnected = BluetoothFanController.isConnected();
        updateStatusUI(btConnected ? connectedName : null, null);
        if (!btConnected) requestPermAndScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBleScan();
    }

    // ── Bind views ────────────────────────────────────────────────────────────

    private void bindViews() {
        tvBtStatus   = findViewById(R.id.tvBtStatus);
        tvBtSub      = findViewById(R.id.tvBtSub);
        tvBtLastCmd  = findViewById(R.id.tvBtLastCmd);
        btnBtConnect = findViewById(R.id.btnBtConnect);
        btnScan      = findViewById(R.id.btnScan);
        scanList     = findViewById(R.id.scanList);

        View backContainer = findViewById(R.id.btnBackContainer);
        if (backContainer != null)
            backContainer.setOnClickListener(v -> finish());

        if (btnBtConnect != null)
            btnBtConnect.setOnClickListener(v -> toggleConnection());

        if (btnScan != null)
            btnScan.setOnClickListener(v -> requestPermAndScan());
    }

    // ── Status UI ─────────────────────────────────────────────────────────────

    private void updateStatusUI(String deviceName, String errorMsg) {
        if (tvBtStatus == null) return;

        if (deviceName != null && !deviceName.isEmpty()) {
            tvBtStatus.setText("● Connected");
            tvBtStatus.setTextColor(0xFF00FF88);
            if (tvBtSub      != null) tvBtSub.setText(deviceName + " · BLE");
            if (btnBtConnect != null) btnBtConnect.setText("Disconnect");
        } else if (errorMsg != null) {
            tvBtStatus.setText("● Error");
            tvBtStatus.setTextColor(0xFFFF4444);
            if (tvBtSub      != null) tvBtSub.setText(errorMsg);
            if (btnBtConnect != null) btnBtConnect.setText("Retry");
        } else {
            tvBtStatus.setText("● Disconnected");
            tvBtStatus.setTextColor(0xFFAAAAAA);
            if (tvBtSub      != null) tvBtSub.setText("Scanning for ESP32…");
            if (btnBtConnect != null) btnBtConnect.setText("Connect");
            if (tvBtLastCmd  != null) tvBtLastCmd.setText("—");
        }
    }

    // ── Connect / disconnect ──────────────────────────────────────────────────

    private void toggleConnection() {
        if (btConnected) {
            BluetoothFanController.disconnect();
            btConnected   = false;
            connectedName = "";
            updateStatusUI(null, null);
            requestPermAndScan();
        } else {
            requestPermAndScan();
        }
    }

    private void connectTo(String deviceName) {
        stopBleScan();
        if (tvBtStatus != null) {
            tvBtStatus.setText("● Connecting…");
            tvBtStatus.setTextColor(0xFFFFCC00);
        }
        if (tvBtSub != null) tvBtSub.setText("Connecting to " + deviceName + "…");

        BluetoothFanController.connect(
                this,
                deviceName,
                // onConnected — Consumer<String>
                name -> {
                    btConnected   = true;
                    connectedName = name;
                    runOnUiThread(() -> {
                        updateStatusUI(name, null);
                        if (tvBtLastCmd != null) tvBtLastCmd.setText("Idle");
                        Toast.makeText(this, "Connected to " + name,
                                Toast.LENGTH_SHORT).show();
                        highlightConnectedDevice(name);
                    });
                },
                // onDisconnected — Runnable
                () -> {
                    btConnected   = false;
                    connectedName = "";
                    runOnUiThread(() -> updateStatusUI(null, null));
                },
                // onError — Consumer<String>
                msg -> {
                    btConnected = false;
                    runOnUiThread(() -> updateStatusUI(null, msg));
                }
        );

        BluetoothFanController.setDataCallback(
                // onRawLine — Consumer<String>
                line -> runOnUiThread(() -> {
                    if (tvBtLastCmd != null) tvBtLastCmd.setText("← " + line);
                }),
                // onSensorData — SensorListener (ignored here)
                (t, h, g1, g2) -> { },
                // onFanStatus — Consumer<Boolean>
                isOn -> runOnUiThread(() -> {
                    if (tvBtLastCmd != null)
                        tvBtLastCmd.setText("Fan " + (isOn ? "ON" : "OFF"));
                })
        );
    }

    // ── BLE scan ──────────────────────────────────────────────────────────────

    private void requestPermAndScan() {
        if (!hasPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION});
            }
            return;
        }
        startBleScan();
    }

    private void startBleScan() {
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Turn on Bluetooth first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) return;

        if (scanning) stopBleScan();

        scanning = true;
        foundNames.clear();
        if (scanList != null) scanList.removeAllViews();
        if (btnScan  != null) btnScan.setText("Scanning…");

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bleScanner.startScan(null, settings, bleScanCallback);

        scanHandler.postDelayed(() -> {
            stopBleScan();
            if (btnScan != null) btnScan.setText("Scan again");
            if (scanList != null && scanList.getChildCount() == 0) addNoDevicesRow();
        }, SCAN_TIMEOUT_MS);
    }

    private void stopBleScan() {
        scanning = false;
        scanHandler.removeCallbacksAndMessages(null);
        if (bleScanner != null) {
            try { bleScanner.stopScan(bleScanCallback); } catch (Exception ignored) {}
            bleScanner = null;
        }
        if (btnScan != null) btnScan.setText("Scan again");
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String name = result.getDevice().getName();
            if (name == null || name.isEmpty()) return;
            if (foundNames.contains(name)) return;
            foundNames.add(name);
            runOnUiThread(() -> addDeviceRow(name));
        }
        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() ->
                    Toast.makeText(ConnectionActivity.this,
                            "Scan failed: " + errorCode, Toast.LENGTH_SHORT).show());
        }
    };

    // ── Device list ───────────────────────────────────────────────────────────

    private void addDeviceRow(String name) {
        if (scanList == null) return;

        boolean isThisOne = name.equals(connectedName);

        if (scanList.getChildCount() > 0) {
            View div = new View(this);
            div.setBackgroundColor(0xFF222222);
            scanList.addView(div,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(32, 24, 32, 24);
        row.setBackgroundColor(isThisOne ? 0xFF0D2B1A : 0xFF151515);
        row.setTag(name);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(isThisOne ? 0xFF00FF88 : 0xFFEEEEEE);
        tvName.setTextSize(15f);
        row.addView(tvName);

        TextView tvSub = new TextView(this);
        tvSub.setText(isThisOne ? "● Connected · BLE" : "BLE Device · Tap to connect");
        tvSub.setTextColor(isThisOne ? 0xFF00FF88 : 0xFF666666);
        tvSub.setTextSize(12f);
        row.addView(tvSub);

        row.setOnClickListener(v -> {
            if (isThisOne) {
                Toast.makeText(this, "Already connected to " + name,
                        Toast.LENGTH_SHORT).show();
            } else {
                connectTo(name);
            }
        });

        scanList.addView(row);
    }

    private void addNoDevicesRow() {
        if (scanList == null) return;
        TextView tv = new TextView(this);
        tv.setText("No BLE devices found.\nMake sure ESP32 is powered on.");
        tv.setTextColor(0xFF555555);
        tv.setTextSize(13f);
        tv.setPadding(32, 24, 32, 24);
        scanList.addView(tv);
    }

    private void highlightConnectedDevice(String name) {
        if (scanList == null) return;
        for (int i = 0; i < scanList.getChildCount(); i++) {
            View child = scanList.getChildAt(i);
            if (name.equals(child.getTag())) {
                child.setBackgroundColor(0xFF0D2B1A);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private BluetoothAdapter getAdapter() {
        BluetoothManager m =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return m != null ? m.getAdapter() : null;
    }
}