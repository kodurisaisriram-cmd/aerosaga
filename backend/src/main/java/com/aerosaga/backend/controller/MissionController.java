
package com.aerosaga.backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<?> createMission(
            @RequestBody(required = false) Mission mission) {

        if (mission == null) {
            return ResponseEntity.badRequest()
                    .body("Mission payload is required");
        }

        try {
            Mission createdMission =
                    missionService.createMission(mission);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(createdMission);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ex.getMessage());
        }
    }

    // Get all missions
    @GetMapping
    public List<Mission> getAllMissions() {
        return missionService.getAllMissions();
    }

    // Get mission by ID
    @GetMapping("/{missionId}")
    public ResponseEntity<?> getMissionById(
            @PathVariable String missionId) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            return ResponseEntity.ok(
                    missionService.getMissionById(missionId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update mission status
    @PatchMapping("/{missionId}/status")
    public ResponseEntity<?> updateMissionStatus(
            @PathVariable String missionId,
            @RequestParam(required = false) MissionStatus status,
            @RequestBody(required = false) Map<String, Object> body) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            MissionStatus targetStatus = status;

            if (targetStatus == null && body != null && body.containsKey("status")) {
                try {
                    targetStatus = MissionStatus.fromValue(body.get("status").toString());
                } catch (IllegalArgumentException ex) {
                    return ResponseEntity.badRequest()
                            .body("Invalid mission status");
                }
            }

            if (targetStatus == null) {
                return ResponseEntity.badRequest()
                        .body("Mission status is required");
            }

            Mission mission = missionService.updateMissionStatus(
                    missionId, targetStatus);

            return ResponseEntity.ok(mission);

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ex.getMessage());
            }
            return ResponseEntity.badRequest()
                    .body(ex.getMessage());
        }
    }


    // Get mission status with recovery details and progress
    @GetMapping("/{missionId}/status")
    public ResponseEntity<?> getMissionStatus(
            @PathVariable String missionId) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            Mission mission = missionService.getMissionById(missionId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("missionId", mission.getMissionId());
            response.put("status", mission.getStatus());
            response.put("recoveryStatus",
                    mission.getRecoveryStatus() != null ? mission.getRecoveryStatus() : "NOT_STARTED");
            response.put("progress",
                    mission.getProgress() != null ? mission.getProgress() : 0.0);
            response.put("currentStep",
                    mission.getCurrentStep() != null ? mission.getCurrentStep() : "Pending");
            if (mission.getUpdatedAt() != null) {
                response.put("updatedAt", mission.getUpdatedAt());
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // Get mission progress & recovery status
    @GetMapping("/{missionId}/progress")
    public ResponseEntity<?> getMissionProgress(
            @PathVariable String missionId) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            Mission mission = missionService.getMissionProgress(missionId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("missionId", mission.getMissionId());
            response.put("droneId", mission.getDroneId());
            response.put("status", mission.getStatus());
            response.put("progress",
                    mission.getProgress() != null ? mission.getProgress() : 0.0);
            response.put("currentStep",
                    mission.getCurrentStep() != null ? mission.getCurrentStep() : "Pending");
            response.put("recoveryStatus",
                    mission.getRecoveryStatus() != null ? mission.getRecoveryStatus() : "NOT_STARTED");
            if (mission.getUpdatedAt() != null) {
                response.put("updatedAt", mission.getUpdatedAt());
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update mission progress
    @PatchMapping("/{missionId}/progress")
    public ResponseEntity<?> updateMissionProgress(
            @PathVariable String missionId,
            @RequestBody(required = false) Map<String, Object> body) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            Double progress = null;
            String currentStep = null;
            String recoveryStatus = null;

            if (body != null) {
                if (body.get("progress") != null) {
                    try {
                        progress = Double.valueOf(body.get("progress").toString());
                    } catch (NumberFormatException ex) {
                        return ResponseEntity.badRequest().body("Progress must be between 0 and 100");
                    }
                }
                if (progress != null && (progress < 0 || progress > 100)) {
                    return ResponseEntity.badRequest()
                            .body("Progress must be between 0 and 100");
                }
                if (body.get("currentStep") != null) {
                    currentStep = body.get("currentStep").toString();
                }
                if (body.get("recoveryStatus") != null) {
                    recoveryStatus = body.get("recoveryStatus").toString();
                }
            }

            Mission mission = missionService.updateMissionProgress(
                    missionId, progress, currentStep, recoveryStatus);
            return ResponseEntity.ok(mission);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ex.getMessage());
            }
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    // Start a mission
    @PostMapping("/{missionId}/start")
    public ResponseEntity<?> startMission(
            @PathVariable String missionId) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            Mission mission = missionService.startMission(missionId);
            return ResponseEntity.ok(mission);

        } catch (IllegalArgumentException ex) {
            if ("Mission ID is required".equalsIgnoreCase(ex.getMessage())
                    || ex.getMessage().contains("required")) {
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }

    // Emergency abort mission
    @PostMapping("/{missionId}/abort")
    public ResponseEntity<?> abortMission(
            @PathVariable String missionId,
            @RequestBody(required = false) Map<String, Object> body) {

        if (missionId == null || missionId.isBlank()) {
            return ResponseEntity.badRequest().body("Mission ID is required");
        }

        try {
            String reason = null;
            if (body != null && body.containsKey("reason") && body.get("reason") != null) {
                reason = body.get("reason").toString();
            }

            Mission abortedMission = missionService.abortMission(missionId, reason);
            return ResponseEntity.ok(abortedMission);

        } catch (IllegalArgumentException ex) {
            if ("Mission ID is required".equalsIgnoreCase(ex.getMessage())) {
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }
}


