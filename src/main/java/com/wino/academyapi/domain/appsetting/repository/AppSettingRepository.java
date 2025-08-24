// src/main/java/com/wino/academyapi/domain/appsetting/repository/AppSettingRepository.java
package com.wino.academyapi.domain.appsetting.repository;

import com.wino.academyapi.domain.appsetting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {
    Optional<AppSetting> findByKey(String key);
}
