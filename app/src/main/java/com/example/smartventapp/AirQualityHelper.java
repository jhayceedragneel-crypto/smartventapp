package com.example.smartventapp;

import android.graphics.Color;
import android.widget.TextView;

/**
 * AirQualityHelper
 *
 * Determines the air quality badge color and label based on
 * temperature (°C) and humidity (%).
 *
 * Quality levels:
 *   🟢 GOOD    – temp ≤ 28°C  AND humidity ≤ 65%
 *   🟡 FAIR    – temp ≤ 35°C  AND humidity ≤ 80%
 *   🔴 POOR    – anything worse
 *
 * Usage in MainActivity (or any Activity):
 *
 *   TextView tvBadge = findViewById(R.id.tvAirQualityBadge);
 *   AirQualityHelper.applyBadge(tvBadge, tempCelsius, humidity);
 *
 * The TextView should have:
 *   android:id="@+id/tvAirQualityBadge"
 *   android:background="@drawable/badge_bg"   ← rounded rect drawable
 *   android:padding="6dp"
 */
public class AirQualityHelper {

    public enum Quality { GOOD, FAIR, POOR }

    public static Quality evaluate(double tempCelsius, int humidity) {
        if (tempCelsius <= 28 && humidity <= 65) return Quality.GOOD;
        if (tempCelsius <= 35 && humidity <= 80) return Quality.FAIR;
        return Quality.POOR;
    }

    /**
     * Applies badge text, text color, and background tint to the given TextView.
     */
    public static void applyBadge(TextView badge, double tempCelsius, int humidity) {
        if (badge == null) return;

        Quality q = evaluate(tempCelsius, humidity);

        switch (q) {
            case GOOD:
                badge.setText("● Good");
                badge.setTextColor(Color.parseColor("#00FF88"));
                badge.setBackgroundColor(Color.parseColor("#1A00FF88"));
                break;
            case FAIR:
                badge.setText("● Fair");
                badge.setTextColor(Color.parseColor("#FFCC00"));
                badge.setBackgroundColor(Color.parseColor("#1AFFCC00"));
                break;
            case POOR:
                badge.setText("● Poor");
                badge.setTextColor(Color.parseColor("#FF4444"));
                badge.setBackgroundColor(Color.parseColor("#1AFF4444"));
                break;
        }
    }

    /** Returns a plain string label, useful for notifications or logs. */
    public static String getLabel(double tempCelsius, int humidity) {
        switch (evaluate(tempCelsius, humidity)) {
            case GOOD: return "Good";
            case FAIR: return "Fair";
            default:   return "Poor";
        }
    }
}
