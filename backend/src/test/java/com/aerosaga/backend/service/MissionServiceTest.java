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
        input.setDestination("Delivery-B");

        assertThrows(IllegalArgumentException.class, () -> missionService.createMission(input));
    }

    @Test
    void testCreateMission_MissingDestination_ThrowsException() {
        Mission input = new Mission();
        input.setDroneId("DR-01");
        input.setDestination(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> missionService.createMission(input));
        assertEquals("Destination is required", ex.getMessage());
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
    void testStartMission_BlankMissionId_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> missionService.startMission("   "));
        assertEquals("Mission ID is required", ex.getMessage());
    }

    @Test
    void testStartMission_MissingDroneId_ThrowsException() {
        Mission mission = new Mission("m-1", null, "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> missionService.startMission("m-1"));
        assertEquals("Drone ID is required to start mission", ex.getMessage());
    }

    @Test
    void testStartMission_MissingDestination_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", null, MissionStatus.PENDING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> missionService.startMission("m-1"));
        assertEquals("Destination is required to start mission", ex.getMessage());
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

    @Test
    void testAbortMission_Success_ActiveMission() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenReturn(droneMissionWorkflow);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission result = missionService.abortMission("m-1");

        assertNotNull(result);
        // Status is ABORT_REQUESTED immediately — ABORTED comes later via getMissionProgress sync
        assertEquals(MissionStatus.ABORT_REQUESTED, result.getStatus());
        assertEquals("ABORT_REQUESTED", result.getRecoveryStatus());
        assertEquals("Emergency abort requested - stopping mission", result.getCurrentStep());
        verify(droneMissionWorkflow).requestEmergencyAbort();
        verify(missionRepository).save(mission);
    }

    @Test
    void testAbortMission_WithReason_Success() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenReturn(droneMissionWorkflow);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission result = missionService.abortMission("m-1", "Adverse weather conditions");

        // Immediately ABORT_REQUESTED with the provided reason in the step description
        assertEquals(MissionStatus.ABORT_REQUESTED, result.getStatus());
        assertEquals("ABORT_REQUESTED", result.getRecoveryStatus());
        assertEquals("Abort requested: Adverse weather conditions", result.getCurrentStep());
        verify(droneMissionWorkflow).requestEmergencyAbort();
    }

    @Test
    void testAbortMission_WorkflowThrowsException_ThrowsIllegalStateException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ACTIVE, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenThrow(new RuntimeException("Temporal server unreachable"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.abortMission("m-1"));
        assertTrue(ex.getMessage().contains("Failed to abort mission workflow"));
        verify(missionRepository, never()).save(any(Mission.class));
    }

    @Test
    void testAbortMission_NotFound_ThrowsException() {
        when(missionRepository.findById("m-unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> missionService.abortMission("m-unknown"));
    }

    @Test
    void testAbortMission_Pending_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.abortMission("m-1"));
    }

    @Test
    void testAbortMission_Completed_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.COMPLETED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.abortMission("m-1"));
    }

    @Test
    void testAbortMission_AlreadyAborted_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORTED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.abortMission("m-1"));
    }

    @Test
    void testAbortMission_AlreadyAborting_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORTING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.abortMission("m-1"));
    }

    @Test
    void testAbortMission_AbortRequestedMission_ThrowsException() {
        // Abort signal already sent — mission is waiting for the drone to safely return
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORT_REQUESTED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.abortMission("m-1"));
        assertEquals("Mission abort is already in progress", ex.getMessage());
    }

    @Test
    void testAbortMission_SetsAbortRequestedNotAborted() {
        // Verifies the two-phase lifecycle: service sets ABORT_REQUESTED right away,
        // ABORTED is only set once the workflow finishes and getMissionProgress syncs it.
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.IN_PROGRESS, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenReturn(droneMissionWorkflow);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission result = missionService.abortMission("m-1");

        assertEquals(MissionStatus.ABORT_REQUESTED, result.getStatus(),
                "Status must be ABORT_REQUESTED immediately after the abort signal, not ABORTED");
        assertNotEquals(MissionStatus.ABORTED, result.getStatus(),
                "Status must NOT jump straight to ABORTED before the workflow finishes");
        verify(droneMissionWorkflow).requestEmergencyAbort();
    }

    @Test
    void testGetMissionProgress_SyncsAbortedStatusFromWorkflow() {
        // Once the workflow finishes its safe-return, getMissionProgress should sync
        // the final ABORTED status back into the DB.
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORT_REQUESTED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenReturn(droneMissionWorkflow);

        com.aerosaga.backend.workflow.MissionProgress workflowProgress =
                new com.aerosaga.backend.workflow.MissionProgress(
                        "m-1", "DR-01", MissionStatus.ABORTED, "Aborted", null, "ABORTED");
        when(droneMissionWorkflow.getMissionProgress()).thenReturn(workflowProgress);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission result = missionService.getMissionProgress("m-1");

        // The sync should have written the final ABORTED state from the workflow
        assertEquals(MissionStatus.ABORTED, result.getStatus());
        assertEquals("Aborted", result.getCurrentStep());
        assertEquals("ABORTED", result.getRecoveryStatus());
        verify(droneMissionWorkflow).getMissionProgress();
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void testGetMissionProgress_SyncsAbortingStatusFromWorkflow() {
        // While safe-return is running the workflow reports ABORTING; that should
        // be synced back to the DB as an intermediate state.
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORT_REQUESTED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(workflowClient.newWorkflowStub(eq(DroneMissionWorkflow.class), eq("mission-m-1")))
                .thenReturn(droneMissionWorkflow);

        com.aerosaga.backend.workflow.MissionProgress workflowProgress =
                new com.aerosaga.backend.workflow.MissionProgress(
                        "m-1", "DR-01", MissionStatus.ABORTING,
                        "Aborting - returning drone to base", null, "ABORTING");
        when(droneMissionWorkflow.getMissionProgress()).thenReturn(workflowProgress);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission result = missionService.getMissionProgress("m-1");

        assertEquals(MissionStatus.ABORTING, result.getStatus());
        assertEquals("Aborting - returning drone to base", result.getCurrentStep());
        assertEquals("ABORTING", result.getRecoveryStatus());
    }

    @Test
    void testAbortMission_Failed_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.FAILED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.abortMission("m-1"));
    }

    @Test
    void testUpdateMissionStatus_CompletedToActive_ThrowsIllegalStateException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.COMPLETED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.updateMissionStatus("m-1", MissionStatus.ACTIVE));
        assertTrue(ex.getMessage().contains("Cannot change status of a completed mission"));
    }

    @Test
    void testUpdateMissionStatus_AbortedToActive_ThrowsIllegalStateException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORTED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.updateMissionStatus("m-1", MissionStatus.ACTIVE));
        assertTrue(ex.getMessage().contains("Cannot change status of an aborted mission"));
    }

    @Test
    void testUpdateMissionStatus_PendingToCompleted_ThrowsIllegalStateException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.PENDING, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.updateMissionStatus("m-1", MissionStatus.COMPLETED));
        assertTrue(ex.getMessage().contains("Cannot transition PENDING mission directly to COMPLETED"));
    }

    @Test
    void testUpdateMissionProgress_AbortedMission_ThrowsIllegalStateException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.ABORTED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.updateMissionProgress("m-1", 50.0, "Flying", "IN_PROGRESS"));
        assertTrue(ex.getMessage().contains("Cannot update progress of an aborted mission"));
    }

    @Test
    void testUpdateMissionProgress_FailedMission_ThrowsIllegalStateException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.FAILED, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> missionService.updateMissionProgress("m-1", 50.0, "Flying", "IN_PROGRESS"));
        assertTrue(ex.getMessage().contains("Cannot update progress of a failed mission"));
    }
}
