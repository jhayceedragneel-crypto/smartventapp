package com.example.smartventapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<HistoryItem> items;

    public HistoryAdapter(List<HistoryItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = items.get(position);

        // Format date nicely: 2026-04-30 → Apr 30, 2026
        try {
            String[] parts = item.getDate().split("-");
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            int monthIndex = Integer.parseInt(parts[1]) - 1;
            String niceDate = months[monthIndex] + " " + parts[2] + ", " + parts[0];
            holder.tvDate.setText(niceDate);
        } catch (Exception e) {
            holder.tvDate.setText(item.getDate());
        }

        holder.tvDescription.setText(capitalize(item.getDescription()));
        holder.tvHumidity.setText(item.getHumidity() + "%");
        holder.tvTemperature.setText((int) Math.round(item.getTemperature()) + "°");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvDescription, tvHumidity, tvTemperature;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate        = itemView.findViewById(R.id.tvDate);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvHumidity    = itemView.findViewById(R.id.tvHumidity);
            tvTemperature = itemView.findViewById(R.id.tvTemperature);
        }
    }
}
