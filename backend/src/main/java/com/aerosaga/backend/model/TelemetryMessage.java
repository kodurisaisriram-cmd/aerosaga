
package com.aerosaga.backend.model;

import java.time.Instant;

public class TelemetryMessage {

    private String droneId;
    private double latitude;
    private double longitude;
    private double altitude;
    private double batteryLevel;
    private Instant timestamp;
    private String missionId;

    public TelemetryMessage() {
    }

    public TelemetryMessage(
            String droneId,
            double latitude,
            double longitude,
            double altitude,
            Instant timestamp,
            String missionId) {
        this(droneId, latitude, longitude, altitude, 0.0, timestamp, missionId);
    }

    public TelemetryMessage(
            String droneId,
            double latitude,
            double longitude,
            double altitude,
            double batteryLevel,
            Instant timestamp,
            String missionId) {
        this.droneId = droneId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.batteryLevel = batteryLevel;
        this.timestamp = timestamp;
        this.missionId = missionId;
    }

    public String getDroneId() {
        return droneId;
    }

    public void setDroneId(String droneId) {
        this.droneId = droneId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }
}
