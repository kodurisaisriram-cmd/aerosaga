
package com.aerosaga.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.service.MissionService;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    // Create a new mission
    @PostMapping
    public ResponseEntity<Mission> createMission(
            @RequestBody Mission mission) {

        Mission createdMission =
                missionService.createMission(mission);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createdMission);
    }

    // Get all missions
    @GetMapping
    public List<Mission> getAllMissions() {
        return missionService.getAllMissions();
    }

    // Get mission by ID
    @GetMapping("/{missionId}")
    public ResponseEntity<Mission> getMissionById(
            @PathVariable String missionId) {

        try {
            return ResponseEntity.ok(
                    missionService.getMissionById(missionId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update mission status
    @PatchMapping("/{missionId}/status")
    public ResponseEntity<Mission> updateMissionStatus(
            @PathVariable String missionId,
            @RequestParam MissionStatus status) {

        try {
            Mission mission = missionService.updateMissionStatus(
                    missionId, status);

            return ResponseEntity.ok(mission);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Get mission status
    @GetMapping("/{missionId}/status")
    public ResponseEntity<?> getMissionStatus(
            @PathVariable String missionId) {

        try {
            Mission mission = missionService.getMissionById(missionId);
            return ResponseEntity.ok(
                    java.util.Map.of(
                            "missionId", mission.getMissionId(),
                            "status", mission.getStatus()
                    )
            );
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // Start a mission
    @PostMapping("/{missionId}/start")
    public ResponseEntity<Mission> startMission(
            @PathVariable String missionId) {

        try {
            Mission mission = missionService.startMission(missionId);
            return ResponseEntity.ok(mission);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
