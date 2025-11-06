// src/main/java/com/wino/academyapi/domain/auth/service/LoginStateUpdater.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패/성공 시 "아주 짧게" 행 잠금을 잡고 상태를 갱신하는 전용 서비스.
 * - REQUIRES_NEW 트랜잭션으로 바깥 흐름과 분리하여 락 보유 시간을 최소화.
 * - PESSIMISTIC_WRITE 쿼리는 Repository의 findByUserIdForUpdate 사용.
 */
@Service
@RequiredArgsConstructor
public class LoginStateUpdater {

    private final AdminUserRepository userRepository;

    /**
     * 실패 카운트 증가(+임계치 시 LOCKED 전환).
     * - 비밀번호 불일치 시에만 호출.
     */
    @Transactional(noRollbackFor = { IllegalArgumentException.class }, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void incrementFailAndMaybeLock(String userId, int maxFailedAttempts, int lockMinutes) {
        AdminUser u = userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));
        u.markLoginFail(maxFailedAttempts, lockMinutes); // 더티체킹 → COMMIT
    }

    /**
     * 성공 로그인 후 실패 카운트 초기화/잠금 해제/마지막 로그인 기록.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void clearFailAndUnlockOnSuccess(String userId) {
        AdminUser u = userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));
        u.markLoginSuccess(); // 더티체킹 → COMMIT
    }
}
