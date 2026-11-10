
package com.aerosaga.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import com.aerosaga.backend.workflow.DroneMissionRequest;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.repository.MissionRepository;
import com.aerosaga.backend.service.MissionService;
import com.aerosaga.backend.worker.TemporalWorkerConfig;
import com.aerosaga.backend.workflow.DroneMissionWorkflow;
import com.aerosaga.backend.workflow.MissionProgress;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@Service
public class MissionServiceImpl implements MissionService {

    private static final Logger log =
            LoggerFactory.getLogger(MissionServiceImpl.class);

    private final MissionRepository missionRepository;
    private final WorkflowClient workflowClient;

    public MissionServiceImpl(
            MissionRepository missionRepository,
            WorkflowClient workflowClient) {
        this.missionRepository = missionRepository;
        this.workflowClient = workflowClient;
    }

    @Override
    public Mission createMission(Mission mission) {

        if (mission == null) {
            throw new IllegalArgumentException(
                    "Mission cannot be null");
        }

        if (mission.getDroneId() == null
                || mission.getDroneId().isBlank()) {
            throw new IllegalArgumentException(
                    "Drone ID is required");
        }

        if (mission.getDestination() == null
                || mission.getDestination().isBlank()) {
            throw new IllegalArgumentException(
                    "Destination is required");
        }

        LocalDateTime now = LocalDateTime.now();
        mission.setMissionId(UUID.randomUUID().toString());
        mission.setStatus(MissionStatus.PENDING);
        mission.setCreatedAt(now);
        mission.setUpdatedAt(now);
        mission.setProgress(0.0);
        mission.setCurrentStep("Pending");
        mission.setRecoveryStatus("NOT_STARTED");

        return missionRepository.save(mission);
    }

    @Override
    public List<Mission> getAllMissions() {
        return missionRepository.findAll();
    }

    @Override
    public Mission getMissionById(String missionId) {
        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mission ID is required");
        }
        return missionRepository.findById(missionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Mission not found: " + missionId));
    }

    @Override
    public Mission getMissionProgress(String missionId) {

        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mission ID is required");
        }

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Mission not found: " + missionId));

        // Coordinate with Member 2's Temporal workflow when active or in recovery
        if (mission.getStatus() == MissionStatus.ACTIVE
                || mission.getStatus() == MissionStatus.IN_PROGRESS
                || mission.getStatus() == MissionStatus.RETURNING_TO_BASE
                || mission.getStatus() == MissionStatus.ABORT_REQUESTED
                || mission.getStatus() == MissionStatus.ABORTING) {
            try {
                DroneMissionWorkflow workflowStub = workflowClient.newWorkflowStub(
                        DroneMissionWorkflow.class,
                        "mission-" + missionId
                );
                MissionProgress liveProgress = workflowStub.getMissionProgress();
                if (liveProgress != null) {
                    boolean changed = false;
                    if (liveProgress.getStatus() != null && liveProgress.getStatus() != mission.getStatus()) {
                        mission.setStatus(liveProgress.getStatus());
                        changed = true;
                    }
                    if (liveProgress.getProgress() != null && !liveProgress.getProgress().equals(mission.getProgress())) {
                        if (mission.getProgress() == null || liveProgress.getProgress() >= mission.getProgress()
                                || liveProgress.getStatus() == MissionStatus.FAILED
                                || liveProgress.getStatus() == MissionStatus.ABORTED) {
                            mission.setProgress(liveProgress.getProgress());
                            changed = true;
                        }
                    }
                    if (liveProgress.getCurrentStep() != null && !liveProgress.getCurrentStep().equals(mission.getCurrentStep())) {
                        mission.setCurrentStep(liveProgress.getCurrentStep());
                        changed = true;
                    }
                    if (liveProgress.getRecoveryStatus() != null && !liveProgress.getRecoveryStatus().equals(mission.getRecoveryStatus())) {
                        mission.setRecoveryStatus(liveProgress.getRecoveryStatus());
                        changed = true;
                    }
                    if (changed) {
                        mission.setUpdatedAt(LocalDateTime.now());
                        mission = missionRepository.save(mission);
                    }
                }
            } catch (Exception ex) {
                // Graceful fallback: workflow might not be started yet or offline during testing
                log.debug("Workflow query unavailable for mission {}: {}", missionId, ex.getMessage());
            }
        }

        return mission;
    }

    @Override
    public Mission updateMissionStatus(
            String missionId,
            MissionStatus status) {

        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mission ID is required");
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "Status is required");
        }

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Mission not found: " + missionId));

        validateStatusTransition(mission.getStatus(), status);

        mission.setStatus(status);
        mission.setUpdatedAt(LocalDateTime.now());

        if (status == MissionStatus.RETURNING_TO_BASE) {
            mission.setRecoveryStatus("RETURNING_TO_BASE");
            mission.setCurrentStep("Returning to base");
            if (mission.getProgress() == null || mission.getProgress() < 90.0) {
                mission.setProgress(90.0);
            }
        } else if (status == MissionStatus.COMPLETED) {
            mission.setRecoveryStatus("COMPLETED");
            mission.setCurrentStep("Completed");
            mission.setProgress(100.0);
        } else if (status == MissionStatus.FAILED || status == MissionStatus.ABORTED) {
            mission.setRecoveryStatus(status.name());
            mission.setCurrentStep(status.name());
        } else if (status == MissionStatus.ACTIVE || status == MissionStatus.IN_PROGRESS) {
            if (mission.getRecoveryStatus() == null || mission.getRecoveryStatus().isBlank()) {
                mission.setRecoveryStatus("NOT_STARTED");
            }
        }

        return missionRepository.save(mission);
    }

    private void validateStatusTransition(MissionStatus current, MissionStatus target) {
        if (current == target) {
            return;
        }

        if (current == MissionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot change status of a completed mission");
        }
        if (current == MissionStatus.ABORTED) {
            throw new IllegalStateException("Cannot change status of an aborted mission");
        }
        if (current == MissionStatus.FAILED) {
            throw new IllegalStateException("Cannot change status of a failed mission");
        }

        if (current == MissionStatus.PENDING) {
            if (target == MissionStatus.COMPLETED || target == MissionStatus.RETURNING_TO_BASE) {
                throw new IllegalStateException("Cannot transition PENDING mission directly to " + target);
            }
        }

        if (target == MissionStatus.PENDING) {
            throw new IllegalStateException("Cannot revert an active mission back to PENDING");
        }

        if (current == MissionStatus.ABORT_REQUESTED || current == MissionStatus.ABORTING) {
            if (target != MissionStatus.ABORTING && target != MissionStatus.ABORTED && target != MissionStatus.FAILED) {
                throw new IllegalStateException("Cannot transition aborting mission to " + target);
            }
        }
    }

@Override
public Mission updateMissionProgress(
        String missionId,
        Double progress,
        String currentStep,
        String recoveryStatus) {

    // 1. Validate Mission ID
    if (missionId == null || missionId.isBlank()) {
        throw new IllegalArgumentException(
                "Mission ID is required");
    }

    // 2. Fetch Mission
    Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Mission not found: " + missionId));

    // 3. Prevent updates to completed or terminated missions
    if (mission.getStatus() == MissionStatus.COMPLETED) {
        throw new IllegalStateException(
                "Cannot update progress of a completed mission");
    }
    if (mission.getStatus() == MissionStatus.ABORTED) {
        throw new IllegalStateException(
                "Cannot update progress of an aborted mission");
    }
    if (mission.getStatus() == MissionStatus.FAILED) {
        throw new IllegalStateException(
                "Cannot update progress of a failed mission");
    }

    // 4. Validate progress range
    if (progress != null && (progress < 0 || progress > 100)) {
        throw new IllegalArgumentException(
                "Progress must be between 0 and 100");
    }

    // 5. Update progress
    if (progress != null) {
        mission.setProgress(progress);
    }

    // 6. Update current step
    if (currentStep != null && !currentStep.isBlank()) {
        mission.setCurrentStep(currentStep);
    }

    // 7. Update recovery status
    if (recoveryStatus != null && !recoveryStatus.isBlank()) {
        mission.setRecoveryStatus(recoveryStatus);
    }

    // 8. Update timestamp
    mission.setUpdatedAt(LocalDateTime.now());

    // 9. Save updated mission
    return missionRepository.save(mission);
}


    @Override
    @Transactional
    public Mission startMission(String missionId) {

        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mission ID is required");
        }

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Mission not found: " + missionId));

        if (mission.getStatus() != MissionStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING missions can be started. "
                    + "Current status: " + mission.getStatus());
        }

        if (mission.getDroneId() == null || mission.getDroneId().isBlank()) {
            throw new IllegalArgumentException(
                    "Drone ID is required to start mission");
        }

        if (mission.getDestination() == null || mission.getDestination().isBlank()) {
            throw new IllegalArgumentException(
                    "Destination is required to start mission");
        }

        DroneMissionWorkflow workflow =
                workflowClient.newWorkflowStub(
                        DroneMissionWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId("mission-" + missionId)
                                .setTaskQueue(
                                        TemporalWorkerConfig.TASK_QUEUE)
                                .build());

DroneMissionRequest request = new DroneMissionRequest(
        mission.getDroneId(),
        mission.getDestination()
);

WorkflowClient.start(
        workflow::executeMission,
        mission.getMissionId(),
        List.of(request)
);

        mission.setStatus(MissionStatus.ACTIVE);
        mission.setCurrentStep("Taking off");
        mission.setProgress(10.0);
        mission.setRecoveryStatus("NOT_STARTED");
        mission.setUpdatedAt(LocalDateTime.now());
        return missionRepository.save(mission);
    }

    @Override
    public Mission abortMission(String missionId) {
        return abortMission(missionId, null);
    }

    @Override
    @Transactional
    public Mission abortMission(String missionId, String reason) {
        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException("Mission ID is required");
        }

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Mission not found: " + missionId));

        if (mission.getStatus() == MissionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot abort a completed mission");
        }

        if (mission.getStatus() == MissionStatus.ABORTED) {
            throw new IllegalStateException("Mission is already aborted");
        }

        if (mission.getStatus() == MissionStatus.ABORT_REQUESTED
                || mission.getStatus() == MissionStatus.ABORTING) {
            throw new IllegalStateException("Mission abort is already in progress");
        }

        if (mission.getStatus() == MissionStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot abort a mission that has not been started. Current status: PENDING");
        }

        if (mission.getStatus() == MissionStatus.FAILED) {
            throw new IllegalStateException("Cannot abort a failed mission");
        }

        // Send Emergency Abort signal to running Temporal workflow
        try {
            DroneMissionWorkflow workflowStub = workflowClient.newWorkflowStub(
                    DroneMissionWorkflow.class,
                    "mission-" + missionId
            );
            workflowStub.requestEmergencyAbort();
            log.info("Sent emergency abort signal to workflow mission-{}", missionId);
        } catch (Exception ex) {
            log.error("Could not signal workflow for mission {}: {}", missionId, ex.getMessage());
            throw new IllegalStateException("Failed to abort mission workflow: " + ex.getMessage(), ex);
        }

        // Mark as ABORT_REQUESTED — the workflow is still executing safe-return/abort
        // procedures. The status will transition to ABORTED only after the workflow
        // finishes and getMissionProgress() syncs the final state back.
        mission.setStatus(MissionStatus.ABORT_REQUESTED);
        mission.setRecoveryStatus("ABORT_REQUESTED");
        mission.setCurrentStep(reason != null && !reason.isBlank()
                ? "Abort requested: " + reason
                : "Emergency abort requested - stopping mission");
        mission.setUpdatedAt(LocalDateTime.now());

        return missionRepository.save(mission);
    }
}

