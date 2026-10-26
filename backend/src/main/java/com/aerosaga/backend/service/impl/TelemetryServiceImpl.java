
package com.aerosaga.backend.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.Telemetry;
import com.aerosaga.backend.model.TelemetryMessage;
import com.aerosaga.backend.repository.MissionRepository;
import com.aerosaga.backend.repository.TelemetryRepository;
import com.aerosaga.backend.service.TelemetryService;
import com.aerosaga.backend.service.TelemetryWebSocketService;

@Service
public class TelemetryServiceImpl implements TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final MissionRepository missionRepository;
    private final TelemetryWebSocketService webSocketService;

    public TelemetryServiceImpl(
            TelemetryRepository telemetryRepository,
            MissionRepository missionRepository,
            TelemetryWebSocketService webSocketService) {

        this.telemetryRepository = telemetryRepository;
        this.missionRepository = missionRepository;
        this.webSocketService = webSocketService;
    }

    @Override
    @Transactional
    public Telemetry saveTelemetry(Telemetry telemetry) {

        if (telemetry == null) {
            throw new IllegalArgumentException(
                    "Telemetry cannot be null");
        }

        if (telemetry.getDroneId() == null
                || telemetry.getDroneId().isBlank()) {
            throw new IllegalArgumentException(
                    "Drone ID is required");
        }

        if (telemetry.getMissionId() == null
                || telemetry.getMissionId().isBlank()) {
            throw new IllegalArgumentException(
                    "Mission ID is required");
        }

        Mission mission = missionRepository
                .findByMissionIdAndDroneId(
                        telemetry.getMissionId(),
                        telemetry.getDroneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mission not found for the given drone"));

        telemetry.setId(null);
        telemetry.setTimestamp(LocalDateTime.now());

        Telemetry savedTelemetry =
                telemetryRepository.save(telemetry);

        TelemetryMessage message = new TelemetryMessage(
                savedTelemetry.getDroneId(),
                savedTelemetry.getLatitude(),
                savedTelemetry.getLongitude(),
                savedTelemetry.getAltitude(),
                savedTelemetry.getBatteryLevel(),
                Instant.now(),
                mission.getMissionId()
        );

        // Publish updates to all telemetry subscribers.
        webSocketService.publishTelemetry(message);

        // Publish updates to subscribers of this specific mission.
        webSocketService.publishMissionTelemetry(
                mission.getMissionId(),
                message
        );

        // Publish updates to subscribers of this specific drone.
        webSocketService.publishDroneTelemetry(
                savedTelemetry.getDroneId(),
                message
        );

        return savedTelemetry;
    }

    @Override
    public List<Telemetry> getAllTelemetry() {
        return telemetryRepository.findAll();
    }

    @Override
    public List<Telemetry> getTelemetryByDroneId(String droneId) {
        return telemetryRepository
                .findByDroneIdOrderByTimestampDesc(droneId);
    }

    @Override
    public Telemetry getLatestTelemetry(String droneId) {

        List<Telemetry> records =
                telemetryRepository
                        .findByDroneIdOrderByTimestampDesc(droneId);

        if (records.isEmpty()) {
            return null;
        }

        return records.get(0);
    }
}
