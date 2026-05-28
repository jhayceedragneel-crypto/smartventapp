package com.example.smartventapp;

public class HistoryItem {
    private String date;
    private double temperature;
    private int humidity;
    private String description;

    public HistoryItem() {}

    public HistoryItem(String date, double temperature, int humidity, String description) {
        this.date = date;
        this.temperature = temperature;
        this.humidity = humidity;
        this.description = description;
    }

    public String getDate() { return date; }
    public double getTemperature() { return temperature; }
    public int getHumidity() { return humidity; }
    public String getDescription() { return description; }

    public void setDate(String date) { this.date = date; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    public void setDescription(String description) { this.description = description; }
}
