
package com.aerosaga.backend.service;

import java.util.List;

import com.aerosaga.backend.model.Telemetry;

public interface TelemetryService {

    // Save drone telemetry
    Telemetry saveTelemetry(Telemetry telemetry);

    // Get all telemetry records
    List<Telemetry> getAllTelemetry();

    // Get telemetry history for a specific drone
    List<Telemetry> getTelemetryByDroneId(String droneId);

    // Get latest telemetry of a drone
    Telemetry getLatestTelemetry(String droneId);
}
