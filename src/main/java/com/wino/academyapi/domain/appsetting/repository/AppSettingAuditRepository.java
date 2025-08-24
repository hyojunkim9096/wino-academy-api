// src/main/java/com/wino/academyapi/domain/appsetting/repository/AppSettingAuditRepository.java
package com.wino.academyapi.domain.appsetting.repository;

import com.wino.academyapi.domain.appsetting.entity.AppSettingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingAuditRepository extends JpaRepository<AppSettingAudit, Long> {
}
