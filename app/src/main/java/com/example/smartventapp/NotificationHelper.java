package com.example.smartventapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID   = "smartvent_alerts";
    private static final String CHANNEL_NAME = "SmartVent Alerts";
    private static final int    NOTIF_TEMP_ID = 1001;
    private static final int    NOTIF_HUM_ID  = 1002;

    // SharedPreferences keys (shared with SettingsActivity)
    public static final String PREFS_NAME          = "smartvent_prefs";
    public static final String KEY_NOTIF_ENABLED   = "notif_enabled";
    public static final String KEY_TEMP_THRESHOLD  = "temp_threshold";   // °C
    public static final String KEY_HUM_THRESHOLD   = "hum_threshold";    // %
    public static final String KEY_USE_FAHRENHEIT  = "use_fahrenheit";

    // Defaults
    public static final boolean DEFAULT_NOTIF_ENABLED  = true;
    public static final int     DEFAULT_TEMP_THRESHOLD = 35;  // °C
    public static final int     DEFAULT_HUM_THRESHOLD  = 80;  // %

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Call this whenever fresh sensor/weather data arrives.
     * @param tempCelsius  current temperature in Celsius
     * @param humidity     current humidity in %
     */
    public static void checkAndNotify(Context context, double tempCelsius, int humidity) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean enabled      = prefs.getBoolean(KEY_NOTIF_ENABLED,  DEFAULT_NOTIF_ENABLED);
        int     tempThresh   = prefs.getInt(KEY_TEMP_THRESHOLD, DEFAULT_TEMP_THRESHOLD);
        int     humThresh    = prefs.getInt(KEY_HUM_THRESHOLD,  DEFAULT_HUM_THRESHOLD);
        boolean useFahr      = prefs.getBoolean(KEY_USE_FAHRENHEIT, false);

        if (!enabled) return;

        createChannel(context);

        // Temperature alert
        if (tempCelsius >= tempThresh) {
            String displayTemp = useFahr
                    ? (int)(tempCelsius * 9 / 5 + 32) + "°F"
                    : (int) tempCelsius + "°C";
            sendNotification(context,
                    NOTIF_TEMP_ID,
                    "🌡️ High Temperature Alert",
                    "Current temp is " + displayTemp + " — consider turning on the fan.");
        }

        // Humidity alert
        if (humidity >= humThresh) {
            sendNotification(context,
                    NOTIF_HUM_ID,
                    "💧 High Humidity Alert",
                    "Humidity is at " + humidity + "% — ventilation recommended.");
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts when temperature or humidity exceeds set thresholds");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private static void sendNotification(Context context, int id, String title, String text) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pendingIntent = PendingIntent.getActivity(context, id, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, builder.build());
    }

    private static final int NOTIF_TIMER_ID = 1003;

    /**
     * Called by FanTimerManager when the countdown finishes.
     */
    public static void sendTimerNotification(Context context, int minutes) {
        createChannel(context);
        sendNotification(context,
                NOTIF_TIMER_ID,
                "⏱ Fan Timer Ended",
                "Your " + minutes + "-minute fan timer has ended. Fan has been turned off.");
    }

}
