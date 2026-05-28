package com.example.smartventapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private String currentMode = "Cooling";
    private String currentView = "Dashboard";
    private int temperature = 26;
    private int humidity    = 56;
    private boolean isFahrenheit = false;

    private double indoorTempC  = 26.0;
    private int    indoorHum    = 56;
    private double outdoorTempC = 26.0;
    private int    outdoorHum   = 56;

    // Track fan state to call FanUsageTracker correctly
    private boolean fanIsOn = false;

    private WeatherAnimationView weatherAnim;
    private View rootLayout;
    private TextView tempValue, unitLabel, humidityValue, menuName;
    private DialTicksView dialTicks;

    // Medium feature views
    private TextView tvAirQualityBadge;
    private TextView tvIndoorTemp, tvIndoorHum, tvOutdoorTemp, tvOutdoorHum;

    private int timerMinutes = 30;   // default timer duration
    private TextView tvTimerDisplay;
    private TextView tvTimerMinutes;
    private TextView tvTimerStartLabel;

    private Map<String, Integer> menuSettings = new HashMap<>();
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupListeners();
        setupTimer();
        listenToFanStatus();    // Advanced: fan usage tracking
        listenToIndoorSensor(); // Medium: indoor card

        Handler handler = new Handler();
        Runnable weatherUpdater = new Runnable() {
            @Override public void run() {
                WeatherManager.fetchAndSave(MainActivity.this);
                handler.postDelayed(this, 8 * 60 * 60 * 1000);
            }
        };
        handler.post(weatherUpdater);

        new Handler().postDelayed(this::setupFirebaseSync, 3000);
    }

    private void initializeUI() {
        rootLayout    = findViewById(R.id.activity_main_root);
        weatherAnim   = findViewById(R.id.weather_anim);
        tempValue     = findViewById(R.id.temp_value);
        unitLabel     = findViewById(R.id.unit_label);
        humidityValue = findViewById(R.id.humidity_value);
        menuName      = findViewById(R.id.menu_name);
        dialTicks     = findViewById(R.id.dial_ticks);

        tvAirQualityBadge = findViewById(R.id.tvAirQualityBadge);
        tvIndoorTemp      = findViewById(R.id.tvIndoorTemp);
        tvIndoorHum       = findViewById(R.id.tvIndoorHum);
        tvOutdoorTemp     = findViewById(R.id.tvOutdoorTemp);
        tvOutdoorHum      = findViewById(R.id.tvOutdoorHum);

        menuSettings.put("Dashboard",   26);
        menuSettings.put("Performance", 22);
        menuSettings.put("History",     24);

        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
        }
    }

    private void setupListeners() {
        SwitchCompat offOnBtn = findViewById(R.id.offonbtn);

        if (findViewById(R.id.menu_container) != null)
            findViewById(R.id.menu_container).setOnClickListener(this::showMenu);

        if (unitLabel != null)
            unitLabel.setOnClickListener(v -> toggleUnits());

        if (dialTicks != null) {
            dialTicks.setOnValueChangeListener(value -> {
                temperature = value;
                if (tempValue != null) tempValue.setText(String.valueOf(temperature));
                updateWeatherVibe();
                mDatabase.child("system").child("target_temp").setValue(temperature);
            });
        }

        if (findViewById(R.id.mode_cooling) != null)
            findViewById(R.id.mode_cooling).setOnClickListener(v -> updateMode("Cooling"));
        if (findViewById(R.id.mode_turbo) != null)
            findViewById(R.id.mode_turbo).setOnClickListener(v -> updateMode("Turbo"));
        

        if (offOnBtn != null) {
            offOnBtn.setOnCheckedChangeListener((btn, isChecked) -> {
                String status = isChecked ? "ON" : "OFF";
                mDatabase.child("system").child("fan_status").setValue(status);
                // Manual toggle also tracked
                handleFanStatusChange(status);
            });
        }

       }

    // ── Advanced: listen to fan_status node for usage tracking ───────────────
    private void listenToFanStatus() {
        mDatabase.child("system").child("fan_status")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        String status = snap.getValue(String.class);
                        if (status != null) handleFanStatusChange(status);
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    private void handleFanStatusChange(String status) {
        boolean newOn = "ON".equals(status);
        if (newOn && !fanIsOn) {
            FanUsageTracker.onFanStarted();
        } else if (!newOn && fanIsOn) {
            FanUsageTracker.onFanStopped();
        }
        if (FanTimerManager.isRunning()) cancelTimer();
        fanIsOn = newOn;

        // Sync the switch UI to reflect Firebase state
        SwitchCompat sw = findViewById(R.id.offonbtn);
        if (sw != null && sw.isChecked() != newOn) {
            sw.setOnCheckedChangeListener(null); // avoid feedback loop
            sw.setChecked(newOn);
            sw.setOnCheckedChangeListener((btn, isChecked) -> {
                String s = isChecked ? "ON" : "OFF";
                mDatabase.child("system").child("fan_status").setValue(s);
                handleFanStatusChange(s);
            });
        }
    }

    // ── Outdoor weather sync ──────────────────────────────────────────────────
    private void setupFirebaseSync() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String timeLabel = (hour >= 5 && hour <= 12) ? "Morning"
                : (hour >= 13 && hour <= 17) ? "Afternoon" : "Evening";

        mDatabase.child("weather/" + today + "/" + timeLabel)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Double temp = snapshot.child("temp_celsius").getValue(Double.class);
                        Integer hum = snapshot.child("humidity").getValue(Integer.class);

                        if (temp != null) {
                            outdoorTempC = temp;
                            temperature  = isFahrenheit ? (int)(temp * 9 / 5 + 32) : temp.intValue();
                            if (tempValue != null) tempValue.setText(String.valueOf(temperature));
                        }
                        if (hum != null) {
                            outdoorHum = hum;
                            humidity   = hum;
                            if (humidityValue != null) humidityValue.setText(humidity + "%");
                        }

                        updateWeatherVibe();
                        refreshAirQualityBadge();
                        refreshTempCards();

                        // Medium: notification check
                        NotificationHelper.checkAndNotify(MainActivity.this, outdoorTempC, outdoorHum);
                        // Advanced: auto fan logic
                        AutoFanManager.evaluate(MainActivity.this, outdoorTempC, outdoorHum);
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    // ── Indoor sensor ─────────────────────────────────────────────────────────
    private void listenToIndoorSensor() {
        mDatabase.child("indoor").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Double temp = snapshot.child("temp_celsius").getValue(Double.class);
                Integer hum = snapshot.child("humidity").getValue(Integer.class);
                if (temp != null) indoorTempC = temp;
                if (hum  != null) indoorHum   = hum;

                refreshTempCards();
                refreshAirQualityBadge();

                NotificationHelper.checkAndNotify(MainActivity.this, indoorTempC, indoorHum);
                AutoFanManager.evaluate(MainActivity.this, indoorTempC, indoorHum);
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    // ── Air quality badge ─────────────────────────────────────────────────────
    private void refreshAirQualityBadge() {
        double avgTemp = (indoorTempC + outdoorTempC) / 2.0;
        int    avgHum  = (indoorHum + outdoorHum) / 2;
        AirQualityHelper.applyBadge(tvAirQualityBadge, avgTemp, avgHum);
    }

    // ── Indoor / Outdoor cards ────────────────────────────────────────────────
    private void refreshTempCards() {
        if (tvIndoorTemp != null) {
            int d = isFahrenheit ? (int)(indoorTempC * 9 / 5 + 32) : (int) indoorTempC;
            tvIndoorTemp.setText(d + (isFahrenheit ? "°F" : "°C"));
        }
        if (tvIndoorHum  != null) tvIndoorHum.setText(indoorHum + "%");
        if (tvOutdoorTemp != null) {
            int d = isFahrenheit ? (int)(outdoorTempC * 9 / 5 + 32) : (int) outdoorTempC;
            tvOutdoorTemp.setText(d + (isFahrenheit ? "°F" : "°C"));
        }
        if (tvOutdoorHum != null) tvOutdoorHum.setText(outdoorHum + "%");
    }

    // ── Menu ──────────────────────────────────────────────────────────────────
    private void showMenu(View v) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.getMenu().add("Dashboard");
        menu.getMenu().add("Performance");
        menu.getMenu().add("History");
        menu.getMenu().add("Settings");
        menu.getMenu().add("Stats");          // Advanced: new screen

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "History":
                    startActivity(new Intent(this, HistoryActivity.class)); return true;
                case "Performance":
                    startActivity(new Intent(this, PerformanceActivity.class)); return true;
                case "Settings":
                    startActivity(new Intent(this, SettingsActivity.class)); return true;
                case "Stats":
                    startActivity(new Intent(this, StatsActivity.class)); return true;  // Advanced
                default:
                    currentView = item.getTitle().toString();
                    if (menuName != null) menuName.setText(currentView);
                    Integer saved = menuSettings.get(currentView);
                    if (saved != null) {
                        temperature = saved;
                        if (tempValue != null) tempValue.setText(String.valueOf(temperature));
                        if (dialTicks  != null) dialTicks.setValue(temperature);
                    }
                    updateWeatherVibe();
                    return true;
            }
        });
        menu.show();
    }

    private void setupTimer() {
        tvTimerDisplay   = findViewById(R.id.tvTimerDisplay);
        tvTimerMinutes   = findViewById(R.id.tvTimerMinutes);
        tvTimerStartLabel = findViewById(R.id.tvTimerStartLabel);

        if (tvTimerMinutes != null) tvTimerMinutes.setText(timerMinutes + " min");

        // Minus button
        if (findViewById(R.id.btnTimerMinus) != null) {
            findViewById(R.id.btnTimerMinus).setOnClickListener(v -> {
                if (FanTimerManager.isRunning()) return; // can't adjust while running
                if (timerMinutes > 5) timerMinutes -= 5;
                if (tvTimerMinutes != null) tvTimerMinutes.setText(String.valueOf(timerMinutes));
            });
        }

        // Plus button
        if (findViewById(R.id.btnTimerPlus) != null) {
            findViewById(R.id.btnTimerPlus).setOnClickListener(v -> {
                if (FanTimerManager.isRunning()) return;
                if (timerMinutes < 180) timerMinutes += 5;
                if (tvTimerMinutes != null) tvTimerMinutes.setText(String.valueOf(timerMinutes));
            });
        }

        // Start / Cancel button
        if (findViewById(R.id.btnTimerStart) != null) {
            findViewById(R.id.btnTimerStart).setOnClickListener(v -> {
                if (FanTimerManager.isRunning()) {
                    cancelTimer();
                } else {
                    startTimer();
                }
            });
        }
    }

    private void startTimer() {
        // Make sure fan is ON before starting timer
        SwitchCompat sw = findViewById(R.id.offonbtn);
        if (sw != null && !sw.isChecked()) {
            sw.setChecked(true); // this triggers handleFanStatusChange via listener
        }

        if (tvTimerStartLabel != null) tvTimerStartLabel.setText("Cancel");

        FanTimerManager.start(this, timerMinutes, new FanTimerManager.TimerCallback() {
            @Override
            public void onTick(int secondsRemaining) {
                runOnUiThread(() -> {
                    if (tvTimerDisplay != null)
                        tvTimerDisplay.setText(FanTimerManager.formatTime(secondsRemaining));
                });
            }

            @Override
            public void onFinished() {
                runOnUiThread(() -> {
                    if (tvTimerDisplay   != null) tvTimerDisplay.setText("--:--");
                    if (tvTimerStartLabel != null) tvTimerStartLabel.setText("Start");

                    // Sync switch OFF
                    SwitchCompat sw = findViewById(R.id.offonbtn);
                    if (sw != null) {
                        sw.setOnCheckedChangeListener(null);
                        sw.setChecked(false);
                        sw.setOnCheckedChangeListener((btn, isChecked) -> {
                            String s = isChecked ? "ON" : "OFF";
                            mDatabase.child("system").child("fan_status").setValue(s);
                            handleFanStatusChange(s);
                        });
                    }
                });
            }
        });
    }

    private void cancelTimer() {
        FanTimerManager.cancel();
        if (tvTimerDisplay   != null) tvTimerDisplay.setText("--:--");
        if (tvTimerStartLabel != null) tvTimerStartLabel.setText("Start");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void toggleUnits() {
        isFahrenheit = !isFahrenheit;
        if (isFahrenheit) {
            temperature = (int)(temperature * 9 / 5.0 + 32);
            if (unitLabel != null) unitLabel.setText("°F");
            if (dialTicks != null) dialTicks.setRange(50, 100);
        } else {
            temperature = (int)((temperature - 32) * 5 / 9.0);
            if (unitLabel != null) unitLabel.setText("°C");
            if (dialTicks != null) dialTicks.setRange(10, 40);
        }
        if (tempValue != null) tempValue.setText(String.valueOf(temperature));
        if (dialTicks  != null) dialTicks.setValue(temperature);
        refreshTempCards();
        WeatherManager.fetchAndSave(this);
    }

    private void updateWeatherVibe() {
        if (rootLayout == null || weatherAnim == null) return;
        int f = isFahrenheit ? temperature : (int)(temperature * 9 / 5.0 + 32);
        if      (f < 60) { rootLayout.setBackgroundResource(R.drawable.winter_bg); weatherAnim.setWeatherType("snow"); }
        else if (f < 75) { rootLayout.setBackgroundResource(R.drawable.autumn_bg); weatherAnim.setWeatherType("leaves"); }
        else if (f < 85) { rootLayout.setBackgroundResource(R.drawable.weather_bg); weatherAnim.setWeatherType("rain"); }
        else             { rootLayout.setBackgroundResource(R.drawable.summer_bg);  weatherAnim.setWeatherType("sun"); }
    }

    private void updateMode(String mode) {
        currentMode = mode;
        if ("Turbo".equals(mode)) {
            temperature = isFahrenheit ? 90 : 32;
            if (tempValue != null) tempValue.setText(String.valueOf(temperature));
            if (dialTicks  != null) dialTicks.setValue(temperature);
        }
        updateWeatherVibe();
        mDatabase.child("system").child("operating_mode").setValue(mode);
    }
}
