package com.aerosaga.backend.workflow;

import java.util.ArrayList;
import java.util.List;

public class MissionProgress {
    private String missionId;
    private MissionStatus status;
    private List<DroneProgress> drones = new ArrayList<>();

    public MissionProgress() {
    }

    public MissionProgress(String missionId, MissionStatus status, List<DroneProgress> drones) {
        this.missionId = missionId;
        this.status = status;
        this.drones = new ArrayList<>(drones);
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
}
