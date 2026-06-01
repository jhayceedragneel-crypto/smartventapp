package com.example.smartventapp;

import android.content.Context;
import android.os.CountDownTimer;

import java.util.function.Consumer;

/**
 * FanTimerManager
 *
 * Uses standard Java functional interfaces — no custom callback classes needed.
 *
 *   start(context, minutes, onTick, onFinished)
 *     Consumer<Integer> onTick      — called every second with secondsRemaining
 *     Runnable          onFinished  — called when countdown reaches zero
 */
public class FanTimerManager {

    private static CountDownTimer activeTimer = null;
    private static boolean        running     = false;
    private static int            secondsLeft = 0;

    public static void start(Context context,
                             int minutes,
                             Consumer<Integer> onTick,
                             Runnable onFinished) {
        cancel();

        long totalMs = (long) minutes * 60 * 1000;
        running = true;

        activeTimer = new CountDownTimer(totalMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                secondsLeft = (int)(millisUntilFinished / 1000);
                if (onTick != null) onTick.accept(secondsLeft);
            }
            @Override
            public void onFinish() {
                running     = false;
                secondsLeft = 0;
                NotificationHelper.sendTimerNotification(context, minutes);
                if (onFinished != null) onFinished.run();
            }
        }.start();
    }

    public static void cancel() {
        if (activeTimer != null) {
            activeTimer.cancel();
            activeTimer = null;
        }
        running     = false;
        secondsLeft = 0;
    }

    public static boolean isRunning()           { return running; }
    public static int     getSecondsRemaining() { return secondsLeft; }

    public static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}