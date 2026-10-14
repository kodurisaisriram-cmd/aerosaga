package com.aerosaga.backend.workflow;

public class DroneMissionRequest {
    private String droneId;
    private String destination;

    public DroneMissionRequest() {
    }

    public DroneMissionRequest(String droneId, String destination) {
        this.droneId = droneId;
        this.destination = destination;
    }

    public String getDroneId() {
        return droneId;
    }

    public void setDroneId(String droneId) {
        this.droneId = droneId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
}
