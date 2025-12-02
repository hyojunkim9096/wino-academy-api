// src/main/java/com/wino/academyapi/domain/auth/service/LoginStateUpdater.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.member.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * 로그인 실패/성공 시 상태를 갱신하는 전용 서비스.
 * - REQUIRES_NEW 트랜잭션으로 메인 로직의 롤백 여부와 관계없이 상태 변경을 저장함.
 * - 동시성 제어를 위해 PESSIMISTIC_WRITE 락을 사용.
 */
@Service
@RequiredArgsConstructor
public class LoginStateUpdater {

    private final AdminUserRepository userRepository;

    /**
     * 실패 카운트 증가 (+ 임계치 도달 시 LOCKED 전환)
     */
    @Transactional(noRollbackFor = { IllegalArgumentException.class }, propagation = Propagation.REQUIRES_NEW)
    public void incrementFailAndMaybeLock(String userId, int maxFailedAttempts, int lockMinutes) {
        userRepository.findByUserIdForUpdate(userId).ifPresent(u -> {
            u.markLoginFail(maxFailedAttempts, lockMinutes);
            // Transactional 종료 시 dirty checking으로 update 쿼리 발생
        });
    }

    /**
     * 로그인 성공 시 실패 카운트 초기화 및 잠금 해제
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailAndUnlockOnSuccess(String userId) {
        userRepository.findByUserIdForUpdate(userId).ifPresent(u -> {
            u.markLoginSuccess();
        });
    }
}