package com.example.smartventapp;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WeeklySummaryHelper
 *
 * Reads the last 7 days of weather data from Firebase and returns:
 *   - Average temperature (°C)
 *   - Average humidity (%)
 *   - Hottest day label + temperature
 *   - Most humid day label + humidity
 *
 * Usage:
 *   WeeklySummaryHelper.load(new WeeklySummaryHelper.Callback() {
 *       public void onResult(Summary s) {
 *           tvAvgTemp.setText((int)s.avgTempC + "°C");
 *           tvAvgHum.setText(s.avgHumidity + "%");
 *           tvHottestDay.setText(s.hottestDay + " — " + (int)s.hottestTemp + "°C");
 *       }
 *       public void onError(String msg) { ... }
 *   });
 */
public class WeeklySummaryHelper {

    public interface Callback {
        void onResult(Summary summary);
        void onError(String message);
    }

    // ── Result container ──────────────────────────────────────────────────────

    public static class Summary {
        public double avgTempC;
        public int    avgHumidity;
        public String hottestDay;
        public double hottestTemp;
        public String mostHumidDay;
        public int    mostHumidHum;
        public int    totalReadings;
        /** All daily averages in date order (for chart use) */
        public List<DailyAvg> dailyAverages = new ArrayList<>();
    }

    public static class DailyAvg {
        public String date;        // "yyyy-MM-dd"
        public String label;       // "Mon", "Tue", …
        public double avgTempC;
        public int    avgHumidity;
    }

    // ── Main loader ───────────────────────────────────────────────────────────

    public static void load(Callback callback) {
        List<String> last7 = getLast7Dates();

        FirebaseDatabase.getInstance().getReference("weather")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Summary summary = new Summary();

                        double totalTemp = 0;
                        int    totalHum  = 0;
                        int    count     = 0;

                        double maxTemp   = Double.MIN_VALUE;
                        int    maxHum    = Integer.MIN_VALUE;

                        for (String date : last7) {
                            DataSnapshot dateSnap = snapshot.child(date);
                            if (!dateSnap.exists()) continue;

                            double dayTempSum = 0;
                            int    dayHumSum  = 0;
                            int    dayCount   = 0;

                            for (DataSnapshot timeSnap : dateSnap.getChildren()) {
                                Double temp = timeSnap.child("temp_celsius").getValue(Double.class);
                                Integer hum = timeSnap.child("humidity").getValue(Integer.class);
                                if (temp != null && hum != null) {
                                    dayTempSum += temp;
                                    dayHumSum  += hum;
                                    dayCount++;
                                }
                            }

                            if (dayCount == 0) continue;

                            double dayAvgTemp = dayTempSum / dayCount;
                            int    dayAvgHum  = dayHumSum  / dayCount;

                            totalTemp += dayAvgTemp;
                            totalHum  += dayAvgHum;
                            count++;

                            if (dayAvgTemp > maxTemp) {
                                maxTemp = dayAvgTemp;
                                summary.hottestTemp = dayAvgTemp;
                                summary.hottestDay  = formatDayLabel(date);
                            }

                            if (dayAvgHum > maxHum) {
                                maxHum = dayAvgHum;
                                summary.mostHumidHum = dayAvgHum;
                                summary.mostHumidDay = formatDayLabel(date);
                            }

                            DailyAvg da = new DailyAvg();
                            da.date       = date;
                            da.label      = getDayShort(date);
                            da.avgTempC   = dayAvgTemp;
                            da.avgHumidity = dayAvgHum;
                            summary.dailyAverages.add(da);
                        }

                        if (count == 0) {
                            callback.onError("No data for the last 7 days");
                            return;
                        }

                        summary.avgTempC    = totalTemp / count;
                        summary.avgHumidity = totalHum  / count;
                        summary.totalReadings = count;

                        callback.onResult(summary);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onError("Firebase error: " + error.getMessage());
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<String> getLast7Dates() {
        List<String> dates = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            dates.add(sdf.format(c.getTime()));
        }
        return dates;
    }

    /** "2026-05-18" → "May 18" */
    private static String formatDayLabel(String date) {
        try {
            String[] parts = date.split("-");
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                    "Jul","Aug","Sep","Oct","Nov","Dec"};
            int m = Integer.parseInt(parts[1]) - 1;
            return months[m] + " " + Integer.parseInt(parts[2]);
        } catch (Exception e) { return date; }
    }

    /** "2026-05-18" → "Mon" */
    private static String getDayShort(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = sdf.parse(date);
            SimpleDateFormat dow = new SimpleDateFormat("EEE", Locale.getDefault());
            return d != null ? dow.format(d) : date;
        } catch (Exception e) { return date; }
    }
}
