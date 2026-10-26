
package com.aerosaga.backend.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DroneActivityImpl implements DroneActivity {

    private static final Logger log =
            LoggerFactory.getLogger(DroneActivityImpl.class);

    private final ConcurrentHashMap<String, AtomicInteger> deliveryAttempts =
            new ConcurrentHashMap<>();

    @Override
    public void takeoff(String droneId) {
        log.info("Drone {} is taking off...", droneId);
        log.info("Drone {} has taken off successfully.", droneId);
    }

    @Override
    public void navigate(String droneId, String destination) {
        log.info("Drone {} is navigating to {}...", droneId, destination);
        log.info("Drone {} reached destination {}.", droneId, destination);
    }

    @Override
    public void dropPackage(String droneId) {
        int attempt = deliveryAttempts
                .computeIfAbsent(droneId, key -> new AtomicInteger(0))
                .incrementAndGet();

        String failureMode = System.getProperty(
                "aerosaga.test.fail-delivery", "none");

        if ("all".equalsIgnoreCase(failureMode)) {
            log.warn("Simulated delivery failure for drone {} on attempt {}",
                    droneId, attempt);
            throw new IllegalStateException(
                    "Simulated delivery failure for drone " + droneId);
        }

        if ("first".equalsIgnoreCase(failureMode) && attempt <= 3) {
            log.warn(
                    "Simulated initial delivery failure for drone {} on activity attempt {}",
                    droneId, attempt);
            throw new IllegalStateException(
                    "Simulated initial delivery failure for drone " + droneId);
        }

        log.info("Drone {} is dropping the package...", droneId);
        log.info("Package dropped successfully by drone {}.", droneId);
    }

    @Override
    public void recoverDrone(String droneId) {
        log.warn("Recovery initiated for drone {}", droneId);
        log.info("Recovery operation completed for drone {}", droneId);
    }

    @Override
    public void returnToBase(String droneId) {
        log.info("Drone {} is returning to base...", droneId);
        log.info("Drone {} has returned to base.", droneId);
    }
}
