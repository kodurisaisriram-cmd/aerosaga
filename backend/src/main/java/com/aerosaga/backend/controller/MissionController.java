
package com.aerosaga.backend.controller;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.service.MissionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

        Mission createdMission = missionService.createMission(mission);

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

        Mission mission = missionService.getMissionById(missionId);

        if (mission == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(mission);
    }

    // Update mission status
    @PatchMapping("/{missionId}/status")
    public ResponseEntity<Mission> updateMissionStatus(
            @PathVariable String missionId,
            @RequestParam MissionStatus status) {

        Mission mission = missionService.updateMissionStatus(
                missionId, status);

        if (mission == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(mission);
    }
}
