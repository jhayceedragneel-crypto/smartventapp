package com.example.smartventapp;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * AutoFanManager
 *
 * Automatically turns the fan ON or OFF in Firebase based on
 * temperature and humidity thresholds stored in SharedPreferences
 * (same prefs used by SettingsActivity / NotificationHelper).
 *
 * Call AutoFanManager.evaluate(context, tempCelsius, humidity)
 * every time fresh sensor data arrives in MainActivity.
 *
 * Firebase node written: "system/fan_status"  →  "ON" | "OFF"
 * Firebase node written: "system/auto_fan_reason" → human-readable string
 *
 * A new SharedPreferences key KEY_AUTO_FAN_ENABLED lets the user
 * toggle this feature from SettingsActivity (wire up the switch
 * with id="switchAutoFan").
 */
public class AutoFanManager {

    public static final String KEY_AUTO_FAN_ENABLED = "auto_fan_enabled";
    public static final boolean DEFAULT_AUTO_FAN    = true;

    // Hysteresis: fan turns ON at threshold, turns OFF 3° below to prevent rapid cycling
    private static final int HYSTERESIS_TEMP = 3;   // °C
    private static final int HYSTERESIS_HUM  = 5;   // %

    /**
     * Evaluate current conditions and set fan status accordingly.
     *
     * @param tempCelsius latest temperature reading in °C
     * @param humidity    latest humidity reading in %
     */
    public static void evaluate(Context context, double tempCelsius, int humidity) {
        SharedPreferences prefs = context.getSharedPreferences(
                NotificationHelper.PREFS_NAME, Context.MODE_PRIVATE);

        boolean autoEnabled = prefs.getBoolean(KEY_AUTO_FAN_ENABLED, DEFAULT_AUTO_FAN);
        if (!autoEnabled) return;

        int tempThresh = prefs.getInt(NotificationHelper.KEY_TEMP_THRESHOLD,
                NotificationHelper.DEFAULT_TEMP_THRESHOLD);
        int humThresh  = prefs.getInt(NotificationHelper.KEY_HUM_THRESHOLD,
                NotificationHelper.DEFAULT_HUM_THRESHOLD);

        DatabaseReference sysRef = FirebaseDatabase.getInstance().getReference("system");

        boolean tooHot  = tempCelsius >= tempThresh;
        boolean tooHumid = humidity   >= humThresh;
        boolean coolEnough = tempCelsius < (tempThresh - HYSTERESIS_TEMP);
        boolean dryEnough  = humidity   < (humThresh  - HYSTERESIS_HUM);

        if (tooHot || tooHumid) {
            // Trigger fan ON
            sysRef.child("fan_status").setValue("ON");

            String reason = "";
            if (tooHot && tooHumid) {
                reason = "Auto: temp " + (int)tempCelsius + "°C & humidity " + humidity + "% exceeded";
            } else if (tooHot) {
                reason = "Auto: temp " + (int)tempCelsius + "°C exceeded " + tempThresh + "°C";
            } else {
                reason = "Auto: humidity " + humidity + "% exceeded " + humThresh + "%";
            }
            sysRef.child("auto_fan_reason").setValue(reason);
            sysRef.child("auto_fan_active").setValue(true);

        } else if (coolEnough && dryEnough) {
            // Conditions comfortable — turn fan OFF (only if it was auto-triggered)
            sysRef.child("auto_fan_active").addListenerForSingleValueEvent(
                    new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(com.google.firebase.database.DataSnapshot snap) {
                            Boolean autoActive = snap.getValue(Boolean.class);
                            if (Boolean.TRUE.equals(autoActive)) {
                                sysRef.child("fan_status").setValue("OFF");
                                sysRef.child("auto_fan_reason").setValue(
                                        "Auto: conditions normal (" + (int)tempCelsius + "°C, " + humidity + "%)");
                                sysRef.child("auto_fan_active").setValue(false);
                            }
                        }
                        @Override
                        public void onCancelled(com.google.firebase.database.DatabaseError e) {}
                    });
        }
    }
}
