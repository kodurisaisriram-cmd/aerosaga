
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
import io.temporal.serviceclient.WorkflowServiceStubs;

@Service
public class MissionServiceImpl implements MissionService {

    private final MissionRepository missionRepository;
    private final WorkflowClient workflowClient;

    public MissionServiceImpl(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;

        WorkflowServiceStubs service =
                WorkflowServiceStubs.newLocalServiceStubs();

        this.workflowClient = WorkflowClient.newInstance(service);
    }

    @Override
    public Mission createMission(Mission mission) {

        if (mission == null) {
            throw new IllegalArgumentException("Mission cannot be null");
        }

        if (mission.getDroneId() == null ||
                mission.getDroneId().isBlank()) {
            throw new IllegalArgumentException("Drone ID is required");
        }

        mission.setMissionId(UUID.randomUUID().toString());
        mission.setStatus(MissionStatus.PENDING);
        mission.setCreatedAt(LocalDateTime.now());

        Mission savedMission = missionRepository.save(mission);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalWorkerConfig.TASK_QUEUE)
                .setWorkflowId("drone-mission-" + savedMission.getMissionId())
                .build();

        DroneMissionWorkflow workflow =
                workflowClient.newWorkflowStub(
                        DroneMissionWorkflow.class, options);

        WorkflowClient.start(
                workflow::executeMission,
                savedMission.getMissionId());

        return savedMission;
    }

    @Override
    public List<Mission> getAllMissions() {
        return missionRepository.findAll();
    }

    @Override
    public Mission getMissionById(String missionId) {
        return missionRepository.findById(missionId).orElse(null);
    }

    @Override
    @Transactional
    public Mission updateMissionStatus(
            String missionId,
            MissionStatus status) {

        if (status == null) {
            throw new IllegalArgumentException("Mission status is required");
        }

        Mission mission = missionRepository.findById(missionId)
                .orElse(null);

        if (mission == null) {
            return null;
        }

        mission.setStatus(status);

        return missionRepository.save(mission);
    }
}
