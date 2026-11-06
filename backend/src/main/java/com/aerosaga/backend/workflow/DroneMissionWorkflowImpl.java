package com.aerosaga.backend.workflow;

import com.aerosaga.backend.activity.DroneActivity;

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

    private boolean emergencyAbortRequested = false;

    private final Map<String, DroneProgress> droneProgress =
            new LinkedHashMap<>();

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
    public void requestEmergencyAbort() {

        if (missionStatus == MissionStatus.RUNNING
                && !emergencyAbortRequested) {

            emergencyAbortRequested = true;
            missionStatus = MissionStatus.ABORT_REQUESTED;

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .warn(
                            "Emergency abort requested for mission {}",
                            missionId);
        }
    }

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
                        "Duplicate drone ID: "
                                + request.getDroneId());
            }

            droneProgress.put(
                    request.getDroneId(),
                    new DroneProgress(
                            request.getDroneId(),
                            request.getDestination(),
                            "Waiting to start",
                            DroneStatus.PENDING));
        }

        missionStatus = emergencyAbortRequested
                ? MissionStatus.ABORT_REQUESTED
                : MissionStatus.RUNNING;

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info(
                        "Mission {} started with {} drones",
                        missionId,
                        requests.size());

        List<Promise<Void>> dronePromises = new ArrayList<>();

        // Start every drone mission asynchronously.
        for (DroneMissionRequest request : requests) {
            dronePromises.add(
                    Async.procedure(
                            () -> executeSingleDroneMission(request)));
        }

        // Wait for all drone missions to finish.
        Promise.allOf(
                dronePromises.toArray(
                        new Promise<?>[0]))
                .get();

        boolean allCompleted = true;
        boolean anyFailed = false;
        boolean anyAborted = false;

        for (DroneProgress progress : droneProgress.values()) {

            if (progress.getStatus() != DroneStatus.COMPLETED) {
                allCompleted = false;
            }

            if (progress.getStatus() == DroneStatus.FAILED) {
                anyFailed = true;
            }

            if (progress.getStatus() == DroneStatus.ABORTED) {
                anyAborted = true;
            }
        }

        if (anyFailed) {

            missionStatus = MissionStatus.FAILED;

        } else if (emergencyAbortRequested || anyAborted) {

            missionStatus = MissionStatus.ABORTED;

        } else if (allCompleted) {

            missionStatus = MissionStatus.COMPLETED;

        } else {

            missionStatus = MissionStatus.FAILED;
        }

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info(
                        "Mission {} finished with status: {}",
                        missionId,
                        missionStatus);
    }

    private void executeSingleDroneMission(
            DroneMissionRequest request) {

        String droneId = request.getDroneId();
        String destination = request.getDestination();

        DroneProgress progress = droneProgress.get(droneId);

        try {

            // Check for emergency abort before starting.
            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setStatus(DroneStatus.RUNNING);
            progress.setCurrentStep("Taking off");

            activities.takeoff(droneId);

            // Check for emergency abort after takeoff.
            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Navigating to destination");

            activities.navigate(
                    droneId,
                    destination);

            // Check for emergency abort after navigation.
            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Delivering package");

            try {

                activities.dropPackage(droneId);

            } catch (ActivityFailure deliveryFailure) {

                // If abort was requested, stop immediately.
                if (stopForEmergencyAbort(progress, droneId)) {
                    return;
                }

                progress.setCurrentStep(
                        "Delivery failed - recovering drone");

                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .warn(
                                "Delivery failed for drone {}. Starting recovery.",
                                droneId);

                activities.recoverDrone(droneId);

                // Check for emergency abort after recovery.
                if (stopForEmergencyAbort(progress, droneId)) {
                    return;
                }

                progress.setCurrentStep(
                        "Recovery completed - retrying delivery");

                activities.dropPackage(droneId);
            }

            // Check for emergency abort after delivery.
            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Returning to base");

            activities.returnToBase(droneId);

            progress.setCurrentStep("Completed");
            progress.setStatus(DroneStatus.COMPLETED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .info(
                            "Drone {} completed mission {}",
                            droneId,
                            missionId);

        } catch (RuntimeException exception) {

            /*
             * If the emergency abort caused this interruption,
             * the abort method is responsible for setting the
             * appropriate status.
             */
            if (progress.getStatus() != DroneStatus.ABORTED) {

                progress.setStatus(DroneStatus.FAILED);
                progress.setCurrentStep("Failed");

                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .error(
                                "Drone {} failed in mission {}",
                                droneId,
                                missionId,
                                exception);
            }
        }
    }

    private boolean stopForEmergencyAbort(
            DroneProgress progress,
            String droneId) {

        if (!emergencyAbortRequested) {
            return false;
        }

        /*
         * Do not process the same drone more than once.
         */
        if (progress.getStatus() == DroneStatus.COMPLETED
                || progress.getStatus() == DroneStatus.FAILED
                || progress.getStatus() == DroneStatus.ABORTED) {

            return true;
        }

        missionStatus = MissionStatus.ABORTING;

        progress.setCurrentStep(
                "Emergency abort - stopping mission");

        try {

            /*
             * Only a running drone needs to be returned to base.
             * A pending drone has not started its mission yet.
             */
            if (progress.getStatus() == DroneStatus.RUNNING) {

                activities.returnToBase(droneId);
            }

            /*
             * Abort operation completed successfully.
             */
            progress.setCurrentStep("Aborted");
            progress.setStatus(DroneStatus.ABORTED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .warn(
                            "Drone {} safely aborted in mission {}",
                            droneId,
                            missionId);

            return true;

        } catch (RuntimeException safetyFailure) {

            /*
             * The emergency safety operation itself failed.
             */
            progress.setCurrentStep(
                    "Emergency safety operation failed");

            progress.setStatus(DroneStatus.FAILED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error(
                            "Emergency safety operation failed for drone {}",
                            droneId,
                            safetyFailure);

            return true;
        }
    }

    @Override
    public MissionProgress getMissionProgress() {

        return new MissionProgress(
                missionId,
                missionStatus,
                new ArrayList<>(droneProgress.values()));
    }
}