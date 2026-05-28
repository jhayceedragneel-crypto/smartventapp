package com.example.smartventapp;

import android.content.Context;
import android.os.CountDownTimer;
import com.google.firebase.database.FirebaseDatabase;

/**
 * FanTimerManager
 *
 * Manages a countdown timer that automatically turns the fan OFF
 * and sends a notification when the time runs out.
 *
 * Usage in MainActivity:
 *
 *   // Start a 30-minute timer
 *   FanTimerManager.start(context, 30, new FanTimerManager.TimerCallback() {
 *       public void onTick(int secondsRemaining) {
 *           tvTimerDisplay.setText(FanTimerManager.formatTime(secondsRemaining));
 *       }
 *       public void onFinished() {
 *           tvTimerDisplay.setText("--:--");
 *           switchFan.setChecked(false);
 *       }
 *   });
 *
 *   // Cancel the timer
 *   FanTimerManager.cancel();
 *
 *   // Check if running
 *   FanTimerManager.isRunning();
 */
public class FanTimerManager {

    public interface TimerCallback {
        void onTick(int secondsRemaining);
        void onFinished();
    }

    private static CountDownTimer activeTimer = null;
    private static boolean running = false;
    private static int secondsRemaining = 0;

    /**
     * Start the fan timer.
     * @param context  app context
     * @param minutes  how many minutes until fan turns OFF
     * @param callback UI update callbacks
     */
    public static void start(Context context, int minutes, TimerCallback callback) {
        // Cancel any existing timer first
        cancel();

        long totalMs = (long) minutes * 60 * 1000;
        running = true;

        activeTimer = new CountDownTimer(totalMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                secondsRemaining = (int)(millisUntilFinished / 1000);
                if (callback != null) callback.onTick(secondsRemaining);
            }

            @Override
            public void onFinish() {
                running = false;
                secondsRemaining = 0;

                // Turn fan OFF in Firebase
                FirebaseDatabase.getInstance()
                        .getReference("system/fan_status")
                        .setValue("OFF");

                // Send notification
                NotificationHelper.sendTimerNotification(context, minutes);

                if (callback != null) callback.onFinished();
            }
        }.start();
    }

    /** Cancel the running timer without triggering the finish action. */
    public static void cancel() {
        if (activeTimer != null) {
            activeTimer.cancel();
            activeTimer = null;
        }
        running = false;
        secondsRemaining = 0;
    }

    public static boolean isRunning() { return running; }

    public static int getSecondsRemaining() { return secondsRemaining; }

    /** Format seconds into "MM:SS" string for display. */
    public static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
