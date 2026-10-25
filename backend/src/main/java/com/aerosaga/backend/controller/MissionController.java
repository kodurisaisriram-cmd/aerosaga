package com.aerosaga.backend.controller;

import com.aerosaga.backend.telemetry.TelemetrySimulator;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/missions")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
public class MissionController {

    private final TelemetrySimulator telemetrySimulator;

    public MissionController(TelemetrySimulator telemetrySimulator) {
        this.telemetrySimulator = telemetrySimulator;
    }

    @GetMapping
    public List<Map<String, Object>> getMissions() {
        return telemetrySimulator.getMissions();
    }
}