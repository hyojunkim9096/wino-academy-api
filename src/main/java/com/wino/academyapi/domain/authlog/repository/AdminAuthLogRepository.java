// src/main/java/com/wino/academyapi/domain/authlog/repository/AdminAuthLogRepository.java
package com.wino.academyapi.domain.authlog.repository;

import com.wino.academyapi.domain.authlog.entity.AdminAuthLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuthLogRepository extends JpaRepository<AdminAuthLog, Long> {
}
