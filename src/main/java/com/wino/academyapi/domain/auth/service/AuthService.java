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
 * 로그인 처리 (status/role 모두 문자열 코드 기반)
 * - 실패/잠금/세션 시간 등은 AppSettingService(DB) → 없으면 yml/디폴트 순으로 폴백
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

    // (기존 @Value 필드들은 DB 기반으로 대체되므로 주석 백업만 유지)
    // @Value("${auth.login.max-failed-attempts:${app.security.max-failed-attempts:5}}") private int maxFailedAttempts;
    // @Value("${auth.login.lock-minutes:${app.security.lock-minutes:30}}") private int lockMinutes;
    // @Value("${session.minutes:60}") private int sessionMinutes;

    /** 로그인 실패 누적 한도 (DB → yml → default 5) */
    private int maxFailedAttempts() {
        return settingService.getFirstIntProfileAware(
                new String[]{"auth.login.max-failed-attempts","app.security.max-failed-attempts","security.max-failed-attempts"},
                5
        );
    }

    /** 계정 잠금 유지 시간(분) (DB → yml → default 30) */
    private int lockMinutes() {
        return settingService.getFirstIntProfileAware(
                new String[]{"auth.login.lock-minutes","app.security.lock-minutes","security.lock-minutes"},
                30
        );
    }

    /** 세션 유지 시간(분) (DB → yml → default 60) */
    private int sessionMinutes() {
        return settingService.getFirstIntProfileAware(
                new String[]{"session.minutes"},
                60
        );
    }

    public String login(AuthRequest.Login request, HttpServletRequest req) {
        final String reqUserId = trimToNull(request == null ? null : request.getUserId());
        final String rawPassword = request == null ? "" : (request.getPassword() == null ? "" : request.getPassword());
        if (reqUserId == null) throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");

        // 사용자 조회
        AdminUser snapshot = userRepository.findByUserId(reqUserId)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

        // 상태 제약 (문자열 비교)
        String status = snapshot.getStatus();
        if ("INACTIVE".equalsIgnoreCase(status)) {
            authLogService.loginFailure(snapshot.getId(), reqUserId, req, "INACTIVE");
            throw new IllegalStateException("사용이 불가한 계정입니다.");
        }
        if ("TEMPORARY".equalsIgnoreCase(status)) {
            authLogService.loginFailure(snapshot.getId(), reqUserId, req, "TEMPORARY");
            throw new IllegalStateException("임시발급된 계정입니다. 관리자에게 문의 바랍니다.");
        }
        if (snapshot.isLockedNow()) {
            authLogService.loginFailure(snapshot.getId(), reqUserId, req, "LOCKED");
            LocalDateTime until = snapshot.getAccountLockedUntil();
            String msg = (until != null)
                    ? "계정이 잠금 상태입니다. 잠금 해제 시각: " + until.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "계정이 잠금 상태입니다. 잠시 후 다시 시도하거나 비밀번호 재설정을 진행하세요.";
            throw new IllegalStateException(msg);
        }

        // 비밀번호 검증
        boolean ok = passwordEncoder.matches(rawPassword, snapshot.getPassword());
        if (!ok) {
            // ⬇️ 현재 설정값으로 실패증가/락 처리
            loginState.incrementFailAndMaybeLock(reqUserId, maxFailedAttempts(), lockMinutes());
            authLogService.loginFailure(snapshot.getId(), reqUserId, req, "BAD_CREDENTIALS");
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // 성공 처리: 실패카운트 초기화/락 해제
        loginState.clearFailAndUnlockOnSuccess(reqUserId);
        authLogService.loginSuccess(snapshot.getId(), reqUserId, req, null);

        // 단일 로그인: 기존 세션 revoke → 새 세션 발급
        sessionService.revokeActiveByUser(snapshot.getUserId());

        // 세션 분 수 적용
        AdminUserSession s = sessionService.issueNew(snapshot.getUserId(), sessionMinutes(), req);
        final String sid = s.getSessionId();

        // JWT 발급: role 문자열을 "그대로" 저장 (정책: ROLE_ 접두사 포함 형태로 DB 관리)
        String roleClaim = snapshot.getRole();
        if (roleClaim == null || roleClaim.isBlank()) roleClaim = "ROLE_STAFF";

        return jwtProvider.createToken(
                snapshot.getUserId(),
                roleClaim,
                sid
        );
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
