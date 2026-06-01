package com.example.smartventapp;

/**
 * Listener for parsed ESP32 sensor data.
 * Called by BluetoothFanController when a DATA: line arrives.
 */
public interface SensorListener {
    void onData(float tempC, float humidity, float gas1, float gas2);
}