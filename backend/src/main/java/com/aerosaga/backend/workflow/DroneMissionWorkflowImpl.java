package com.aerosaga.backend.workflow;

import com.aerosaga.backend.activity.DroneActivity;
import com.aerosaga.backend.model.MissionStatus;

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


    // Temporal workflows execute on a single deterministic thread. Signals are
    // delivered by the Temporal worker on the same workflow thread before the
    // next workflow task begins, so no Java visibility guarantees (volatile/sync)
    // are needed. Using volatile here would be incorrect and misleading.
    private boolean emergencyAbortRequested = false;

    // Mission-level progress and recovery details
    private String currentStep = "Pending";
    private Double progress = 0.0;
    private String recoveryStatus = "NOT_STARTED";

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
            this.missionStatus = MissionStatus.FAILED;
            this.currentStep = "Failed: Mission ID is required";
            this.recoveryStatus = "FAILED";
            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error("Mission failed: Mission ID is required");
            return;
        }

        if (requests == null || requests.isEmpty()) {

        if (requests == null || requests.isEmpty()) {
            this.missionId = missionId;
            this.missionStatus = MissionStatus.FAILED;
            this.currentStep = "Failed: At least one drone is required";
            this.recoveryStatus = "FAILED";
            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error("Mission {} failed: At least one drone is required", missionId);
            return;
        }

        this.missionId = missionId;

        droneProgress.clear();
        activeActivityScopes.clear();


        // Safely validate drone requests so replayed or invalid executions complete as FAILED
        boolean hasInvalidDrone = false;
        String invalidReason = null;
        Set<String> droneIds = new HashSet<>();

        for (DroneMissionRequest request : requests) {

            if (request == null
                    || request.getDroneId() == null
                    || request.getDroneId().isBlank()
                    || request.getDestination() == null
                    || request.getDestination().isBlank()) {

                hasInvalidDrone = true;
                invalidReason = "Drone ID and destination are required";
                break;
            }

            if (!droneIds.add(request.getDroneId())) {
                hasInvalidDrone = true;
                invalidReason = "Duplicate drone ID: " + request.getDroneId();
                break;
            }

            }
        }

        if (hasInvalidDrone) {
            this.missionStatus = MissionStatus.FAILED;
            this.currentStep = "Failed: " + invalidReason;
            this.recoveryStatus = "FAILED";
            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error("Mission {} failed: {}", missionId, invalidReason);
            return;
        }

        this.missionStatus = MissionStatus.ACTIVE;
        this.currentStep = "Taking off";
        this.progress = 10.0;
        this.recoveryStatus = "NOT_STARTED";

        droneProgress.clear();

        for (DroneMissionRequest request : requests) {
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

        // Start every drone mission asynchronously.
        List<Promise<Void>> dronePromises = new ArrayList<>();

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

        int completedCount = 0;
        boolean anyFailed = false;
        boolean anyAborted = false;

        for (DroneProgress drone : droneProgress.values()) {

            if (drone.getStatus() == DroneStatus.COMPLETED) {
                completedCount++;
            } else {
                allCompleted = false;
            }

            if (drone.getStatus() == DroneStatus.FAILED) {
                anyFailed = true;
            }

            if (drone.getStatus() == DroneStatus.ABORTED) {
                anyAborted = true;
            }
        }

        if (emergencyAbortRequested) {
            for (DroneProgress drone : droneProgress.values()) {
                if (drone.getStatus() != DroneStatus.COMPLETED
                        && drone.getStatus() != DroneStatus.FAILED) {
                    drone.setStatus(DroneStatus.ABORTED);
                    drone.setCurrentStep("Aborted");
                    anyAborted = true;
                }
            }
        }

        if (anyFailed) {
            missionStatus = MissionStatus.FAILED;
            currentStep = "Failed";
            progress = (completedCount * 100.0) / requests.size();
            recoveryStatus = "FAILED";

        } else if (emergencyAbortRequested || anyAborted) {
            missionStatus = MissionStatus.ABORTED;
            currentStep = "Aborted";
            recoveryStatus = "ABORTED";

        } else if (allCompleted) {
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

        }

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info(
                        "Mission {} finished with status: {}",
                        missionId,
                        missionStatus);
    }



        String droneId = request.getDroneId();
        String destination = request.getDestination();


        DroneProgress drone = droneProgress.get(droneId);

        try {

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            drone.setStatus(DroneStatus.RUNNING);
            drone.setCurrentStep("Taking off");

            currentStep = "Taking off";
            progress = Math.max(progress, 25.0);

            executeCancelableActivity(
                    droneId,
                    () -> activities.takeoff(droneId));

            Workflow.await(Duration.ofSeconds(15),
                    () -> emergencyAbortRequested);

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            drone.setCurrentStep("Navigating to destination");

            currentStep = "Navigating";
            progress = Math.max(progress, 50.0);

            executeCancelableActivity(
                    droneId,
                    () -> activities.navigate(droneId, destination));

            Workflow.await(Duration.ofSeconds(20),
                    () -> emergencyAbortRequested);

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            drone.setCurrentStep("Delivering package");


            currentStep = "Delivering package";
            progress = Math.max(progress, 75.0);

            try {

                executeCancelableActivity(
                        droneId,
                        () -> activities.dropPackage(droneId));

                Workflow.await(Duration.ofSeconds(15),
                        () -> emergencyAbortRequested);

            } catch (ActivityFailure deliveryFailure) {

                if (stopForEmergencyAbort(drone, droneId)) {
                    return;
                }

                drone.setCurrentStep(
                        "Delivery failed - recovering drone");

                currentStep = "Recovering drone";
                recoveryStatus = "RECOVERING";


                Workflow.getLogger(DroneMissionWorkflowImpl.class)
                        .warn(
                                "Delivery failed for drone {}. Starting recovery.",
                                droneId);


                executeCancelableActivity(
                        droneId,
                        () -> activities.recoverDrone(droneId));

                Workflow.await(Duration.ofSeconds(10),
                        () -> emergencyAbortRequested);

                if (stopForEmergencyAbort(drone, droneId)) {

                    return;
                }

                drone.setCurrentStep(
                        "Recovery completed - retrying delivery");


                recoveryStatus = "RETRYING_DELIVERY";

                executeCancelableActivity(
                        droneId,
                        () -> activities.dropPackage(droneId));

                Workflow.await(Duration.ofSeconds(10),
                        () -> emergencyAbortRequested);
            }

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            drone.setCurrentStep("Returning to base");

            currentStep = "Returning to base";
            progress = Math.max(progress, 90.0);
            missionStatus = MissionStatus.RETURNING_TO_BASE;
            recoveryStatus = "RETURNING_TO_BASE";

            executeCancelableActivity(
                    droneId,
                    () -> activities.returnToBase(droneId));

            Workflow.await(Duration.ofSeconds(15),
                    () -> emergencyAbortRequested);

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }


            drone.setCurrentStep("Completed");
            drone.setStatus(DroneStatus.COMPLETED);

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


            if (drone.getStatus() != DroneStatus.ABORTED) {

                drone.setStatus(DroneStatus.FAILED);
                drone.setCurrentStep("Failed");

                currentStep = "Failed";
                recoveryStatus = "FAILED";

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
            DroneProgress drone,
            String droneId) {


            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error(
                            "Emergency safety operation failed for drone {}",
                            droneId,
                            safetyFailure);

            return true;
        }
    }

    @Override
    public void requestEmergencyAbort() {
        if (!emergencyAbortRequested) {
            emergencyAbortRequested = true;
            // Set ABORT_REQUESTED to signal intent; the actual ABORTING/ABORTED
            // transitions happen inside stopForEmergencyAbort() as each drone
            // completes its safe-return/abort procedure.
            this.missionStatus = MissionStatus.ABORT_REQUESTED;
            this.currentStep = "Emergency abort requested - stopping mission";
            this.recoveryStatus = "ABORT_REQUESTED";

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .warn("Emergency abort requested for mission {}", missionId);
        }
    }

    private boolean stopForEmergencyAbort(DroneProgress drone, String droneId) {
        if (!emergencyAbortRequested) {
            return false;
        }

        if (drone.getStatus() == DroneStatus.COMPLETED
                || drone.getStatus() == DroneStatus.FAILED
                || drone.getStatus() == DroneStatus.ABORTED) {
            return true;
        }

        // Transition to ABORTING while safe-return/abort is in progress
        currentStep = "Aborting - returning drone to base";
        recoveryStatus = "ABORTING";
        missionStatus = MissionStatus.ABORTING;

        try {
            if (drone.getStatus() == DroneStatus.RUNNING) {
                drone.setCurrentStep("Returning to base - aborting");
                activities.returnToBase(droneId);
                // Simulate safe-return flight duration so system stays in ABORTING state
                Workflow.sleep(Duration.ofSeconds(10));
            }
            drone.setStatus(DroneStatus.ABORTED);
            drone.setCurrentStep("Aborted");
            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .warn("Drone {} safely aborted in mission {}",
                            droneId, missionId);
        } catch (RuntimeException safetyFailure) {
            drone.setCurrentStep("Emergency safety operation failed");
            drone.setStatus(DroneStatus.FAILED);

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error("Emergency safety operation failed for drone {}",
                            droneId, safetyFailure);
        }
        return true;
    }

    @Override
    public MissionProgress getMissionProgress() {


        // Preserve teammate's drone-level progress list.
        return new MissionProgress(

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