// api/src/main/java/com/wino/academyapi/domain/auth/repository/AdminUserSessionRepository.java
package com.wino.academyapi.domain.auth.repository;

import com.wino.academyapi.domain.auth.entity.AdminUserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserSessionRepository extends JpaRepository<AdminUserSession, Long> {
    Optional<AdminUserSession> findBySessionId(String sessionId);
    List<AdminUserSession> findByUserIdAndRevokedYn(String userId, String revokedYn);
}
