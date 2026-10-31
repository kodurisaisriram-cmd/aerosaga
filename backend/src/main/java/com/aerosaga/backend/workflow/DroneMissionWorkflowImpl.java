
package com.aerosaga.backend.workflow;

import com.aerosaga.backend.activity.DroneActivity;
import com.aerosaga.backend.model.MissionStatus;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DroneMissionWorkflowImpl implements DroneMissionWorkflow {

    private MissionStatus missionStatus = MissionStatus.PENDING;
    private String missionId;

    private final Map<String, DroneProgress> droneProgress =
            new LinkedHashMap<>();

    // Mission-level progress and recovery details
    private String currentStep = "Pending";
    private Double progress = 0.0;
    private String recoveryStatus = "NOT_STARTED";

    private final DroneActivity activities =
            Workflow.newActivityStub(
                    DroneActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(30))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(1))
                                            .setBackoffCoefficient(2.0)
                                            .setMaximumInterval(Duration.ofSeconds(10))
                                            .setMaximumAttempts(3)
                                            .build())
                            .build());

    @Override
    public void executeMission(
            String missionId,
            List<DroneMissionRequest> requests) {

        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException("Mission ID is required");
        }

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one drone is required");
        }

        this.missionId = missionId;
        this.missionStatus = MissionStatus.ACTIVE;
        this.currentStep = "Taking off";
        this.progress = 10.0;
        this.recoveryStatus = "NOT_STARTED";

        droneProgress.clear();

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
                        "Duplicate drone ID: " + request.getDroneId());
            }

            droneProgress.put(
                    request.getDroneId(),
                    new DroneProgress(
                            request.getDroneId(),
                            request.getDestination(),
                            "Waiting to start",
                            DroneStatus.PENDING));
        }

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info("Mission {} started with {} drones",
                        missionId, requests.size());

        // Start every drone mission asynchronously.
        List<Promise<Void>> dronePromises = new ArrayList<>();

        for (DroneMissionRequest request : requests) {
            dronePromises.add(
                    Async.procedure(
                            () -> executeSingleDroneMission(request)));
        }

        // Wait for all drone missions to finish.
        Promise.allOf(dronePromises.toArray(new Promise<?>[0])).get();

        boolean allCompleted = true;
        int completedCount = 0;

        for (DroneProgress drone : droneProgress.values()) {

            if (drone.getStatus() == DroneStatus.COMPLETED) {
                completedCount++;
            } else {
                allCompleted = false;
            }
        }

        if (allCompleted) {
            missionStatus = MissionStatus.COMPLETED;
            currentStep = "Completed";
            progress = 100.0;
            recoveryStatus = "COMPLETED";
        } else {
            missionStatus = MissionStatus.FAILED;
            currentStep = "Failed";
            progress = (completedCount * 100.0) / requests.size();
            recoveryStatus = "FAILED";
        }

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info("Mission {} finished with status: {}",
                        missionId, missionStatus);
    }

    private void executeSingleDroneMission(DroneMissionRequest request) {

        String droneId = request.getDroneId();
        String destination = request.getDestination();

        DroneProgress drone = droneProgress.get(droneId);

        try {

            drone.setStatus(DroneStatus.RUNNING);
            drone.setCurrentStep("Taking off");

            currentStep = "Taking off";
            progress = Math.max(progress, 25.0);

            activities.takeoff(droneId);

            drone.setCurrentStep("Navigating to destination");

            currentStep = "Navigating";
            progress = Math.max(progress, 50.0);

            activities.navigate(droneId, destination);

            drone.setCurrentStep("Delivering package");

            currentStep = "Delivering package";
            progress = Math.max(progress, 75.0);

            try {
                activities.dropPackage(droneId);

            } catch (ActivityFailure deliveryFailure) {

                drone.setCurrentStep(
                        "Delivery failed - recovering drone");

                currentStep = "Recovering drone";
                recoveryStatus = "RECOVERING";

                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .warn(
                                "Delivery failed for drone {}. Starting recovery.",
                                droneId);

                activities.recoverDrone(droneId);

                drone.setCurrentStep(
                        "Recovery completed - retrying delivery");

                recoveryStatus = "RETRYING_DELIVERY";

                activities.dropPackage(droneId);
            }

            drone.setCurrentStep("Returning to base");

            currentStep = "Returning to base";
            progress = Math.max(progress, 90.0);
            missionStatus = MissionStatus.RETURNING_TO_BASE;
            recoveryStatus = "RETURNING_TO_BASE";

            activities.returnToBase(droneId);

            drone.setCurrentStep("Completed");
            drone.setStatus(DroneStatus.COMPLETED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .info("Drone {} completed mission {}",
                            droneId, missionId);

        } catch (RuntimeException exception) {

            drone.setStatus(DroneStatus.FAILED);
            drone.setCurrentStep("Failed");

            currentStep = "Failed";
            recoveryStatus = "FAILED";

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error("Drone {} failed in mission {}",
                            droneId, missionId, exception);
        }
    }

    @Override
    public MissionProgress getMissionProgress() {

        // Preserve teammate's drone-level progress list.
        MissionProgress snapshot = new MissionProgress(
                missionId,
                missionStatus,
                new ArrayList<>(droneProgress.values()));

        // Add mission-level progress details.
        snapshot.setCurrentStep(currentStep);
        snapshot.setProgress(progress);
        snapshot.setRecoveryStatus(recoveryStatus);

        return snapshot;
    }
}
