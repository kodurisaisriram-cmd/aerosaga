
package com.aerosaga.backend.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CopyOnWriteArraySet<WebSocketSession> sessions =
            new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("Telemetry client connected: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("Telemetry client disconnected: " + session.getId());
    }

    public void broadcastTelemetry(TelemetryMessage telemetry) {
        try {
            String json = objectMapper.writeValueAsString(telemetry);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException exception) {
                        System.err.println(
                                "Failed to send telemetry: "
                                        + exception.getMessage());
                    }
                } else {
                    sessions.remove(session);
                }
            }
        } catch (IOException exception) {
            System.err.println(
                    "Failed to serialize telemetry: "
                            + exception.getMessage());
        }
    }

    public int getConnectedClientCount() {
        return sessions.size();
    }
}
