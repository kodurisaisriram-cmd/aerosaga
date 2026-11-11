package com.aerosaga.backend.workflow;

import com.aerosaga.backend.activity.DroneActivity;

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.CancellationScope;
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

    /*
     * Stores the currently running activity cancellation scope
     * for each drone.
     */
    private final Map<String, CancellationScope> activeActivityScopes =
            new LinkedHashMap<>();

    private final DroneActivity activities =
            Workflow.newActivityStub(
                    DroneActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(30))
                            .setHeartbeatTimeout(Duration.ofSeconds(2))
                            .setCancellationType(
                                    ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(1))
                                            .setBackoffCoefficient(2.0)
                                            .setMaximumInterval(Duration.ofSeconds(10))
                                            .setMaximumAttempts(3)
                                            .build())
                            .build());

    /*
     * Emergency abort signal handler.
     */
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

            /*
             * Cancel currently running activities for all drones.
             */
            for (CancellationScope scope :
                    new ArrayList<>(activeActivityScopes.values())) {

                scope.cancel();
            }
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
        activeActivityScopes.clear();

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

    /*
     * Runs an activity inside its own cancellation scope.
     */
    private void executeCancelableActivity(
            String droneId,
            Runnable activityOperation) {

        CancellationScope scope =
                Workflow.newCancellationScope(activityOperation::run);

        activeActivityScopes.put(droneId, scope);

        try {

            scope.run();

        } finally {

            activeActivityScopes.remove(droneId);
        }
    }

    private void executeSingleDroneMission(
            DroneMissionRequest request) {

        String droneId = request.getDroneId();
        String destination = request.getDestination();

        DroneProgress progress = droneProgress.get(droneId);

        try {

            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setStatus(DroneStatus.RUNNING);
            progress.setCurrentStep("Taking off");

            executeCancelableActivity(
                    droneId,
                    () -> activities.takeoff(droneId));

            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Navigating to destination");

            executeCancelableActivity(
                    droneId,
                    () -> activities.navigate(droneId, destination));

            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Delivering package");

            try {

                executeCancelableActivity(
                        droneId,
                        () -> activities.dropPackage(droneId));

            } catch (ActivityFailure deliveryFailure) {

                if (stopForEmergencyAbort(progress, droneId)) {
                    return;
                }

                progress.setCurrentStep(
                        "Delivery failed - recovering drone");

                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .warn(
                                "Delivery failed for drone {}. Starting recovery.",
                                droneId);

                executeCancelableActivity(
                        droneId,
                        () -> activities.recoverDrone(droneId));

                if (stopForEmergencyAbort(progress, droneId)) {
                    return;
                }

                progress.setCurrentStep(
                        "Recovery completed - retrying delivery");

                executeCancelableActivity(
                        droneId,
                        () -> activities.dropPackage(droneId));
            }

            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Returning to base");

            executeCancelableActivity(
                    droneId,
                    () -> activities.returnToBase(droneId));

            if (stopForEmergencyAbort(progress, droneId)) {
                return;
            }

            progress.setCurrentStep("Completed");
            progress.setStatus(DroneStatus.COMPLETED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .info(
                            "Drone {} completed mission {}",
                            droneId,
                            missionId);

        } catch (CanceledFailure cancellation) {

            /*
             * Temporal activity was cancelled.
             * Handle the emergency abort separately.
             */
            if (emergencyAbortRequested) {

                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .warn(
                                "Running activity cancelled for drone {}",
                                droneId);

                stopForEmergencyAbort(progress, droneId);

            } else {

                progress.setStatus(DroneStatus.FAILED);
                progress.setCurrentStep("Activity cancelled");

                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .warn(
                                "Activity cancelled unexpectedly for drone {}",
                                droneId);
            }

        } catch (RuntimeException exception) {

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

    /*
     * Handles emergency abort and performs a separate
     * return-to-base operation after the interrupted activity.
     */
    private boolean stopForEmergencyAbort(
            DroneProgress progress,
            String droneId) {

        if (!emergencyAbortRequested) {
            return false;
        }

        if (progress.getStatus() == DroneStatus.COMPLETED
                || progress.getStatus() == DroneStatus.FAILED
                || progress.getStatus() == DroneStatus.ABORTED) {

            return true;
        }

        missionStatus = MissionStatus.ABORTING;

        progress.setCurrentStep(
                "Emergency abort - stopping mission");

        try {

            if (progress.getStatus() == DroneStatus.RUNNING) {

                progress.setCurrentStep(
                        "Emergency abort - returning to base");

                /*
                 * This is a new activity scope.
                 * It is not part of the cancelled navigation scope.
                 */
                executeCancelableActivity(
                        droneId,
                        () -> activities.returnToBase(droneId));
            }

            progress.setCurrentStep("Aborted");
            progress.setStatus(DroneStatus.ABORTED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .warn(
                            "Drone {} safely aborted in mission {}",
                            droneId,
                            missionId);

            return true;

        } catch (RuntimeException safetyFailure) {

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