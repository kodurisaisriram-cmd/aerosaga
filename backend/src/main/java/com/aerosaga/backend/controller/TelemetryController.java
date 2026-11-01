
package com.aerosaga.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aerosaga.backend.model.Telemetry;
import com.aerosaga.backend.service.TelemetryService;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    // Save telemetry and publish live updates.
    @PostMapping
    public ResponseEntity<?> saveTelemetry(
            @RequestBody Telemetry telemetry) {
        try {
            Telemetry savedTelemetry =
                    telemetryService.saveTelemetry(telemetry);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(savedTelemetry);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity
                    .badRequest()
                    .body(ex.getMessage());
        }
    }

    // Get all telemetry records.
    @GetMapping
    public ResponseEntity<List<Telemetry>> getAllTelemetry() {
        return ResponseEntity.ok(
                telemetryService.getAllTelemetry()
        );
    }

    // Get telemetry history of a drone.
    @GetMapping("/drone/{droneId}")
    public ResponseEntity<List<Telemetry>> getTelemetryByDroneId(
            @PathVariable String droneId) {

        return ResponseEntity.ok(
                telemetryService.getTelemetryByDroneId(droneId)
        );
    }

    // Get latest telemetry of a drone.
    @GetMapping("/drone/{droneId}/latest")
    public ResponseEntity<Telemetry> getLatestTelemetry(
            @PathVariable String droneId) {

        Telemetry telemetry =
                telemetryService.getLatestTelemetry(droneId);

        if (telemetry == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(telemetry);
    }
}
