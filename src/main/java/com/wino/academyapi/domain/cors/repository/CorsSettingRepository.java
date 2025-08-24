// src/main/java/com/wino/academyapi/domain/cors/repository/CorsSettingRepository.java
package com.wino.academyapi.domain.cors.repository;

import com.wino.academyapi.domain.cors.entity.CorsSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CorsSettingRepository extends JpaRepository<CorsSetting, Long> {
    Optional<CorsSetting> findTopByOrderByIdAsc();
}
