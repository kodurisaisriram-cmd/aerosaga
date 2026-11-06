package com.aerosaga.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.recoveryStatus").value("NOT_STARTED"));
    }

    @Test
    void testCreateMission_MissingDroneId_ReturnsBadRequest() throws Exception {
        when(missionService.createMission(any(Mission.class)))
                .thenThrow(new IllegalArgumentException("Drone ID is required"));

        mockMvc.perform(post("/api/missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Drone ID is required"));
    }

    @Test
    void testCreateMission_MissingDestination_ReturnsBadRequest() throws Exception {
        when(missionService.createMission(any(Mission.class)))
                .thenThrow(new IllegalArgumentException("Destination is required"));

        mockMvc.perform(post("/api/missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"droneId\":\"DR-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Destination is required"));
    }

    @Test
    void testGetAllMissions_ReturnsList() throws Exception {
        Mission m1 = new Mission("m-1", "DR-01", "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionService.getAllMissions()).thenReturn(List.of(m1));

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].missionId").value("m-1"));
    }

    @Test
    void testGetMissionById_Success() throws Exception {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionService.getMissionById("m-1")).thenReturn(mission);

        mockMvc.perform(get("/api/missions/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void testGetMissionById_NotFound() throws Exception {
        when(missionService.getMissionById("unknown")).thenThrow(new IllegalArgumentException("Mission not found"));

        mockMvc.perform(get("/api/missions/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetMissionStatus_ReturnsStatusWithRecoveryAndProgress() throws Exception {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, 50.0, "Navigating", "NOT_STARTED", LocalDateTime.now(), LocalDateTime.now());
        when(missionService.getMissionById("m-1")).thenReturn(mission);

        mockMvc.perform(get("/api/missions/m-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.progress").value(50.0))
                .andExpect(jsonPath("$.currentStep").value("Navigating"))
                .andExpect(jsonPath("$.recoveryStatus").value("NOT_STARTED"));
    }

    @Test
    void testGetMissionProgress_ReturnsProgressAndRecoveryStatus() throws Exception {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.RETURNING_TO_BASE, 90.0, "Returning to base", "RETURNING_TO_BASE", LocalDateTime.now(), LocalDateTime.now());
        when(missionService.getMissionProgress("m-1")).thenReturn(mission);

        mockMvc.perform(get("/api/missions/m-1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.droneId").value("DR-01"))
                .andExpect(jsonPath("$.status").value("RETURNING_TO_BASE"))
                .andExpect(jsonPath("$.progress").value(90.0))
                .andExpect(jsonPath("$.currentStep").value("Returning to base"))
                .andExpect(jsonPath("$.recoveryStatus").value("RETURNING_TO_BASE"));
    }

    @Test
    void testStartMission_ReturnsOk() throws Exception {
        Mission started = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionService.startMission("m-1")).thenReturn(started);

        mockMvc.perform(post("/api/missions/m-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
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

    @Test
    void testStartMission_MissingDestination_ReturnsBadRequest() throws Exception {
        when(missionService.startMission("m-1"))
                .thenThrow(new IllegalArgumentException("Destination is required to start mission"));

        mockMvc.perform(post("/api/missions/m-1/start"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Destination is required to start mission"));
    }

    @Test
    void testUpdateMissionStatus_ReturnsOk() throws Exception {
        Mission updated = new Mission("m-1", "DR-01", "A", "B", MissionStatus.RETURNING_TO_BASE, 90.0, "Returning to base", "RETURNING_TO_BASE", LocalDateTime.now(), LocalDateTime.now());
        when(missionService.updateMissionStatus("m-1", MissionStatus.RETURNING_TO_BASE)).thenReturn(updated);

        mockMvc.perform(patch("/api/missions/m-1/status?status=RETURNING_TO_BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNING_TO_BASE"))
                .andExpect(jsonPath("$.recoveryStatus").value("RETURNING_TO_BASE"));
    }

    @Test
    void testUpdateMissionProgress_ReturnsOk() throws Exception {
        Mission updated = new Mission("m-1", "DR-01", "A", "B", MissionStatus.RETURNING_TO_BASE, 90.0, "Returning to base", "RETURNING_TO_BASE", LocalDateTime.now(), LocalDateTime.now());
        when(missionService.updateMissionProgress(eq("m-1"), eq(90.0), eq("Returning to base"), eq("RETURNING_TO_BASE"))).thenReturn(updated);

        mockMvc.perform(patch("/api/missions/m-1/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":90.0,\"currentStep\":\"Returning to base\",\"recoveryStatus\":\"RETURNING_TO_BASE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(90.0))
                .andExpect(jsonPath("$.currentStep").value("Returning to base"))
                .andExpect(jsonPath("$.recoveryStatus").value("RETURNING_TO_BASE"));
    }

    @Test
    void testAbortMission_ReturnsOk() throws Exception {
        Mission aborted = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORTED, 50.0, "Aborted", "ABORTED", LocalDateTime.now(), LocalDateTime.now());
        when(missionService.abortMission(eq("m-1"), any())).thenReturn(aborted);

        mockMvc.perform(post("/api/missions/m-1/abort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("ABORTED"))
                .andExpect(jsonPath("$.recoveryStatus").value("ABORTED"));
    }

    @Test
    void testAbortMission_WithReason_ReturnsOk() throws Exception {
        Mission aborted = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORTED, 50.0, "Aborted: Weather hazard", "ABORTED", LocalDateTime.now(), LocalDateTime.now());
        when(missionService.abortMission(eq("m-1"), eq("Weather hazard"))).thenReturn(aborted);

        mockMvc.perform(post("/api/missions/m-1/abort")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Weather hazard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("m-1"))
                .andExpect(jsonPath("$.status").value("ABORTED"))
                .andExpect(jsonPath("$.currentStep").value("Aborted: Weather hazard"));
    }

    @Test
    void testAbortMission_NotFound_Returns404() throws Exception {
        when(missionService.abortMission(eq("m-unknown"), any()))
                .thenThrow(new IllegalArgumentException("Mission not found: m-unknown"));

        mockMvc.perform(post("/api/missions/m-unknown/abort"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Mission not found: m-unknown"));
    }

    @Test
    void testAbortMission_Conflict_Returns409() throws Exception {
        when(missionService.abortMission(eq("m-1"), any()))
                .thenThrow(new IllegalStateException("Cannot abort a completed mission"));

        mockMvc.perform(post("/api/missions/m-1/abort"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Cannot abort a completed mission"));
    }

    @Test
    void testUpdateMissionStatus_NotFound_Returns404() throws Exception {
        when(missionService.updateMissionStatus(eq("m-unknown"), eq(MissionStatus.ACTIVE)))
                .thenThrow(new IllegalArgumentException("Mission not found: m-unknown"));

        mockMvc.perform(patch("/api/missions/m-unknown/status?status=ACTIVE"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Mission not found: m-unknown"));
    }

    @Test
    void testUpdateMissionStatus_Conflict_Returns409() throws Exception {
        when(missionService.updateMissionStatus(eq("m-1"), eq(MissionStatus.ACTIVE)))
                .thenThrow(new IllegalStateException("Cannot change status of a completed mission"));

        mockMvc.perform(patch("/api/missions/m-1/status?status=ACTIVE"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Cannot change status of a completed mission"));
    }

    @Test
    void testUpdateMissionProgress_ProgressOutOfBounds_Returns400() throws Exception {
        mockMvc.perform(patch("/api/missions/m-1/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":150.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Progress must be between 0 and 100"));
    }

    @Test
    void testUpdateMissionProgress_Conflict_Returns409() throws Exception {
        when(missionService.updateMissionProgress(eq("m-1"), any(), any(), any()))
                .thenThrow(new IllegalStateException("Cannot update progress of an aborted mission"));

        mockMvc.perform(patch("/api/missions/m-1/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":50.0}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Cannot update progress of an aborted mission"));
    }
}


