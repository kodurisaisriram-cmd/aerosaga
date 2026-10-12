
package com.aerosaga.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.repository.MissionRepository;
import com.aerosaga.backend.service.MissionService;

@Service
public class MissionServiceImpl implements MissionService {

    private final MissionRepository missionRepository;

    // Constructor Injection
    public MissionServiceImpl(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    // Create Mission
    @Override
    public Mission createMission(Mission mission) {

        if (mission == null) {
            throw new IllegalArgumentException("Mission cannot be null");
        }

        if (mission.getDroneId() == null ||
                mission.getDroneId().isBlank()) {
            throw new IllegalArgumentException("Drone ID is required");
        }

        mission.setMissionId(UUID.randomUUID().toString());
        mission.setStatus(MissionStatus.PENDING);
        mission.setCreatedAt(LocalDateTime.now());

        return missionRepository.save(mission);
    }

    // Get All Missions
    @Override
    public List<Mission> getAllMissions() {
        return missionRepository.findAll();
    }

    // Get Mission By ID
    @Override
    public Mission getMissionById(String missionId) {
        return missionRepository.findById(missionId).orElse(null);
    }

    // Update Mission Status
    @Override
    @Transactional
    public Mission updateMissionStatus(
            String missionId,
            MissionStatus status) {

        if (status == null) {
            throw new IllegalArgumentException("Mission status is required");
        }

        Mission mission = missionRepository.findById(missionId)
                .orElse(null);

        if (mission == null) {
            return null;
        }

        mission.setStatus(status);

        return missionRepository.save(mission);
    }
}
