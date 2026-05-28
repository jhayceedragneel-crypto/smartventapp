package com.example.smartventapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerHistory;
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList = new ArrayList<>();
    private TextView tvLoading, tvEmpty;
    private WeatherAnimationView weatherAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        View rootLayout = findViewById(R.id.activity_history_root);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Setup views
        recyclerHistory = findViewById(R.id.recyclerHistory);
        tvLoading       = findViewById(R.id.tvLoading);
        tvEmpty         = findViewById(R.id.tvEmpty);
        weatherAnim     = findViewById(R.id.weather_anim);

        if (weatherAnim != null) {
            weatherAnim.setWeatherType("leaves"); // Default aesthetic for history
        }

        // Header Menu (Same as Main)
        View menuContainer = findViewById(R.id.menu_container);
        if (menuContainer != null) {
            menuContainer.setOnClickListener(this::showMenu);
        }

        // Back button
        View btnBackContainer = findViewById(R.id.btnBackContainer);
        if (btnBackContainer != null) {
            btnBackContainer.setOnClickListener(v -> finish());
        }

        // Setup RecyclerView
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        recyclerHistory.setAdapter(adapter);

        // Load history from Firebase
        loadHistory();
    }

    private void showMenu(View v) {
        PopupMenu popupMenu = new PopupMenu(this, v);
        popupMenu.getMenu().add("Dashboard");
        popupMenu.getMenu().add("Performance");
        popupMenu.getMenu().add("History");

        popupMenu.setOnMenuItemClickListener(item -> {
            String selected = item.getTitle().toString();
            if (selected.equals("Dashboard")) {
                finish(); // Go back to main
                return true;
            }
            if (selected.equals("Performance")) {
                startActivity(new Intent(this, PerformanceActivity.class));
                finish();
                return true;
            }
            return true;
        });
        popupMenu.show();
    }

    private void loadHistory() {
        tvLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        FirebaseDatabase.getInstance()
            .getReference("weather")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    historyList.clear();

                    for (DataSnapshot dateSnap : snapshot.getChildren()) {
                        String date = dateSnap.getKey();

                        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) continue;

                        for (DataSnapshot timeSnap : dateSnap.getChildren()) {
                            String timeLabel = timeSnap.getKey();

                            Double temp = timeSnap.child("temp_celsius").getValue(Double.class);
                            Integer hum = timeSnap.child("humidity").getValue(Integer.class);
                            String desc = timeSnap.child("description").getValue(String.class);

                            if (temp != null && hum != null) {
                                historyList.add(new HistoryItem(
                                        date + " · " + capitalize(timeLabel),
                                        temp,
                                        hum,
                                        desc != null ? desc : "N/A"
                                ));
                            }
                        }
                    }

                    // Sort newest first
                    Collections.sort(historyList,
                        (a, b) -> b.getDate().compareTo(a.getDate()));

                    tvLoading.setVisibility(View.GONE);

                    if (historyList.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        adapter.notifyDataSetChanged();
                        
                        // Update background based on latest history
                        if (!historyList.isEmpty() && weatherAnim != null) {
                            String desc = historyList.get(0).getDescription().toLowerCase();
                            if (desc.contains("rain")) weatherAnim.setWeatherType("rain");
                            else if (desc.contains("cloud")) weatherAnim.setWeatherType("leaves");
                            else if (desc.contains("snow")) weatherAnim.setWeatherType("snow");
                            else weatherAnim.setWeatherType("sun");
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    tvLoading.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Failed to load history");
                }
            });
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
