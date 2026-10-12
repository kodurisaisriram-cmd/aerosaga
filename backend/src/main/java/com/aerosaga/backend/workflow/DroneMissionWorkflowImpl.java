package com.aerosaga.backend.workflow;

import com.aerosaga.backend.activity.DroneActivity;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class DroneMissionWorkflowImpl
        implements DroneMissionWorkflow {

    private final DroneActivity activities =
            Workflow.newActivityStub(
                    DroneActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(
                                    Duration.ofSeconds(30))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setMaximumAttempts(3)
                                            .build())
                            .build()
            );

    @Override
    public void executeMission(
            String missionId,
            String droneId,
            String destination) {

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info("Mission {} started for drone {}",
                        missionId, droneId);

        activities.takeoff(droneId);

        activities.navigate(droneId, destination);

        activities.dropPackage(droneId);

        activities.returnToBase(droneId);

        Workflow.getLogger(DroneMissionWorkflowImpl.class)
                .info("Mission {} completed successfully",
                        missionId);
    }
}
```