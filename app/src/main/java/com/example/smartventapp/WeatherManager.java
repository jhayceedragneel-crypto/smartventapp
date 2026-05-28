package com.example.smartventapp;
import android.content.Context;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class WeatherManager {
    private static final String API_KEY = "27868d477ec22efe7fb10bb5ff9c80a2";
    private static final String CITY = "Manila,PH";
    private static final String URL = "https://api.openweathermap.org/data/2.5/weather?q=" + CITY + "&appid=" + API_KEY + "&units=metric";

    public static void fetchAndSave(Context context) {
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, URL, null,
                response -> {
                    try {
                        double tempC = response.getJSONObject("main").getDouble("temp");
                        int hum = response.getJSONObject("main").getInt("humidity");
                        String desc = response.getJSONArray("weather").getJSONObject(0).getString("description");

                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                        Calendar cal = Calendar.getInstance();
                        int hour = cal.get(Calendar.HOUR_OF_DAY);

                        String timeLabel;
                        if (hour >= 5 && hour <= 12) {
                            timeLabel = "Morning";
                        }
                        else if (hour >= 13 && hour <= 17) {
                            timeLabel = "Afternoon";
                        }
                        else {
                            timeLabel = "Evening";
                        }

                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("weather/" + today + "/" + timeLabel);
                        ref.child("temp_celsius").setValue(tempC);
                        ref.child("humidity").setValue(hum);
                        ref.child("description").setValue(desc);
                        ref.child("timestamp").setValue(System.currentTimeMillis());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error ->
                    error.printStackTrace()
        );
            queue.add(request);
    }
}

