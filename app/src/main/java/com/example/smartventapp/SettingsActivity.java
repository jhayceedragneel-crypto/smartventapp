package com.example.smartventapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * SettingsActivity
 *
 * Layout file required: res/layout/activity_settings.xml
 * See the XML template at the bottom of this file (as a comment).
 *
 * IDs expected in layout:
 *   btnBackContainer        – back button container
 *   switchNotifications     – SwitchCompat  (enable/disable alerts)
 *   switchFahrenheit        – SwitchCompat  (°C / °F)
 *   seekbarTempThreshold    – SeekBar  0-50 → mapped to 20-50 °C
 *   tvTempThresholdValue    – TextView showing current threshold
 *   seekbarHumThreshold     – SeekBar  0-60 → mapped to 40-100 %
 *   tvHumThresholdValue     – TextView showing current threshold
 */
public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    // SeekBar ranges
    private static final int TEMP_MIN = 20;   // °C
    private static final int TEMP_MAX = 50;
    private static final int HUM_MIN  = 40;   // %
    private static final int HUM_MAX  = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        prefs = getSharedPreferences(NotificationHelper.PREFS_NAME, MODE_PRIVATE);

        // Back
        if (findViewById(R.id.btnBackContainer) != null) {
            findViewById(R.id.btnBackContainer).setOnClickListener(v -> finish());
        }

        setupNotificationSwitch();
        setupFahrenheitSwitch();
        setupTempThreshold();
        setupHumThreshold();
    }

    // ── Notifications toggle ──────────────────────────────────────────────────

    private void setupNotificationSwitch() {
        SwitchCompat sw = findViewById(R.id.switchNotifications);
        if (sw == null) return;
        sw.setChecked(prefs.getBoolean(NotificationHelper.KEY_NOTIF_ENABLED,
                NotificationHelper.DEFAULT_NOTIF_ENABLED));
        sw.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(NotificationHelper.KEY_NOTIF_ENABLED, checked).apply());
    }

    // ── °C / °F toggle ───────────────────────────────────────────────────────

    private void setupFahrenheitSwitch() {
        SwitchCompat sw = findViewById(R.id.switchFahrenheit);
        if (sw == null) return;
        sw.setChecked(prefs.getBoolean(NotificationHelper.KEY_USE_FAHRENHEIT, false));
        sw.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(NotificationHelper.KEY_USE_FAHRENHEIT, checked).apply());
    }

    // ── Temperature threshold ─────────────────────────────────────────────────

    private void setupTempThreshold() {
        SeekBar seekBar = findViewById(R.id.seekbarTempThreshold);
        TextView tvVal  = findViewById(R.id.tvTempThresholdValue);
        if (seekBar == null || tvVal == null) return;

        int saved = prefs.getInt(NotificationHelper.KEY_TEMP_THRESHOLD,
                NotificationHelper.DEFAULT_TEMP_THRESHOLD);
        seekBar.setMax(TEMP_MAX - TEMP_MIN);
        seekBar.setProgress(saved - TEMP_MIN);
        tvVal.setText(saved + "°C");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = TEMP_MIN + progress;
                tvVal.setText(val + "°C");
                if (fromUser)
                    prefs.edit().putInt(NotificationHelper.KEY_TEMP_THRESHOLD, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // ── Humidity threshold ────────────────────────────────────────────────────

    private void setupHumThreshold() {
        SeekBar seekBar = findViewById(R.id.seekbarHumThreshold);
        TextView tvVal  = findViewById(R.id.tvHumThresholdValue);
        if (seekBar == null || tvVal == null) return;

        int saved = prefs.getInt(NotificationHelper.KEY_HUM_THRESHOLD,
                NotificationHelper.DEFAULT_HUM_THRESHOLD);
        seekBar.setMax(HUM_MAX - HUM_MIN);
        seekBar.setProgress(saved - HUM_MIN);
        tvVal.setText(saved + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = HUM_MIN + progress;
                tvVal.setText(val + "%");
                if (fromUser)
                    prefs.edit().putInt(NotificationHelper.KEY_HUM_THRESHOLD, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }
}