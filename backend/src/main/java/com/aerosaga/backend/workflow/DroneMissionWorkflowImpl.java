package com.aerosaga.backend.workflow;

import com.aerosaga.backend.activity.DroneActivity;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DroneMissionWorkflowImpl
        implements DroneMissionWorkflow {

    private MissionStatus missionStatus =
            MissionStatus.PENDING;

    private String missionId;

    private final Map<String, DroneProgress> droneProgress =
            new LinkedHashMap<>();

    private final DroneActivity activities =
            Workflow.newActivityStub(
                    DroneActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(
                                    Duration.ofSeconds(30))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(
                                                    Duration.ofSeconds(1))
                                            .setBackoffCoefficient(2.0)
                                            .setMaximumInterval(
                                                    Duration.ofSeconds(10))
                                            .setMaximumAttempts(3)
                                            .build())
                            .build()
            );

    @Override
    public void executeMission(
            String missionId,
            List<DroneMissionRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one drone is required");
        }

        this.missionId = missionId;

        Set<String> droneIds = new HashSet<>();

        for (DroneMissionRequest request : requests) {
            if (request == null
                    || request.getDroneId() == null
                    || request.getDroneId().isBlank()
                    || request.getDestination() == null
                    || request.getDestination().isBlank()) {
                throw new IllegalArgumentException(
                        "Drone ID and destination are required");
            }

            if (!droneIds.add(request.getDroneId())) {
                throw new IllegalArgumentException(
                        "Duplicate drone ID: "
                                + request.getDroneId());
            }

            droneProgress.put(
                    request.getDroneId(),
                    new DroneProgress(
                            request.getDroneId(),
                            request.getDestination(),
                            "Waiting to start",
                            DroneStatus.PENDING)
            );
        }

        missionStatus = MissionStatus.RUNNING;

        Workflow.getLogger(
                DroneMissionWorkflowImpl.class)
                .info(
                        "Mission {} started with {} drones",
                        missionId,
                        requests.size());

        List<Promise<Void>> dronePromises =
                new ArrayList<>();

        // Start every drone mission asynchronously.
        for (DroneMissionRequest request : requests) {
            Promise<Void> promise = Async.procedure(
                    () -> executeSingleDroneMission(request));

            dronePromises.add(promise);
        }

        try {
            // Wait for all drone missions to finish.
            Promise.allOf(
                    dronePromises.toArray(
                            new Promise<?>[0]))
                    .get();

            missionStatus = MissionStatus.COMPLETED;

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .info(
                            "Mission {} completed successfully",
                            missionId);

        } catch (RuntimeException exception) {
            missionStatus = MissionStatus.FAILED;

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .error(
                            "Mission {} failed",
                            missionId,
                            exception);

            throw exception;
        }
    }

    private void executeSingleDroneMission(
            DroneMissionRequest request) {

        String droneId = request.getDroneId();
        String destination = request.getDestination();

        DroneProgress progress = droneProgress.get(droneId);

        try {
            progress.setStatus(DroneStatus.RUNNING);

            progress.setCurrentStep("Taking off");
            activities.takeoff(droneId);

            progress.setCurrentStep("Navigating");
            activities.navigate(droneId, destination);

            progress.setCurrentStep("Dropping package");
            activities.dropPackage(droneId);

            progress.setCurrentStep("Returning to base");
            activities.returnToBase(droneId);

            progress.setCurrentStep("Completed");
            progress.setStatus(DroneStatus.COMPLETED);

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .info(
                            "Drone {} completed mission {}",
                            droneId,
                            missionId);

        } catch (RuntimeException exception) {
            progress.setStatus(DroneStatus.FAILED);
            progress.setCurrentStep("Failed");

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .error(
                            "Drone {} failed in mission {}",
                            droneId,
                            missionId,
                            exception);

            throw exception;
        }
    }

    @Override
    public MissionProgress getMissionProgress() {
        return new MissionProgress(
                missionId,
                missionStatus,
                new ArrayList<>(droneProgress.values())
        );
    }
}