package com.aerosaga.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.model.Telemetry;
import com.aerosaga.backend.model.TelemetryMessage;
import com.aerosaga.backend.repository.MissionRepository;
import com.aerosaga.backend.repository.TelemetryRepository;
import com.aerosaga.backend.service.impl.TelemetryServiceImpl;

@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private TelemetryWebSocketService webSocketService;

    private TelemetryServiceImpl telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new TelemetryServiceImpl(telemetryRepository, missionRepository, webSocketService);
    }

    @Test
    void testSaveTelemetry_Success_BroadcastsWebSocket() {
        Telemetry input = new Telemetry();
        input.setDroneId("DR-02");
        input.setMissionId("m-100");
        input.setLatitude(19.076);
        input.setLongitude(72.877);
        input.setAltitude(120.0);
        input.setBatteryLevel(88.0);

        Mission mission = new Mission("m-100", "DR-02", "A", "B", MissionStatus.IN_PROGRESS, LocalDateTime.now());
        when(missionRepository.findByMissionIdAndDroneId("m-100", "DR-02")).thenReturn(Optional.of(mission));
        when(telemetryRepository.save(any(Telemetry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Telemetry saved = telemetryService.saveTelemetry(input);

        assertNotNull(saved);
        assertEquals("DR-02", saved.getDroneId());
        assertNotNull(saved.getTimestamp());

        // Verify WebSocket broadcasts
        ArgumentCaptor<TelemetryMessage> msgCaptor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(webSocketService).publishTelemetry(msgCaptor.capture());
        verify(webSocketService).publishMissionTelemetry(eq("m-100"), msgCaptor.capture());
        verify(webSocketService).publishDroneTelemetry(eq("DR-02"), msgCaptor.capture());

        TelemetryMessage captured = msgCaptor.getValue();
        assertEquals("DR-02", captured.getDroneId());
        assertEquals("m-100", captured.getMissionId());
        assertEquals(19.076, captured.getLatitude());
        assertEquals(88.0, captured.getBatteryLevel());
    }

    @Test
    void testSaveTelemetry_MissionNotFound_ThrowsException() {
        Telemetry input = new Telemetry();
        input.setDroneId("DR-02");
        input.setMissionId("m-invalid");

        when(missionRepository.findByMissionIdAndDroneId("m-invalid", "DR-02")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> telemetryService.saveTelemetry(input));
        verify(telemetryRepository, never()).save(any());
        verify(webSocketService, never()).publishTelemetry(any());
    }
}
