package com.example.smartventapp;

import android.content.Context;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FanUsageTracker
 *
 * Tracks how long the fan runs each day and estimates electricity cost in ₱.
 *
 * Firebase structure written:
 *   fan_usage/
 *     yyyy-MM-dd/
 *       sessions/
 *         session_<timestamp>/
 *           start_ms  : long
 *           end_ms    : long      (added when fan turns OFF)
 *           duration_minutes : int
 *       total_minutes_today : int
 *       estimated_cost_php  : double
 *
 * ── How to use ────────────────────────────────────────────────────────────────
 * In MainActivity, whenever fan_status changes in Firebase:
 *
 *   if ("ON".equals(status))  FanUsageTracker.onFanStarted();
 *   if ("OFF".equals(status)) FanUsageTracker.onFanStopped();
 *
 * To read today's usage for the StatsActivity / UI:
 *
 *   FanUsageTracker.getTodaySummary(new FanUsageTracker.UsageCallback() {
 *       public void onResult(int minutes, double costPhp) {
 *           tvHours.setText(String.format("%.1f hrs", minutes / 60.0));
 *           tvCost.setText(String.format("₱%.2f", costPhp));
 *       }
 *       public void onError(String msg) { ... }
 *   });
 *
 * ── Cost calculation ──────────────────────────────────────────────────────────
 * Default assumptions (adjustable via setWatts / setRatePerKwh):
 *   Fan power  : 60W  (typical ceiling/stand fan)
 *   Meralco rate: ₱10.00 / kWh  (adjust to your actual rate)
 *
 *   Cost = (watts / 1000) * hours * ratePerKwh
 */
public class FanUsageTracker {

    // ── Configurable constants ────────────────────────────────────────────────
    private static double FAN_WATTS      = 60.0;
    private static double RATE_PER_KWH   = 10.0;  // ₱ per kWh (Meralco avg)

    public static void setWatts(double watts)        { FAN_WATTS    = watts; }
    public static void setRatePerKwh(double rate)    { RATE_PER_KWH = rate;  }

    // ── Session key (in-memory, resets on process death) ─────────────────────
    private static String activeSessionKey = null;
    private static long   sessionStartMs   = 0;

    // ── Fan started ───────────────────────────────────────────────────────────

    public static void onFanStarted() {
        String today      = today();
        long   now        = System.currentTimeMillis();
        String sessionKey = "session_" + now;

        activeSessionKey = sessionKey;
        sessionStartMs   = now;

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("fan_usage/" + today + "/sessions/" + sessionKey);
        ref.child("start_ms").setValue(now);
    }

    // ── Fan stopped ───────────────────────────────────────────────────────────

    public static void onFanStopped() {
        if (activeSessionKey == null || sessionStartMs == 0) return;

        String today    = today();
        final long   now      = System.currentTimeMillis();
        int    minutes  = (int)((now - sessionStartMs) / 60_000);

        // Cap at 24 hours just in case
        minutes = Math.min(minutes, 1440);

        final String sessionKey = activeSessionKey;
        activeSessionKey  = null;
        sessionStartMs    = 0;

        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("fan_usage/" + today + "/sessions/" + sessionKey);
        sessionRef.child("end_ms").setValue(now);
        sessionRef.child("duration_minutes").setValue(minutes);

        // Update daily totals
        DatabaseReference dayRef = FirebaseDatabase.getInstance()
                .getReference("fan_usage/" + today);

        int finalMinutes = minutes;
        dayRef.child("total_minutes_today").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        int existing = snap.getValue(Integer.class) != null
                                ? snap.getValue(Integer.class) : 0;
                        int newTotal = existing + finalMinutes;
                        double hours    = newTotal / 60.0;
                        double costPhp  = (FAN_WATTS / 1000.0) * hours * RATE_PER_KWH;

                        dayRef.child("total_minutes_today").setValue(newTotal);
                        dayRef.child("estimated_cost_php").setValue(
                                Math.round(costPhp * 100.0) / 100.0);
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {}
                });
    }

    // ── Read today's summary ──────────────────────────────────────────────────

    public interface UsageCallback {
        void onResult(int totalMinutes, double estimatedCostPhp);
        void onError(String message);
    }

    public static void getTodaySummary(UsageCallback callback) {
        FirebaseDatabase.getInstance()
                .getReference("fan_usage/" + today())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        Integer mins  = snap.child("total_minutes_today").getValue(Integer.class);
                        Double  cost  = snap.child("estimated_cost_php").getValue(Double.class);

                        // Add live session if fan is currently running
                        int liveMins = 0;
                        if (sessionStartMs > 0) {
                            liveMins = (int)((System.currentTimeMillis() - sessionStartMs) / 60_000);
                        }

                        int    totalMins = (mins != null ? mins : 0) + liveMins;
                        double totalCost = cost != null ? cost : 0.0;

                        // Add live session cost estimate
                        if (liveMins > 0) {
                            totalCost += (FAN_WATTS / 1000.0) * (liveMins / 60.0) * RATE_PER_KWH;
                            totalCost  = Math.round(totalCost * 100.0) / 100.0;
                        }

                        callback.onResult(totalMins, totalCost);
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {
                        callback.onError("Firebase error: " + e.getMessage());
                    }
                });
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
