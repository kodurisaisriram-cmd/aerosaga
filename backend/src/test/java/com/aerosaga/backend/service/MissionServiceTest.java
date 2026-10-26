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

        assertEquals(MissionStatus.IN_PROGRESS, started.getStatus());
        verify(workflowClient).newWorkflowStub(eq(DroneMissionWorkflow.class), any(WorkflowOptions.class));
        verify(missionRepository).save(mission);
    }

    @Test
    void testStartMission_NotPending_ThrowsException() {
        Mission mission = new Mission("m-1", "DR-01", "A", "B", MissionStatus.IN_PROGRESS, LocalDateTime.now());
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        assertThrows(IllegalStateException.class, () -> missionService.startMission("m-1"));
    }
}
