
package com.aerosaga.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.aerosaga.backend.model.Telemetry;
import com.aerosaga.backend.repository.TelemetryRepository;
import com.aerosaga.backend.service.TelemetryService;

@Service
public class TelemetryServiceImpl implements TelemetryService {

    private final TelemetryRepository telemetryRepository;

    // Constructor Injection
    public TelemetryServiceImpl(TelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    // Save telemetry data
    @Override
    public Telemetry saveTelemetry(Telemetry telemetry) {

        if (telemetry == null) {
            throw new IllegalArgumentException("Telemetry cannot be null");
        }

        if (telemetry.getDroneId() == null ||
                telemetry.getDroneId().isBlank()) {
            throw new IllegalArgumentException("Drone ID is required");
        }

        // Always create a new telemetry record
        telemetry.setId(null);

        // Set timestamp when telemetry is received
        telemetry.setTimestamp(LocalDateTime.now());

        return telemetryRepository.save(telemetry);
    }

    // Get all telemetry records
    @Override
    public List<Telemetry> getAllTelemetry() {
        return telemetryRepository.findAll();
    }

    // Get telemetry history by drone ID
    @Override
    public List<Telemetry> getTelemetryByDroneId(String droneId) {
        return telemetryRepository
                .findByDroneIdOrderByTimestampDesc(droneId);
    }

    // Get latest telemetry record
    @Override
    public Telemetry getLatestTelemetry(String droneId) {

        List<Telemetry> records =
                telemetryRepository.findByDroneIdOrderByTimestampDesc(droneId);

        if (records.isEmpty()) {
            return null;
        }

        return records.get(0);
    }
}
