
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
import com.aerosaga.backend.worker.TemporalWorkerConfig;
import com.aerosaga.backend.workflow.DroneMissionWorkflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@Service
public class MissionServiceImpl implements MissionService {

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

        mission.setMissionId(UUID.randomUUID().toString());
        mission.setStatus(MissionStatus.PENDING);
        mission.setCreatedAt(LocalDateTime.now());

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

        mission.setStatus(status);

        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission startMission(String missionId) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Mission not found: " + missionId));

        if (mission.getStatus() != MissionStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING missions can be started. "
                    + "Current status: " + mission.getStatus());
        }

        DroneMissionWorkflow workflow =
                workflowClient.newWorkflowStub(
                        DroneMissionWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId("mission-" + missionId)
                                .setTaskQueue(
                                        TemporalWorkerConfig.TASK_QUEUE)
                                .build());

        WorkflowClient.start(
                workflow::executeMission,
                mission.getMissionId(),
                mission.getDroneId(),
                mission.getDestination()
        );

        mission.setStatus(MissionStatus.IN_PROGRESS);

        return missionRepository.save(mission);
    }
}
