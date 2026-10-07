package com.aerosaga.backend.model;

import java.time.LocalDateTime;

public class Mission {

    private String missionId;
    private String droneId;
    private String source;
    private String destination;
    private MissionStatus status;
    private LocalDateTime createdAt;

    // No-argument constructor
    public Mission() {
    }

    // Parameterized constructor
    public Mission(String missionId, String droneId, String source,
                   String destination, MissionStatus status,
                   LocalDateTime createdAt) {

        this.missionId = missionId;
        this.droneId = droneId;
        this.source = source;
        this.destination = destination;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getDroneId() {
        return droneId;
    }

    public void setDroneId(String droneId) {
        this.droneId = droneId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}