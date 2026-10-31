package com.aerosaga.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aerosaga.backend.model.Mission;
import com.aerosaga.backend.model.MissionStatus;
import com.aerosaga.backend.repository.MissionRepository;
import com.aerosaga.backend.service.impl.MissionServiceImpl;
import com.aerosaga.backend.workflow.DroneMissionWorkflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@ExtendWith(MockitoExtension.class)
public class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private WorkflowClient workflowClient;

    @Mock
    private DroneMissionWorkflow droneMissionWorkflow;

    private MissionServiceImpl missionService;

    @BeforeEach
    void setUp() {
        missionService = new MissionServiceImpl(missionRepository, workflowClient);
    }

    @Test
    void testCreateMission_Success() {
        Mission input = new Mission();
        input.setDroneId("DR-01");
        input.setSource("Warehouse-A");
        input.setDestination("Delivery-B");

        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission created = missionService.createMission(input);

        assertNotNull(created.getMissionId());
        assertEquals("DR-01", created.getDroneId());
        assertEquals(MissionStatus.PENDING, created.getStatus());
        assertNotNull(created.getCreatedAt());
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void testCreateMission_MissingDroneId_ThrowsException() {
        Mission input = new Mission();
        input.setDroneId(null);

        assertThrows(IllegalArgumentException.class, () -> missionService.createMission(input));
    }

    @Test
    void testGetMissionById_Success() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        Mission result = missionService.getMissionById("m-1");

        assertNotNull(result);
        assertEquals("m-1", result.getMissionId());
    }

    @Test
    void testGetMissionById_NotFound_ThrowsException() {
        when(missionRepository.findById("m-99")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> missionService.getMissionById("m-99"));
    }

    @Test
    void testStartMission_Success() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(droneMissionWorkflow);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission started = missionService.startMission("m-1");

        assertEquals(MissionStatus.ACTIVE, started.getStatus());
        assertEquals("Taking off", started.getCurrentStep());
        assertEquals(10.0, started.getProgress());
        assertEquals("NOT_STARTED", started.getRecoveryStatus());
        verify(workflowClient).newWorkflowStub(eq(DroneMissionWorkflow.class), any(WorkflowOptions.class));
        verify(missionRepository).save(mission);
    }

    @Test
    void testStartMission_NotPending_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.startMission("m-1"));
    }

    @Test
    void testUpdateMissionStatus_ReturningToBase() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission updated = missionService.updateMissionStatus("m-1", MissionStatus.RETURNING_TO_BASE);

        assertEquals(MissionStatus.RETURNING_TO_BASE, updated.getStatus());
        assertEquals("RETURNING_TO_BASE", updated.getRecoveryStatus());
        assertEquals("Returning to base", updated.getCurrentStep());
        assertEquals(90.0, updated.getProgress());
    }

    @Test
    void testUpdateMissionStatus_Completed() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.RETURNING_TO_BASE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission updated = missionService.updateMissionStatus("m-1", MissionStatus.COMPLETED);

        assertEquals(MissionStatus.COMPLETED, updated.getStatus());
        assertEquals("COMPLETED", updated.getRecoveryStatus());
        assertEquals("Completed", updated.getCurrentStep());
        assertEquals(100.0, updated.getProgress());
    }

    @Test
    void testUpdateMissionProgress_Success() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission updated = missionService.updateMissionProgress("m-1", 65.0, "Dropping package", "IN_PROGRESS");

        assertEquals(65.0, updated.getProgress());
        assertEquals("Dropping package", updated.getCurrentStep());
        assertEquals("IN_PROGRESS", updated.getRecoveryStatus());
    }

    @Test
    void testGetMissionProgress_SyncsWithTemporalWorkflow() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenReturn(droneMissionWorkflow);

        com.aerosaga.backend.workflow.MissionProgress workflowProgress =
                new com.aerosaga.backend.workflow.MissionProgress(
                        "m-1", "DR-01", MissionStatus.RETURNING_TO_BASE, "Returning to base", 90.0, "RETURNING_TO_BASE");
        when(droneMissionWorkflow.getMissionProgress()).thenReturn(workflowProgress);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission result = missionService.getMissionProgress("m-1");

        assertNotNull(result);
        assertEquals(MissionStatus.RETURNING_TO_BASE, result.getStatus());
        assertEquals(90.0, result.getProgress());
        assertEquals("Returning to base", result.getCurrentStep());
        assertEquals("RETURNING_TO_BASE", result.getRecoveryStatus());
    }

    @Test
    void testGetMissionProgress_FallbackGracefullyWhenTemporalFails() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenThrow(new RuntimeException("Temporal server unreachable"));

        Mission result = missionService.getMissionProgress("m-1");

        assertNotNull(result);
        assertEquals(MissionStatus.ACTIVE, result.getStatus());
    }
}
