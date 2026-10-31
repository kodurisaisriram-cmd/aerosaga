
package com.aerosaga.backend.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

@WorkflowInterface
public interface DroneMissionWorkflow {

    @WorkflowMethod
    void executeMission(
            String missionId,
            List<DroneMissionRequest> requests
    );

    @QueryMethod
    MissionProgress getMissionProgress();
}

