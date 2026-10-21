package com.aerosaga.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.aerosaga.backend.model.Telemetry;
import com.aerosaga.backend.service.TelemetryService;

@WebMvcTest(TelemetryController.class)
public class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelemetryService telemetryService;

    @Test
    void testSaveTelemetry_ReturnsCreated() throws Exception {
        Telemetry saved = new Telemetry("DR-02", 19.076, 72.877, 150.0, 80.0, LocalDateTime.now());
        saved.setId(1L);
        saved.setMissionId("m-1");

        when(telemetryService.saveTelemetry(any(Telemetry.class))).thenReturn(saved);

        mockMvc.perform(post("/api/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"droneId\":\"DR-02\",\"missionId\":\"m-1\",\"latitude\":19.076,\"longitude\":72.877,\"altitude\":150.0,\"batteryLevel\":80.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.droneId").value("DR-02"))
                .andExpect(jsonPath("$.missionId").value("m-1"));
    }

    @Test
    void testSaveTelemetry_Invalid_ReturnsBadRequest() throws Exception {
        when(telemetryService.saveTelemetry(any(Telemetry.class)))
                .thenThrow(new IllegalArgumentException("Mission ID is required"));

        mockMvc.perform(post("/api/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"droneId\":\"DR-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Mission ID is required"));
    }

    @Test
    void testGetLatestTelemetry_ReturnsOk() throws Exception {
        Telemetry latest = new Telemetry("DR-02", 19.076, 72.877, 150.0, 80.0, LocalDateTime.now());
        latest.setId(10L);
        when(telemetryService.getLatestTelemetry("DR-02")).thenReturn(latest);

        mockMvc.perform(get("/api/telemetry/drone/DR-02/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.droneId").value("DR-02"))
                .andExpect(jsonPath("$.latitude").value(19.076));
    }
}
