package com.aerosaga.backend.workflow;

import java.util.ArrayList;
import java.util.List;

import com.aerosaga.backend.model.MissionStatus;

public class MissionProgress {

    private String missionId;
    private MissionStatus status;

    // Existing drone-level progress
    private List<DroneProgress> drones = new ArrayList<>();

    // Mission-level progress and recovery details
    private String droneId;
    private String currentStep;
    private Double progress;
    private String recoveryStatus;

    public MissionProgress() {
    }

    public MissionProgress(String missionId, MissionStatus missionStatus, ArrayList drones) {
        this.missionId = missionId;
        this.status = missionStatus;
        this.drones = new ArrayList<>(drones);
    }

    // Constructor used by mission-level progress
    public MissionProgress(String missionId, String droneId,
                           MissionStatus status, String currentStep,
                           Double progress, String recoveryStatus) {
        this.missionId = missionId;
        this.droneId = droneId;
        this.status = status;
        this.currentStep = currentStep;
        this.progress = progress;
        this.recoveryStatus = recoveryStatus;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public List<DroneProgress> getDrones() {
        return drones;
    }

    public void setDrones(List<DroneProgress> drones) {
        this.drones = drones;
    }

    public void setRecoveryStatus(String recoveryStatus) {
        throw new UnsupportedOperationException("Unimplemented method 'setRecoveryStatus'");
    }

    public void setProgress(Double progress) {
        throw new UnsupportedOperationException("Unimplemented method 'setProgress'");
    }

    public void setCurrentStep(String currentStep) {
        throw new UnsupportedOperationException("Unimplemented method 'setCurrentStep'");
    }
}
