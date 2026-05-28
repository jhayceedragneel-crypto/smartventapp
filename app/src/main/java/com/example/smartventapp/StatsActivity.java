package com.example.smartventapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * StatsActivity — 7-day chart screen (Feature 11)
 *
 * Shows:
 *  - 7-day temperature line chart
 *  - 7-day humidity line chart
 *  - Weekly summary cards (avg temp, avg humidity, hottest day)
 *  - Fan usage today (hours + ₱ cost)
 *
 * ── Dependencies ──────────────────────────────────────────────────────────────
 * Add to app/build.gradle:
 *   implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
 *
 * Add to settings.gradle (if not present):
 *   maven { url 'https://jitpack.io' }
 *
 * ── Layout file needed: res/layout/activity_stats.xml ────────────────────────
 * See XML template at the bottom of this file.
 *
 * ── Register in AndroidManifest.xml ──────────────────────────────────────────
 * <activity android:name=".StatsActivity"/>
 */
public class StatsActivity extends AppCompatActivity {

    private LineChart chartTemp;
    private LineChart chartHumidity;
    private TextView tvAvgTemp, tvAvgHum, tvHottestDay, tvFanHours, tvFanCost;
    private TextView tvLoading, tvAutoFanReason;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stats);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.activity_stats_root), (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                    return insets;
                });

        // Views
        chartTemp      = findViewById(R.id.chartTemp);
        chartHumidity  = findViewById(R.id.chartHumidity);
        tvAvgTemp      = findViewById(R.id.tvAvgTemp);
        tvAvgHum       = findViewById(R.id.tvAvgHum);
        tvHottestDay   = findViewById(R.id.tvHottestDay);
        tvFanHours     = findViewById(R.id.tvFanHours);
        tvFanCost      = findViewById(R.id.tvFanCost);
        tvLoading      = findViewById(R.id.tvLoading);
        tvAutoFanReason = findViewById(R.id.tvAutoFanReason);

        // Back
        if (findViewById(R.id.btnBackContainer) != null)
            findViewById(R.id.btnBackContainer).setOnClickListener(v -> finish());

        // Menu
        if (findViewById(R.id.menu_container) != null)
            findViewById(R.id.menu_container).setOnClickListener(this::showMenu);

        // Weather animation
        WeatherAnimationView anim = findViewById(R.id.weather_anim);
        if (anim != null) anim.setWeatherType("sun");

        setupChartStyle(chartTemp);
        setupChartStyle(chartHumidity);

        loadData();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        if (tvLoading != null) tvLoading.setVisibility(View.VISIBLE);

        // Weekly summary
        WeeklySummaryHelper.load(new WeeklySummaryHelper.Callback() {
            @Override
            public void onResult(WeeklySummaryHelper.Summary s) {
                if (tvLoading != null) tvLoading.setVisibility(View.GONE);

                // Summary cards
                if (tvAvgTemp != null)
                    tvAvgTemp.setText((int) Math.round(s.avgTempC) + "°C");
                if (tvAvgHum != null)
                    tvAvgHum.setText(s.avgHumidity + "%");
                if (tvHottestDay != null)
                    tvHottestDay.setText(s.hottestDay + "  " + (int) Math.round(s.hottestTemp) + "°C");

                // Build chart datasets
                buildCharts(s.dailyAverages);
            }

            @Override
            public void onError(String msg) {
                if (tvLoading != null) {
                    tvLoading.setText("No data available");
                    tvLoading.setVisibility(View.VISIBLE);
                }
            }
        });

        // Fan usage
        FanUsageTracker.getTodaySummary(new FanUsageTracker.UsageCallback() {
            @Override
            public void onResult(int minutes, double costPhp) {
                runOnUiThread(() -> {
                    if (tvFanHours != null)
                        tvFanHours.setText(String.format("%.1f hrs", minutes / 60.0));
                    if (tvFanCost != null)
                        tvFanCost.setText(String.format("₱%.2f", costPhp));
                });
            }
            @Override
            public void onError(String message) {}
        });


        // Auto fan reason
        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("system/auto_fan_reason")
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snap) {
                        String reason = snap.getValue(String.class);
                        if (tvAutoFanReason != null && reason != null) {
                            tvAutoFanReason.setText(reason);
                            tvAutoFanReason.setVisibility(View.VISIBLE);
                        }
                    }
                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError e) {}
                });
    }

    // ── Chart building ────────────────────────────────────────────────────────

    private void buildCharts(List<WeeklySummaryHelper.DailyAvg> days) {
        if (days.isEmpty()) return;

        List<Entry> tempEntries = new ArrayList<>();
        List<Entry> humEntries  = new ArrayList<>();
        String[]    labels      = new String[days.size()];

        for (int i = 0; i < days.size(); i++) {
            WeeklySummaryHelper.DailyAvg d = days.get(i);
            tempEntries.add(new Entry(i, (float) d.avgTempC));
            humEntries.add(new Entry(i, d.avgHumidity));
            labels[i] = d.label;
        }

        // Temperature chart
        LineDataSet tempSet = new LineDataSet(tempEntries, "Avg Temp (°C)");
        tempSet.setColor(Color.parseColor("#FFEE00"));
        tempSet.setCircleColor(Color.parseColor("#FFEE00"));
        tempSet.setLineWidth(2.5f);
        tempSet.setCircleRadius(4f);
        tempSet.setDrawValues(true);
        tempSet.setValueTextColor(Color.parseColor("#AAAAAA"));
        tempSet.setValueTextSize(10f);
        tempSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        tempSet.setDrawFilled(true);
        tempSet.setFillColor(Color.parseColor("#FFEE00"));
        tempSet.setFillAlpha(30);

        chartTemp.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chartTemp.setData(new LineData(tempSet));
        chartTemp.invalidate();

        // Humidity chart
        LineDataSet humSet = new LineDataSet(humEntries, "Avg Humidity (%)");
        humSet.setColor(Color.parseColor("#00E5FF"));
        humSet.setCircleColor(Color.parseColor("#00E5FF"));
        humSet.setLineWidth(2.5f);
        humSet.setCircleRadius(4f);
        humSet.setDrawValues(true);
        humSet.setValueTextColor(Color.parseColor("#AAAAAA"));
        humSet.setValueTextSize(10f);
        humSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        humSet.setDrawFilled(true);
        humSet.setFillColor(Color.parseColor("#00E5FF"));
        humSet.setFillAlpha(30);

        chartHumidity.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chartHumidity.setData(new LineData(humSet));
        chartHumidity.invalidate();
    }

    // ── Chart style ───────────────────────────────────────────────────────────

    private void setupChartStyle(LineChart chart) {
        if (chart == null) return;

        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.parseColor("#AAAAAA"));
        chart.getLegend().setTextSize(11f);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setNoDataText("Loading...");
        chart.setNoDataTextColor(Color.parseColor("#888888"));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#888888"));
        xAxis.setTextSize(11f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setGranularity(1f);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.parseColor("#888888"));
        left.setTextSize(10f);
        left.setDrawGridLines(true);
        left.setGridColor(Color.parseColor("#1F1F1F"));
        left.setDrawAxisLine(false);

        chart.getAxisRight().setEnabled(false);
    }

    // ── Navigation menu ───────────────────────────────────────────────────────

    private void showMenu(View v) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.getMenu().add("Dashboard");
        menu.getMenu().add("Performance");
        menu.getMenu().add("History");
        menu.getMenu().add("Settings");
        menu.getMenu().add("Stats");

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Dashboard":
                    startActivity(new Intent(this, MainActivity.class));
                    finish(); return true;
                case "Performance":
                    startActivity(new Intent(this, PerformanceActivity.class));
                    finish(); return true;
                case "History":
                    startActivity(new Intent(this, HistoryActivity.class));
                    finish(); return true;
                case "Settings":
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
            }
            return true;
        });
        menu.show();
    }
}
