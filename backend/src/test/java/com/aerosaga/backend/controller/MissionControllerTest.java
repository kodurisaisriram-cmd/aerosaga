package com.aerosaga.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.service.MissionService;

@WebMvcTest(MissionController.class)
public class MissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MissionService missionService;

    @Test
    void testCreateMission_ReturnsCreated() throws Exception {
        Mission created = new Mission("m-1", "DR-01", "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionService.createMission(any(Mission.class))).thenReturn(created);

        mockMvc.perform(post("/api/missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"droneId\":\"DR-01\",\"source\":\"A\",\"destination\":\"B\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void testGetMissionStatus_ReturnsStatus() throws Exception {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.IN_PROGRESS, LocalDateTime.now());
        when(missionService.getMissionById("m-1")).thenReturn(mission);

        mockMvc.perform(get("/api/missions/m-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void testStartMission_ReturnsOk() throws Exception {
        Mission started = new Mission("m-1", "DR-01", "A", "B", MissionStatus.IN_PROGRESS, LocalDateTime.now());
        when(missionService.startMission("m-1")).thenReturn(started);

        mockMvc.perform(post("/api/missions/m-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void testStartMission_NotFound_Returns404() throws Exception {
        when(missionService.startMission("invalid")).thenThrow(new IllegalArgumentException("Mission not found"));

        mockMvc.perform(post("/api/missions/invalid/start"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testStartMission_Conflict_Returns409() throws Exception {
        when(missionService.startMission("m-1")).thenThrow(new IllegalStateException("Not pending"));

        mockMvc.perform(post("/api/missions/m-1/start"))
                .andExpect(status().isConflict());
    }
}
