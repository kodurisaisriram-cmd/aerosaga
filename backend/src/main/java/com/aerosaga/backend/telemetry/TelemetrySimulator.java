
package com.aerosaga.backend.telemetry;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
@EnableScheduling
public class TelemetrySimulator {

    private static final int TOTAL_DRONES = 50;
    private static final double BASE_LATITUDE = 12.9716;
    private static final double BASE_LONGITUDE = 77.5946;

    private final TelemetryWebSocketHandler handler;
    private final Map<Integer, DroneState> states =
            new ConcurrentHashMap<>();

    public TelemetrySimulator(TelemetryWebSocketHandler handler) {
        this.handler = handler;

        for (int i = 1; i <= TOTAL_DRONES; i++) {
            states.put(i, new DroneState(i));
        }
    }

    @Scheduled(fixedRate = 1000)
    public void sendTelemetry() {
        for (int i = 1; i <= TOTAL_DRONES; i++) {
            DroneState d = states.get(i);
            d.update();

            TelemetryMessage message = new TelemetryMessage();
            message.setDroneId(String.format("DRONE-%02d", i));
            message.setLatitude(d.latitude);
            message.setLongitude(d.longitude);
            message.setAltitude(d.altitude);
            message.setSpeed(d.speed);
            message.setBattery(d.battery);
            message.setStatus(d.offline ? "OFFLINE"
                    : d.returning ? "RETURNING" : "ONLINE");
            message.setMission(d.mission);
            message.setTimestamp(System.currentTimeMillis());

            handler.broadcastTelemetry(message);
        }
    }

    public List<Map<String, Object>> getMissions() {
    List<Map<String, Object>> missions = new ArrayList<>();

    for (int i = 1; i <= TOTAL_DRONES; i++) {
        DroneState d = states.get(i);

        String status = d.offline
                ? "OFFLINE"
                : d.returning
                    ? "RETURNING"
                    : d.mission;

        missions.add(Map.of(
                "missionId", String.format("MISSION-%03d", i),
                "droneId", String.format("DRONE-%02d", i),
                "status", status
        ));
    }

    return missions;
}

    private static class DroneState {
        double latitude;
        double longitude;
        double altitude;
        double speed;
        int battery;

        boolean offline;
        boolean returning;

        double directionLat;
        double directionLon;

        String mission = "TAKEOFF";
        int missionTicks = 0;

        DroneState(int id) {
            latitude = BASE_LATITUDE + ((id % 10) - 5) * 0.012;
            longitude = BASE_LONGITUDE + ((id / 10) - 2) * 0.015;
            altitude = 60 + (id % 8) * 10;
            speed = 20 + (id % 6) * 3;
            battery = 85 + (id % 16);
            offline = id == 7;

            ThreadLocalRandom r = ThreadLocalRandom.current();
            directionLat = r.nextDouble(-0.0008, 0.0008);
            directionLon = r.nextDouble(-0.0008, 0.0008);
        }

        void update() {
            if (offline) {
                speed = 0;
                return;
            }

            ThreadLocalRandom r = ThreadLocalRandom.current();

            // Drain battery slowly and monotonically.
            if (battery > 0 && r.nextDouble() < 0.05) {
                battery--;
            }

            if (battery <= 0) {
                battery = 0;
                offline = true;
                speed = 0;
                mission = "OFFLINE";
                return;
            }

            if (battery <= 20) {
                returning = true;
                mission = "RETURN TO BASE";
            } else {
                missionTicks++;

                if (missionTicks >= 10) {
                    missionTicks = 0;

                    switch (mission) {
                        case "TAKEOFF" -> mission = "NAVIGATE";
                        case "NAVIGATE" -> mission = "DROP";
                        case "DROP" -> mission = "RETURN TO BASE";
                        default -> mission = "TAKEOFF";
                    }
                }

                returning = "RETURN TO BASE".equals(mission);
            }

            if (returning) {
                latitude += Math.signum(BASE_LATITUDE - latitude) * 0.0004;
                longitude += Math.signum(BASE_LONGITUDE - longitude) * 0.0004;
                speed = 15 + r.nextDouble(0, 10);
            } else {
                if (r.nextDouble() < 0.25) {
                    directionLat = r.nextDouble(-0.0008, 0.0008);
                    directionLon = r.nextDouble(-0.0008, 0.0008);
                }

                latitude += directionLat;
                longitude += directionLon;
                speed = Math.max(10, Math.min(55, speed + r.nextDouble(-3, 3)));
            }

            altitude = Math.max(30,
                    Math.min(150, altitude + r.nextDouble(-5, 5)));
        }
    }
}
