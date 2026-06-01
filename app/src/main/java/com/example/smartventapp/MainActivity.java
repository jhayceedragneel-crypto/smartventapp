package com.example.smartventapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * MainActivity — SmartVent (ESP32 BLE edition)
 *
 * No custom callback classes. Uses standard Java:
 *   Consumer<String>  for onConnected, onError, onRawLine
 *   Consumer<Boolean> for onFanStatus
 *   Consumer<Integer> for onTick (timer)
 *   Runnable          for onDisconnected, onFinished
 *   SensorListener    for onSensorData (4 floats — defined in BluetoothFanController)
 */
public class MainActivity extends AppCompatActivity {

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isFahrenheit = false;
    private boolean fanIsOn      = false;
    private int     timerMinutes = 30;

    private float lastTempC    = 0f;
    private int   lastHumidity = 0;

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView             tvBtStatus;
    private TextView             tempValue, unitLabel, humidityValue;
    private TextView             tvGas1, tvGas2, tvAirQualityBadge;
    private TextView             tvTimerDisplay, tvTimerMinutes, tvTimerStartLabel;
    private TextView             tvFanStatusLabel, tvOutdoorTimerEcho;
    private DialTicksView        dialTicks;
    private WeatherAnimationView weatherAnim;
    private SwitchCompat         fanSwitch;

    private static final String ARDUINO_BT_NAME = "ESP32S3_Config_Node";

    private ActivityResultLauncher<String[]> btPermissionLauncher;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        btPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.BLUETOOTH_CONNECT));
                    if (granted) connectBluetooth();
                    else Toast.makeText(this,
                            "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
                });

        bindViews();
        setupFanSwitch();
        setupTimer();
        setupUnitToggle();
        setupMenu();
        requestBluetoothAndConnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FanTimerManager.cancel();
        BluetoothFanController.disconnect();
    }

    // ── Bind views ────────────────────────────────────────────────────────────

    private void bindViews() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.activity_main_root), (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                    return insets;
                });

        tvBtStatus         = findViewById(R.id.tvBtStatus);
        tempValue          = findViewById(R.id.temp_value);
        unitLabel          = findViewById(R.id.unit_label);
        humidityValue      = findViewById(R.id.humidity_value);
        tvGas1             = findViewById(R.id.tvGas1);
        tvGas2             = findViewById(R.id.tvGas2);
        tvAirQualityBadge  = findViewById(R.id.tvAirQualityBadge);
        tvTimerDisplay     = findViewById(R.id.tvTimerDisplay);
        tvTimerMinutes     = findViewById(R.id.tvTimerMinutes);
        tvTimerStartLabel  = findViewById(R.id.tvTimerStartLabel);
        tvFanStatusLabel   = findViewById(R.id.tvFanStatusLabel);
        tvOutdoorTimerEcho = findViewById(R.id.tvOutdoorTimerEcho);
        dialTicks          = findViewById(R.id.dial_ticks);
        weatherAnim        = findViewById(R.id.weather_anim);
        fanSwitch          = findViewById(R.id.offonbtn);

        if (tvTimerMinutes != null) tvTimerMinutes.setText(timerMinutes + " min");
    }

    // ── Bluetooth ─────────────────────────────────────────────────────────────

    @SuppressLint("InlinedApi")
    private void requestBluetoothAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                btPermissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN});
                return;
            }
        }
        connectBluetooth();
    }

    private void connectBluetooth() {
        setBtStatus("Connecting…", 0xFFFFCC00);

        BluetoothFanController.connect(
                this,
                ARDUINO_BT_NAME,
                // onConnected — Consumer<String> receives device name
                name -> runOnUiThread(() -> {
                    setBtStatus("● " + name, 0xFF00FF88);
                    Toast.makeText(this, "Connected to " + name,
                            Toast.LENGTH_SHORT).show();
                }),
                // onDisconnected — Runnable
                () -> runOnUiThread(() -> setBtStatus("● Disconnected", 0xFFAAAAAA)),
                // onError — Consumer<String> receives error message
                msg -> runOnUiThread(() -> {
                    setBtStatus("● Error", 0xFFFF4444);
                    Toast.makeText(this, "Bluetooth: " + msg,
                            Toast.LENGTH_LONG).show();
                })
        );

        BluetoothFanController.setDataCallback(
                // onRawLine — Consumer<String>
                line -> { /* debug: Log.d("BT", line); */ },

                // onSensorData — SensorListener (temp, humidity, gas1, gas2)
                (tempC, humidity, gas1, gas2) -> runOnUiThread(() -> {
                    if (tempC >= 0) {
                        lastTempC = tempC;
                        int display = isFahrenheit
                                ? (int)(tempC * 9 / 5 + 32) : (int) tempC;
                        if (tempValue != null)
                            tempValue.setText(String.valueOf(display));
                        if (dialTicks != null) dialTicks.setValue(display);
                        updateWeatherVibe(tempC);
                    }
                    if (humidity >= 0) {
                        lastHumidity = (int) humidity;
                        if (humidityValue != null)
                            humidityValue.setText((int) humidity + "%");
                    }
                    if (gas1 >= 0 && tvGas1 != null)
                        tvGas1.setText("Gas 1: " + (int) gas1);
                    if (gas2 >= 0 && tvGas2 != null)
                        tvGas2.setText("Gas 2: " + (int) gas2);

                    AirQualityHelper.applyBadge(tvAirQualityBadge, lastTempC, lastHumidity);
                    NotificationHelper.checkAndNotify(this, (double) lastTempC, lastHumidity);
                }),

                // onFanStatus — Consumer<Boolean>
                isOn -> runOnUiThread(() -> {
                    fanIsOn = isOn;
                    if (fanSwitch != null && fanSwitch.isChecked() != isOn) {
                        fanSwitch.setOnCheckedChangeListener(null);
                        fanSwitch.setChecked(isOn);
                        fanSwitch.setOnCheckedChangeListener(
                                (btn, checked) -> setFan(checked));
                    }
                    if (!isOn && FanTimerManager.isRunning()) cancelTimer();
                })
        );
    }

    // ── Fan control ───────────────────────────────────────────────────────────

    private void setupFanSwitch() {
        if (fanSwitch != null)
            fanSwitch.setOnCheckedChangeListener((btn, checked) -> setFan(checked));
    }

    private void setFan(boolean on) {
        if (!BluetoothFanController.isConnected()) {
            Toast.makeText(this, "Not connected to Arduino", Toast.LENGTH_SHORT).show();
            if (fanSwitch != null) {
                fanSwitch.setOnCheckedChangeListener(null);
                fanSwitch.setChecked(fanIsOn);
                fanSwitch.setOnCheckedChangeListener((btn, checked) -> setFan(checked));
            }
            return;
        }
        if (on) BluetoothFanController.sendFanOn();
        else    BluetoothFanController.sendFanOff();

        fanIsOn = on;
        if (tvFanStatusLabel != null) {
            tvFanStatusLabel.setText(on ? "ON" : "OFF");
            tvFanStatusLabel.setTextColor(on ? 0xFF00FF88 : 0xFFFF5555);
        }
        if (!on && FanTimerManager.isRunning()) cancelTimer();
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void setupTimer() {
        if (tvTimerMinutes != null) tvTimerMinutes.setText(timerMinutes + " min");

        View btnMinus = findViewById(R.id.btnTimerMinus);
        View btnPlus  = findViewById(R.id.btnTimerPlus);
        View btnStart = findViewById(R.id.btnTimerStart);

        if (btnMinus != null)
            btnMinus.setOnClickListener(v -> {
                if (FanTimerManager.isRunning()) return;
                if (timerMinutes > 5) timerMinutes -= 5;
                if (tvTimerMinutes != null) tvTimerMinutes.setText(timerMinutes + " min");
            });

        if (btnPlus != null)
            btnPlus.setOnClickListener(v -> {
                if (FanTimerManager.isRunning()) return;
                if (timerMinutes < 180) timerMinutes += 5;
                if (tvTimerMinutes != null) tvTimerMinutes.setText(timerMinutes + " min");
            });

        if (btnStart != null)
            btnStart.setOnClickListener(v -> {
                if (FanTimerManager.isRunning()) cancelTimer();
                else startTimer();
            });
    }

    private void startTimer() {
        if (!BluetoothFanController.isConnected()) {
            Toast.makeText(this, "Connect to Arduino first", Toast.LENGTH_SHORT).show();
            return;
        }
        setFan(true);
        BluetoothFanController.sendTimer(timerMinutes);
        if (tvTimerStartLabel != null) tvTimerStartLabel.setText("Cancel");

        FanTimerManager.start(
                this,
                timerMinutes,
                // onTick — Consumer<Integer> receives secondsRemaining
                secs -> runOnUiThread(() -> {
                    String t = FanTimerManager.formatTime(secs);
                    if (tvTimerDisplay    != null) tvTimerDisplay.setText(t);
                    if (tvOutdoorTimerEcho != null) tvOutdoorTimerEcho.setText(t);
                }),
                // onFinished — Runnable
                () -> runOnUiThread(() -> {
                    if (tvTimerDisplay    != null) tvTimerDisplay.setText("--:--");
                    if (tvOutdoorTimerEcho != null) tvOutdoorTimerEcho.setText("--:--");
                    if (tvTimerStartLabel != null) tvTimerStartLabel.setText("Start");
                    setFan(false);
                })
        );
    }

    private void cancelTimer() {
        FanTimerManager.cancel();
        BluetoothFanController.sendTimerCancel();
        if (tvTimerDisplay    != null) tvTimerDisplay.setText("--:--");
        if (tvOutdoorTimerEcho != null) tvOutdoorTimerEcho.setText("--:--");
        if (tvTimerStartLabel != null) tvTimerStartLabel.setText("Start");
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    private void setupMenu() {
        View menuContainer = findViewById(R.id.menu_container);
        if (menuContainer != null)
            menuContainer.setOnClickListener(this::showMenu);
    }

    private void showMenu(View v) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.getMenu().add("Bluetooth");
        menu.getMenu().add("Settings");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Bluetooth")) {
                startActivity(new Intent(this, ConnectionActivity.class));
                return true;
            } else if (title.equals("Settings")) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
    }

    // ── Unit toggle ───────────────────────────────────────────────────────────

    private void setupUnitToggle() {
        if (unitLabel != null)
            unitLabel.setOnClickListener(v -> {
                isFahrenheit = !isFahrenheit;
                unitLabel.setText(isFahrenheit ? "°F" : "°C");
                int display = isFahrenheit
                        ? (int)(lastTempC * 9 / 5 + 32) : (int) lastTempC;
                if (tempValue != null) tempValue.setText(String.valueOf(display));
                if (dialTicks != null) {
                    dialTicks.setRange(isFahrenheit ? 50 : 10, isFahrenheit ? 110 : 45);
                    dialTicks.setValue(display);
                }
            });
    }

    // ── Weather animation ─────────────────────────────────────────────────────

    private void updateWeatherVibe(float tempC) {
        if (weatherAnim == null) return;
        if      (tempC < 18) weatherAnim.setWeatherType("snow");
        else if (tempC < 26) weatherAnim.setWeatherType("leaves");
        else if (tempC < 32) weatherAnim.setWeatherType("rain");
        else                 weatherAnim.setWeatherType("sun");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setBtStatus(String text, int color) {
        if (tvBtStatus != null) {
            tvBtStatus.setText(text);
            tvBtStatus.setTextColor(color);
        }
    }
}