package com.aerosaga.backend.workflow;

public class DroneProgress {
    private String droneId;
    private String destination;
    private String currentStep;
    private DroneStatus status;

    public DroneProgress() {
    }

    public DroneProgress(String droneId, String destination, String currentStep, DroneStatus status) {
        this.droneId = droneId;
        this.destination = destination;
        this.currentStep = currentStep;
        this.status = status;
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

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public DroneStatus getStatus() {
        return status;
    }

    public void setStatus(DroneStatus status) {
        this.status = status;
    }
}
