
package com.aerosaga.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.aerosaga.backend.model.Mission;

public interface MissionRepository extends JpaRepository<Mission, String> {

    Optional<Mission> findByMissionIdAndDroneId(
            String missionId,
            String droneId
    );
}
