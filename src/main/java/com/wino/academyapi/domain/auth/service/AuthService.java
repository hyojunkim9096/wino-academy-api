// src/main/java/com/wino/academyapi/domain/auth/service/AuthService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.auth.dto.AuthRequest;
import com.wino.academyapi.domain.auth.entity.AdminUserSession;
import com.wino.academyapi.domain.authlog.service.AuthLogService;
import com.wino.academyapi.global.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 로그인 처리 서비스
 * - 실패/잠금/세션 시간 등은 AppSettingService(DB) → 없으면 기본값 순으로 적용
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthLogService authLogService;
    private final SessionService sessionService;
    private final LoginStateUpdater loginState;
    private final AppSettingService settingService;

    /**
     * 로그인 처리
     * 1. 사용자 조회 & 상태 체크 (LOCKED 등)
     * 2. 비밀번호 검증 -> 실패 시 카운트 증가(별도 트랜잭션)
     * 3. 성공 시 -> 카운트 초기화, 세션 발급, JWT 발급
     */
    public String login(AuthRequest.Login reqDto, HttpServletRequest req) {
        String reqUserId = reqDto.getUserId();
        String rawPassword = reqDto.getPassword();

        // 1. 사용자 조회
        AdminUser snapshot = userRepository.findByUserId(reqUserId)
                .orElse(null);

        if (snapshot == null) {
            // 보안상 아이디 없음도 '아이디/비번 불일치'로 처리하지만 로그는 남김
            authLogService.loginFailure(null, reqUserId, req, "USER_NOT_FOUND");
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // 2. 계정 상태 체크 (LOCKED, INACTIVE)
        if (!snapshot.canLogin()) {
            authLogService.loginFailure(snapshot.getId(), reqUserId, req, "ACCOUNT_" + snapshot.getStatus());
            String msg = "INACTIVE".equalsIgnoreCase(snapshot.getStatus())
                    ? "비활성화된 계정입니다. 관리자에게 문의하세요."
                    : "계정이 잠금 상태입니다. 잠시 후 다시 시도하거나 비밀번호 재설정을 진행하세요.";

            // Locked 상태인데 시간이 지났으면 풀어줄 수도 있지만, 여기선 명시적 해제 정책을 따름
            if (snapshot.isLockedNow()) {
                throw new IllegalStateException(msg + " (해제 예정: " +
                        snapshot.getAccountLockedUntil().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + ")");
            } else if ("LOCKED".equalsIgnoreCase(snapshot.getStatus())) {
                // 락 시간이 지났으면 로그인 시도 허용 (아래 비번 검증으로 넘어감)
            } else {
                throw new IllegalStateException(msg);
            }
        }

        // 3. 비밀번호 검증
        boolean ok = passwordEncoder.matches(rawPassword, snapshot.getPassword());
        if (!ok) {
            // 실패 카운트 증가 및 잠금 처리 (별도 트랜잭션)
            loginState.incrementFailAndMaybeLock(reqUserId, maxFailedAttempts(), lockMinutes());
            authLogService.loginFailure(snapshot.getId(), reqUserId, req, "BAD_CREDENTIALS");
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // 4. 성공 처리
        loginState.clearFailAndUnlockOnSuccess(reqUserId);
        authLogService.loginSuccess(snapshot.getId(), reqUserId, req, null);

        // 단일 로그인 정책: 기존 세션 모두 해지
        sessionService.revokeActiveByUser(snapshot.getUserId());

        // 세션 발급 (DB)
        AdminUserSession s = sessionService.issueNew(snapshot.getUserId(), sessionMinutes(), req);
        final String sid = s.getSessionId();

        // JWT 발급 (sid 포함)
        String roleClaim = snapshot.getRole();
        if (roleClaim == null || roleClaim.isBlank()) roleClaim = "ROLE_STAFF";

        return jwtProvider.createToken(
                snapshot.getUserId(),
                roleClaim,
                sid
        );
    }

    /* === 설정값 조회 헬퍼 === */
    private int maxFailedAttempts() {
        return settingService.getInt("auth.login.max-failed-attempts", 5);
    }
    private int lockMinutes() {
        return settingService.getInt("auth.login.lock-minutes", 30);
    }
    private int sessionMinutes() {
        return settingService.getInt("auth.session.minutes", 60);
    }
}