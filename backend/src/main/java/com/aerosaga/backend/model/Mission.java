
package com.aerosaga.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @Column(name = "mission_id", nullable = false)
    private String missionId;

    @Column(name = "drone_id", nullable = false)
    private String droneId;

    @Column(name = "source")
    private String source;

    @Column(name = "destination")
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MissionStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "progress")
    private Double progress;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "recovery_status")
    private String recoveryStatus;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // No-argument constructor
    public Mission() {
    }

    // Parameterized constructor (backward-compatible)
    public Mission(String missionId, String droneId, String source,
                   String destination, MissionStatus status,
                   LocalDateTime createdAt) {

        this.missionId = missionId;
        this.droneId = droneId;
        this.source = source;
        this.destination = destination;
        this.status = status;
        this.createdAt = createdAt;
        this.progress = 0.0;
        this.currentStep = "Pending";
        this.recoveryStatus = "NOT_STARTED";
        this.updatedAt = createdAt;
    }

    // Full Parameterized constructor
    public Mission(String missionId, String droneId, String source,
                   String destination, MissionStatus status,
                   Double progress, String currentStep,
                   String recoveryStatus, LocalDateTime createdAt,
                   LocalDateTime updatedAt) {

        this.missionId = missionId;
        this.droneId = droneId;
        this.source = source;
        this.destination = destination;
        this.status = status;
        this.progress = progress;
        this.currentStep = currentStep;
        this.recoveryStatus = recoveryStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getDroneId() {
        return droneId;
    }

    public void setDroneId(String droneId) {
        this.droneId = droneId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getProgress() {
        return progress;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getRecoveryStatus() {
        return recoveryStatus;
    }

    public void setRecoveryStatus(String recoveryStatus) {
        this.recoveryStatus = recoveryStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
