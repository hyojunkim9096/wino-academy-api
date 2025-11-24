// src/main/java/com/wino/academyapi/domain/auth/service/SessionService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.auth.entity.AdminUserSession;
import com.wino.academyapi.domain.auth.repository.AdminUserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 세션 발급/검증/활동갱신/해지 관리
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final AdminUserSessionRepository repo;
    private final AppSettingService settingService;

    public enum SessionState { ACTIVE, REVOKED, EXPIRED, NOT_FOUND }

    /** 신규 세션 발급 */
    @Transactional
    public AdminUserSession issueNew(String userId, int minutes, HttpServletRequest req) {
        // 세션 시간 보정 (최소 5분 ~ 최대 24시간)
        int validMin = Math.max(5, Math.min(minutes, 1440));

        AdminUserSession s = AdminUserSession.builder()
                .userId(userId)
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(validMin))
                .revokedYn("N")
                .ip(extractIp(req))
                .userAgent(limit(req.getHeader("User-Agent"), 250))
                .build();
        return repo.save(s);
    }

    /** 활동 갱신 (Touch) */
    @Transactional
    public LocalDateTime touch(String sid) {
        AdminUserSession s = repo.findBySessionId(sid)
                .orElseThrow(() -> new IllegalStateException("Session not found"));

        if (!isActive(s)) throw new IllegalStateException("Session inactive");

        int minutes = settingService.getInt("auth.session.minutes", 60);
        int validMin = Math.max(5, Math.min(minutes, 1440));

        s.setLastActivityAt(LocalDateTime.now());
        // 슬라이딩 만료: 현재 활동 시각 + 설정 시간으로 연장
        LocalDateTime newExp = LocalDateTime.now().plusMinutes(validMin);
        s.setExpiresAt(newExp);

        return newExp;
    }

    /** ✅ [추가] 남은 시간 조회 (로그인 상태 확인용) */
    @Transactional(readOnly = true)
    public long remainingSeconds(String sid) {
        return repo.findBySessionId(sid)
                .filter(this::isActive)
                .map(s -> Duration.between(LocalDateTime.now(), s.getExpiresAt()).getSeconds())
                .map(sec -> Math.max(0, sec))
                .orElse(0L);
    }

    /** 특정 사용자의 모든 활성 세션 해지 (단일 로그인) */
    @Transactional
    public void revokeActiveByUser(String userId) {
        List<AdminUserSession> list = repo.findByUserIdAndRevokedYn(userId, "N");
        LocalDateTime now = LocalDateTime.now();
        for (AdminUserSession s : list) {
            if (s.getExpiresAt().isAfter(now)) {
                s.setRevokedYn("Y");
            }
        }
    }

    /** 세션 ID로 해지 */
    @Transactional
    public void revoke(String sid) {
        repo.findBySessionId(sid).ifPresent(s -> s.setRevokedYn("Y"));
    }

    /** 세션 상태 조회 (필터 등에서 사용) */
    @Transactional(readOnly = true)
    public SessionState requireActiveState(String sid) {
        Optional<AdminUserSession> op = repo.findBySessionId(sid);
        if (op.isEmpty()) return SessionState.NOT_FOUND;
        AdminUserSession s = op.get();
        if (isRevoked(s)) return SessionState.REVOKED;
        if (isExpired(s)) return SessionState.EXPIRED;
        return SessionState.ACTIVE;
    }

    /* ===================== 내부 판별/유틸 ===================== */

    private boolean isActive(AdminUserSession s) {
        return !isRevoked(s) && !isExpired(s);
    }

    private boolean isRevoked(AdminUserSession s) {
        return "Y".equalsIgnoreCase(s.getRevokedYn());
    }

    private boolean isExpired(AdminUserSession s) {
        LocalDateTime exp = s.getExpiresAt();
        return exp == null || exp.isBefore(LocalDateTime.now());
    }

    private String extractIp(HttpServletRequest req) {
        if (req == null) return "";
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP"};
        for (String h : headers) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) {
                return v.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "";
    }

    private String limit(String s, int len) {
        if (s == null) return null;
        return s.length() > len ? s.substring(0, len) : s;
    }
}