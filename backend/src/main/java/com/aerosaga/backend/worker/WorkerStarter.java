package com.aerosaga.backend.worker;

import com.aerosaga.backend.workflow.DroneMissionRequest;
import com.aerosaga.backend.workflow.DroneMissionWorkflow;
import com.aerosaga.backend.workflow.MissionProgress;
import com.aerosaga.backend.workflow.DroneProgress;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorkerStarter {

    public static void main(String[] args) throws Exception {

        WorkflowServiceStubs service =
                WorkflowServiceStubs.newLocalServiceStubs();

        try {
            WorkflowClient client =
                    WorkflowClient.newInstance(service);

            String missionId =
                    "mission-" + System.currentTimeMillis();

            DroneMissionWorkflow workflow =
                    client.newWorkflowStub(
                            DroneMissionWorkflow.class,
                            WorkflowOptions.newBuilder()
                                    .setTaskQueue(
                                            "DRONE_MISSION_TASK_QUEUE")
                                    .setWorkflowId(missionId)
                                    .build()
                    );

            List<DroneMissionRequest> requests = List.of(
                    new DroneMissionRequest(
                            "DRONE-01", "Zone-A"),
                    new DroneMissionRequest(
                            "DRONE-02", "Zone-B"),
                    new DroneMissionRequest(
                            "DRONE-03", "Zone-C")
            );

            System.out.println(
                    "Starting mission: " + missionId);

            // Start the workflow asynchronously.
            WorkflowClient.start(
                    workflow::executeMission,
                    missionId,
                    requests
            );

            // Obtain a future for the workflow result.
            CompletableFuture<Void> result =
                    WorkflowStub.fromTyped(workflow)
                            .getResultAsync(Void.class);

            // Query mission progress while it is running.
            while (!result.isDone()) {

                try {
                    MissionProgress progress =
                            workflow.getMissionProgress();

                    System.out.println(
                            "Mission status: "
                                    + progress.getStatus());

                    for (DroneProgress drone :
                            progress.getDrones()) {

                        System.out.println(
                                drone.getDroneId()
                                + " | "
                                + drone.getStatus()
                                + " | "
                                + drone.getCurrentStep());
                    }

                    System.out.println("--------------------");

                } catch (RuntimeException exception) {
                    // The workflow may finish between
                    // checking the result and querying it.
                    if (result.isDone()) {
                        break;
                    }
                    throw exception;
                }

                Thread.sleep(500);
            }

            // Wait for the final result and surface failures.
            result.get();

            System.out.println(
                    "Mission completed: " + missionId);

        } finally {
            service.shutdown();
        }
    }
}
