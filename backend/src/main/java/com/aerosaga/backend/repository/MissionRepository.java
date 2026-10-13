
package com.aerosaga.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.aerosaga.backend.model.Mission;

public interface MissionRepository extends JpaRepository<Mission, String> {

}
