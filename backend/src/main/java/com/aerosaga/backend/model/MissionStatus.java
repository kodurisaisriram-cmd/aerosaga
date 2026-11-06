package com.aerosaga.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MissionStatus {

    PENDING,
    ACTIVE,
    IN_PROGRESS,
    RETURNING_TO_BASE,
    COMPLETED,
    FAILED,
    ABORT_REQUESTED,
    ABORTING,
    ABORTED;

    @JsonCreator
    public static MissionStatus fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim()
                .toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");

        for (MissionStatus status : MissionStatus.values()) {
            if (status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown mission status: " + value);
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}