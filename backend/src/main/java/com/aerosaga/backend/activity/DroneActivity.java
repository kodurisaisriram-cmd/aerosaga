package com.aerosaga.backend.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

//defines activities that can be executed during a drone mission.
@ActivityInterface
public interface DroneActivity {
    
    /*
     * Checks whether the drone is ready to start a mission.
     * @param missionId is unique identifier for the mission
     * @return true if the drone is ready, false otherwise
     */
    @ActivityMethod
    boolean checkDroneReadiness(String missionId);

    /*
    * starts the drone mission for the given missionId.
    * @param missionId is unique identifier for the mission
    * @return status message describing the result of the launch operation
     */
    @ActivityMethod 
    String launchDrone(String missionId);

    /*
    * executes the drone mission for the given missionId.
    * @param missionId is unique identifier for the mission
    * @return status message describing the result of the mission execution
    */
    @ActivityMethod 
    String executeDroneMission(String missionId);

    /*
    * returns the drone safely after the mission for the given missionId.
    * @param missionId is unique identifier for the mission
    * @return status message describing the result of the return operation
     */
    @ActivityMethod 
    String returnDrone(String missionId);

    /*
    * handles the failure of a drone mission for the given missionId.
    * @param missionId is unique identifier for the mission
    * @param reason is the reason for the failure
    * @return status message describing the result of the failure handling
     */
    @ActivityMethod 
    String handleMissionFailure(String missionId, String reason);
}
