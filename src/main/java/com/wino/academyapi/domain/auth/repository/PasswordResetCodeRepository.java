// src/main/java/com/wino/academyapi/domain/auth/repository/PasswordResetCodeRepository.java
package com.wino.academyapi.domain.auth.repository;

import com.wino.academyapi.domain.auth.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** 특정 사용자 모든 코드 삭제(가장 최근 1건만 유지 정책 등 구현 시 사용) */
    void deleteByUserId(String userId);

    /** 가장 최근 발급 코드 1건(issuedAt 기준) */
    Optional<PasswordResetCode> findTopByUserIdOrderByIssuedAtDesc(String userId);
}
