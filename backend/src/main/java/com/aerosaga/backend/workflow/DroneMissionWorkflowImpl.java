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

    /*
     * Emergency abort flag.
     * Temporal workflow state is accessed from the workflow thread,
     * so volatile/synchronized is not required.
     */
    private boolean emergencyAbortRequested = false;

    /*
     * Mission-level progress information.
     */
    private String currentStep = "Pending";
    private Double progress = 0.0;
    private String recoveryStatus = "NOT_STARTED";

    /*
     * Stores progress of every drone.
     */
    private final Map<String, DroneProgress> droneProgress =
            new LinkedHashMap<>();

    /*
     * Stores the cancellation scope of every currently running activity.
     * When emergency abort is requested, these scopes are cancelled.
     */
    private final Map<String, CancellationScope> activeActivityScopes =
            new LinkedHashMap<>();

    /*
     * Temporal Activity Stub.
     */
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
     * ============================================================
     * EXECUTE MISSION
     * ============================================================
     */
    @Override
    public void executeMission(
            String missionId,
            List<DroneMissionRequest> requests) {

        /*
         * Validate mission ID.
         */
        if (missionId == null || missionId.isBlank()) {

            this.missionStatus = MissionStatus.FAILED;
            this.currentStep = "Failed: Mission ID is required";
            this.recoveryStatus = "FAILED";

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error("Mission failed: Mission ID is required");

            return;
        }

        /*
         * Validate drone list.
         */
        if (requests == null || requests.isEmpty()) {

            this.missionId = missionId;
            this.missionStatus = MissionStatus.FAILED;
            this.currentStep = "Failed: At least one drone is required";
            this.recoveryStatus = "FAILED";

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error(
                            "Mission {} failed: At least one drone is required",
                            missionId);

            return;
        }

        this.missionId = missionId;

        /*
         * Reset state for this mission.
         */
        droneProgress.clear();
        activeActivityScopes.clear();
        emergencyAbortRequested = false;

        /*
         * Validate every drone request.
         */
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
                invalidReason =
                        "Drone ID and destination are required";

                break;
            }

            if (!droneIds.add(request.getDroneId())) {

                hasInvalidDrone = true;
                invalidReason =
                        "Duplicate drone ID: " + request.getDroneId();

                break;
            }
        }

        /*
         * Stop mission if validation failed.
         */
        if (hasInvalidDrone) {

            this.missionStatus = MissionStatus.FAILED;
            this.currentStep = "Failed: " + invalidReason;
            this.recoveryStatus = "FAILED";

            Workflow.getLogger(DroneMissionWorkflowImpl.class)
                    .error(
                            "Mission {} failed: {}",
                            missionId,
                            invalidReason);

            return;
        }

        /*
         * Initialize mission.
         */
        this.missionStatus = MissionStatus.IN_PROGRESS;
        this.currentStep = "Taking off";
        this.progress = 10.0;
        this.recoveryStatus = "NOT_STARTED";

        /*
         * Initialize drone progress.
         */
        for (DroneMissionRequest request : requests) {

            droneProgress.put(
                    request.getDroneId(),
                    new DroneProgress(
                            request.getDroneId(),
                            request.getDestination(),
                            "Waiting to start",
                            DroneStatus.PENDING));
        }

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info(
                        "Mission {} started with {} drones",
                        missionId,
                        requests.size());

        /*
         * Start all drone missions asynchronously.
         */
        List<Promise<Void>> dronePromises =
                new ArrayList<>();

        for (DroneMissionRequest request : requests) {

            dronePromises.add(
                    Async.procedure(
                            () -> executeSingleDroneMission(request)));
        }

        /*
         * Wait until all drone workflows finish.
         */
        Promise.allOf(
                dronePromises.toArray(
                        new Promise<?>[0]))
                .get();

        /*
         * Calculate final mission status.
         */
        boolean allCompleted = true;
        boolean anyFailed = false;
        boolean anyAborted = false;

        int completedCount = 0;

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

        /*
         * If emergency abort was requested, mark unfinished drones
         * as aborted.
         */
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

        /*
         * Determine final mission state.
         */
        if (anyFailed) {

            missionStatus = MissionStatus.FAILED;
            currentStep = "Failed";

            progress =
                    (completedCount * 100.0)
                            / requests.size();

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

            progress =
                    (completedCount * 100.0)
                            / requests.size();

            recoveryStatus = "FAILED";
        }

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info(
                        "Mission {} finished with status: {}",
                        missionId,
                        missionStatus);
    }

    /*
     * ============================================================
     * EXECUTE SINGLE DRONE MISSION
     * ============================================================
     */
    private void executeSingleDroneMission(
            DroneMissionRequest request) {

        String droneId = request.getDroneId();
        String destination = request.getDestination();

        DroneProgress drone =
                droneProgress.get(droneId);

        try {

            /*
             * Check whether abort was already requested.
             */
            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            /*
             * ----------------------------------------------------
             * TAKEOFF
             * ----------------------------------------------------
             */
            drone.setStatus(DroneStatus.RUNNING);
            drone.setCurrentStep("Taking off");

            currentStep = "Taking off";
            progress = Math.max(progress, 25.0);

            executeCancelableActivity(
                    droneId,
                    () -> activities.takeoff(droneId));

            Workflow.await(
                    Duration.ofSeconds(15),
                    () -> emergencyAbortRequested);

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            /*
             * ----------------------------------------------------
             * NAVIGATION
             * ----------------------------------------------------
             */
            drone.setCurrentStep(
                    "Navigating to destination");

            currentStep = "Navigating";
            progress = Math.max(progress, 50.0);

            executeCancelableActivity(
                    droneId,
                    () -> activities.navigate(
                            droneId,
                            destination));

            Workflow.await(
                    Duration.ofSeconds(20),
                    () -> emergencyAbortRequested);

            if (stopForEmergencyAbort(drone, droneId)) {
                return;
            }

            /*
             * ----------------------------------------------------
             * DELIVERY
             * ----------------------------------------------------
             */
            drone.setCurrentStep(
                    "Delivering package");

            currentStep = "Delivering package";
            progress = Math.max(progress, 75.0);

            try {

                executeCancelableActivity(
                        droneId,
                        () -> activities.dropPackage(
                                droneId));

                Workflow.await(
                        Duration.ofSeconds(15),
                        () -> emergencyAbortRequested);

            } catch (ActivityFailure deliveryFailure) {

                /*
                 * If abort happened during delivery,
                 * do not start recovery.
                 */
                if (stopForEmergencyAbort(
                        drone,
                        droneId)) {

                    return;
                }

                /*
                 * Delivery failed.
                 * Start recovery.
                 */
                drone.setCurrentStep(
                        "Delivery failed - recovering drone");

                currentStep = "Recovering drone";
                recoveryStatus = "RECOVERING";

                Workflow.getLogger(
                        DroneMissionWorkflowImpl.class)
                        .warn(
                                "Delivery failed for drone {}. Starting recovery.",
                                droneId);

                executeCancelableActivity(
                        droneId,
                        () -> activities.recoverDrone(
                                droneId));

                Workflow.await(
                        Duration.ofSeconds(10),
                        () -> emergencyAbortRequested);

                if (stopForEmergencyAbort(
                        drone,
                        droneId)) {

                    return;
                }

                /*
                 * Retry delivery after recovery.
                 */
                drone.setCurrentStep(
                        "Recovery completed - retrying delivery");

                recoveryStatus =
                        "RETRYING_DELIVERY";

                executeCancelableActivity(
                        droneId,
                        () -> activities.dropPackage(
                                droneId));

                Workflow.await(
                        Duration.ofSeconds(10),
                        () -> emergencyAbortRequested);
            }

            if (stopForEmergencyAbort(
                    drone,
                    droneId)) {

                return;
            }

            /*
             * ----------------------------------------------------
             * RETURN TO BASE
             * ----------------------------------------------------
             */
            drone.setCurrentStep(
                    "Returning to base");

            currentStep = "Returning to base";
            progress = Math.max(progress, 90.0);

            missionStatus =
                    MissionStatus.RETURNING_TO_BASE;

            recoveryStatus =
                    "RETURNING_TO_BASE";

            executeCancelableActivity(
                    droneId,
                    () -> activities.returnToBase(
                            droneId));

            Workflow.await(
                    Duration.ofSeconds(15),
                    () -> emergencyAbortRequested);

            if (stopForEmergencyAbort(
                    drone,
                    droneId)) {

                return;
            }

            /*
             * ----------------------------------------------------
             * COMPLETED
             * ----------------------------------------------------
             */
            drone.setCurrentStep("Completed");
            drone.setStatus(DroneStatus.COMPLETED);

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .info(
                            "Drone {} completed mission {}",
                            droneId,
                            missionId);

        } catch (CanceledFailure cancellation) {

            /*
             * Activity was explicitly cancelled.
             */
            if (emergencyAbortRequested) {

                Workflow.getLogger(
                        DroneMissionWorkflowImpl.class)
                        .warn(
                                "Running activity cancelled for drone {}",
                                droneId);

                stopForEmergencyAbort(
                        drone,
                        droneId);

            } else {

                drone.setStatus(
                        DroneStatus.FAILED);

                drone.setCurrentStep(
                        "Activity cancelled");

                Workflow.getLogger(
                        DroneMissionWorkflowImpl.class)
                        .warn(
                                "Activity cancelled unexpectedly for drone {}",
                                droneId);
            }

        } catch (RuntimeException exception) {

            /*
             * Normal activity/runtime failure.
             */
            if (drone.getStatus()
                    != DroneStatus.ABORTED) {

                drone.setStatus(
                        DroneStatus.FAILED);

                drone.setCurrentStep(
                        "Failed");

                currentStep = "Failed";
                recoveryStatus = "FAILED";

                Workflow.getLogger(
                        DroneMissionWorkflowImpl.class)
                        .error(
                                "Drone {} failed in mission {}",
                                droneId,
                                missionId,
                                exception);
            }
        }
    }

    /*
     * ============================================================
     * CANCELABLE ACTIVITY
     * ============================================================
     *
     * Every running activity gets its own CancellationScope.
     * Emergency abort can cancel the scope from the signal handler.
     */
    private void executeCancelableActivity(
            String droneId,
            Runnable activityOperation) {

        CancellationScope scope =
                Workflow.newCancellationScope(
                        activityOperation);

        activeActivityScopes.put(
                droneId,
                scope);

        try {

            scope.run();

        } finally {

            activeActivityScopes.remove(
                    droneId);
        }
    }

    /*
     * ============================================================
     * EMERGENCY ABORT SIGNAL
     * ============================================================
     */
    @Override
    public void requestEmergencyAbort() {

        if (emergencyAbortRequested) {
            return;
        }

        /*
         * Ignore abort after mission is already completed,
         * failed, or aborted.
         */
        if (missionStatus == MissionStatus.COMPLETED
                || missionStatus == MissionStatus.FAILED
                || missionStatus == MissionStatus.ABORTED) {

            return;
        }

        emergencyAbortRequested = true;

        missionStatus =
                MissionStatus.ABORT_REQUESTED;

        currentStep =
                "Emergency abort requested - stopping mission";

        recoveryStatus =
                "ABORT_REQUESTED";

        Workflow.getLogger(
                DroneMissionWorkflowImpl.class)
                .warn(
                        "Emergency abort requested for mission {}",
                        missionId);

        /*
         * Explicitly cancel every currently running activity.
         */
        for (CancellationScope scope :
                new ArrayList<>(
                        activeActivityScopes.values())) {

            scope.cancel();
        }
    }

    /*
     * ============================================================
     * HANDLE EMERGENCY ABORT
     * ============================================================
     *
     * After the current activity is cancelled, the drone performs
     * a safe return-to-base operation.
     */
    private boolean stopForEmergencyAbort(
            DroneProgress drone,
            String droneId) {

        if (!emergencyAbortRequested) {
            return false;
        }

        /*
         * Already in a terminal state.
         */
        if (drone.getStatus()
                == DroneStatus.COMPLETED
                || drone.getStatus()
                == DroneStatus.FAILED
                || drone.getStatus()
                == DroneStatus.ABORTED) {

            return true;
        }

        /*
         * Mission is now actively aborting.
         */
        currentStep =
                "Aborting - returning drone to base";

        recoveryStatus =
                "ABORTING";

        missionStatus =
                MissionStatus.ABORTING;

        try {

            /*
             * If drone was flying, return it to base.
             */
            if (drone.getStatus()
                    == DroneStatus.RUNNING) {

                drone.setCurrentStep(
                        "Returning to base - aborting");

                /*
                 * This is the emergency safety operation.
                 * It is deliberately outside the cancelled
                 * normal activity scope.
                 */
                activities.returnToBase(
                        droneId);

                /*
                 * Simulated safe-return duration.
                 */
                Workflow.sleep(
                        Duration.ofSeconds(10));
            }

            /*
             * Mark drone as safely aborted.
             */
            drone.setStatus(
                    DroneStatus.ABORTED);

            drone.setCurrentStep(
                    "Aborted");

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .warn(
                            "Drone {} safely aborted in mission {}",
                            droneId,
                            missionId);

        } catch (RuntimeException safetyFailure) {

            drone.setCurrentStep(
                    "Emergency safety operation failed");

            drone.setStatus(
                    DroneStatus.FAILED);

            Workflow.getLogger(
                    DroneMissionWorkflowImpl.class)
                    .error(
                            "Emergency safety operation failed for drone {}",
                            droneId,
                            safetyFailure);
        }

        return true;
    }

    @Override
    public MissionProgress getMissionProgress() {
        throw new UnsupportedOperationException("Unimplemented method 'getMissionProgress'");
    }
}