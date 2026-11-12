
package com.aerosaga.backend.service;

import java.util.List;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;

public interface MissionService {

    Mission createMission(Mission mission);

    List<Mission> getAllMissions();

    Mission getMissionById(String missionId);

    Mission getMissionProgress(String missionId);

    Mission updateMissionStatus(
            String missionId,
            MissionStatus status);

    Mission updateMissionProgress(
            String missionId,
            Double progress,
            String currentStep,
            String recoveryStatus);

    Mission startMission(String missionId);

    Mission abortMission(String missionId);

    Mission abortMission(String missionId, String reason);
}

