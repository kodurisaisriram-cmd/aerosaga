package com.aerosaga.backend.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface 
public interface DroneMissionWorkflow {
           
    @WorkflowMethod 
    void executeMission(String missionId);
}
