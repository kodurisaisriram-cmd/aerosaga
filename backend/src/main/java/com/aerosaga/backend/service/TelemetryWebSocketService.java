
package com.aerosaga.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.aerosaga.backend.model.TelemetryMessage;

@Service
public class TelemetryWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public TelemetryWebSocketService(
            SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTelemetry(TelemetryMessage telemetryMessage) {
        messagingTemplate.convertAndSend(
                "/topic/telemetry",
                telemetryMessage
        );
    }

    public void publishMissionTelemetry(
            String missionId,
            TelemetryMessage telemetryMessage) {

        messagingTemplate.convertAndSend(
                "/topic/missions/" + missionId + "/telemetry",
                telemetryMessage
        );
    }

    public void publishDroneTelemetry(
            String droneId,
            TelemetryMessage telemetryMessage) {

        messagingTemplate.convertAndSend(
                "/topic/drones/" + droneId + "/telemetry",
                telemetryMessage
        );
    }
}
