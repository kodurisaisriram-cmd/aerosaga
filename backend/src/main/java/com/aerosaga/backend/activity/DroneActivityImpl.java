package com.aerosaga.backend.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DroneActivityImpl implements DroneActivity {

    private static final Logger log =
            LoggerFactory.getLogger(DroneActivityImpl.class);

    @Override
    public void takeoff(String droneId) {
        log.info("Drone {} is taking off...", droneId);
        log.info("Drone {} has taken off successfully.", droneId);
    }

    @Override
    public void navigate(String droneId, String destination) {
        log.info("Drone {} is navigating to {}...",
                droneId, destination);
        log.info("Drone {} reached destination {}.",
                droneId, destination);
    }

    @Override
    public void dropPackage(String droneId) {
        log.info("Drone {} is dropping the package...", droneId);
        log.info("Package dropped successfully by drone {}.", droneId);
    }

    @Override
    public void returnToBase(String droneId) {
        log.info("Drone {} is returning to base...", droneId);
        log.info("Drone {} has returned to base.", droneId);
    }
}