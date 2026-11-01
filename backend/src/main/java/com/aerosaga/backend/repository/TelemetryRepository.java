
package com.aerosaga.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aerosaga.backend.model.Telemetry;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {

    List<Telemetry> findByDroneIdOrderByTimestampDesc(String droneId);

}
