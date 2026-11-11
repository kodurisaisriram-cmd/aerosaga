package com.aerosaga.backend.workflow;

public enum MissionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    ABORT_REQUESTED,
    ABORTING,
    ABORTED,
}
