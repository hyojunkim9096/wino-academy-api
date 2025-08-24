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
 *
 * ✅ touch()는 새 만료시각(LocalDateTime)을 반환 → 프런트가 즉시 남은시간 갱신 가능
 * ✅ remainingSeconds(sid): 예외 대신 0 반환 → 초기 화면에서 부드러운 상태 갱신
 * ✅ session.minutes 값을 DB(AppSetting) → yml → 기본값(60) 순으로 적용
 * ✅ 설정 오입력 안전장치: 세션 TTL 최소/최대값 클램프
 * ✅ IP 추출 강화: X-Forwarded-For, X-Real-IP, CF-Connecting-IP 지원
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final AdminUserSessionRepository repo;
    private final AppSettingService settingService;

    /* ===================== 설정 키 & 보정 유틸 ===================== */

    /** 세션 TTL 후보 키(앞에서부터 우선) — 운영에서 키 이름 바뀌어도 대응하기 쉽게 */
    private static final String[] SESSION_MINUTES_KEYS = new String[] {
            "session.minutes",          // 기본(현재 사용 중)
            "auth.session.minutes"      // 추후 이름 바꿔도 자동 대응
    };

    /** 잘못된 설정으로 인한 과도한 값 방지: 최소/최대 분 단위 */
    private static final int MIN_TTL_MINUTES = 5;      // 최솟값(5분)
    private static final int MAX_TTL_MINUTES = 24 * 60; // 최댓값(24시간)

    /**
     * 현재 세션 만료 분(슬라이딩 기준)을 설정에서 읽고 안전 범위로 보정
     * 우선순위: DB(key@profile) → DB(key) → yml → 기본값(60)
     */
    private int currentSessionMinutes() {
        Integer minutes = null;
        for (String key : SESSION_MINUTES_KEYS) {
            minutes = settingService.getInt(key, null);
            if (minutes != null) break;
        }
        if (minutes == null) minutes = 60; // 최종 기본값
        // 안전 범위 보정(클램프)
        if (minutes < MIN_TTL_MINUTES) minutes = MIN_TTL_MINUTES;
        if (minutes > MAX_TTL_MINUTES) minutes = MAX_TTL_MINUTES;
        return minutes;
    }

    /* ===================== 발급/해지/검증 ===================== */

    /**
     * 새 세션 발급
     *
     * @param userId 사용자 식별자
     * @param sessionMinutesOverride 0보다 크면 이 값 우선, 아니면 설정값(currentSessionMinutes)
     * @param req  원격 IP/UA 기록을 위한 요청 객체
     * @return 저장된 AdminUserSession
     */
    @Transactional
    public AdminUserSession issueNew(String userId, int sessionMinutesOverride, HttpServletRequest req) {
        // 동일 사용자 활성 세션 해지(만료된 것은 건너뜀)
        revokeActiveByUser(userId);

        LocalDateTime now = LocalDateTime.now();
        int minutes = (sessionMinutesOverride > 0) ? sessionMinutesOverride : currentSessionMinutes();

        AdminUserSession s = AdminUserSession.builder()
                .userId(userId)
                .sessionId(UUID.randomUUID().toString().replace("-", "")) // 32자 sid
                .issuedAt(now)
                .lastActivityAt(now)
                .expiresAt(now.plusMinutes(minutes))
                .revokedYn("N")
                .ip(extractIp(req))
                .userAgent(req.getHeader("User-Agent"))
                .build();

        return repo.save(s);
    }

    /**
     * 편의: 문자열 sid만 필요할 때
     */
    @Transactional
    public String issue(String userId, HttpServletRequest req, int sessionMinutesOverride) {
        return issueNew(userId, sessionMinutesOverride, req).getSessionId();
    }

    /**
     * 동일 사용자 활성 세션 해지
     * - 이미 만료된 세션은 건너뜀
     * - 해지 플래그만 'Y'로 바꿔 멱등 처리 (JPA 더티체킹으로 커밋 시 반영)
     *
     * 필요 시 만료된 세션까지 함께 정리하려면
     *  - isExpired(s) == true 인 경우에도 상태를 바꾸거나 삭제하도록 확장 가능
     */
    @Transactional
    public void revokeActiveByUser(String userId) {
        List<AdminUserSession> actives = repo.findByUserIdAndRevokedYn(userId, "N");
        for (AdminUserSession s : actives) {
            if (isExpired(s)) continue;   // 이미 만료 → 건너뜀
            s.setRevokedYn("Y");          // 활성만 해지
        }
    }

    /**
     * 특정 sid 해지 — 멱등
     */
    @Transactional
    public void revoke(String sid) {
        repo.findBySessionId(sid).ifPresent(s -> s.setRevokedYn("Y"));
    }

    /**
     * 유효성 강제 확인(예외 발생)
     * - 세션이 없거나(REVOKED/EXPIRED)면 IllegalStateException
     */
    @Transactional(readOnly = true)
    public AdminUserSession requireActive(String sid) {
        AdminUserSession s = repo.findBySessionId(sid)
                .orElseThrow(() -> new IllegalStateException("세션 정보가 유효하지 않습니다."));
        if (!isActive(s)) throw new IllegalStateException("세션이 만료되었거나 종료되었습니다.");
        return s;
    }

    /**
     * 활동 갱신 (슬라이딩 만료)
     * - lastActivityAt = now
     * - expiresAt = now + currentSessionMinutes()
     *
     * @return 새 만료 시각
     */
    @Transactional
    public LocalDateTime touch(String sid) {
        AdminUserSession s = requireActive(sid);
        LocalDateTime now = LocalDateTime.now();
        s.setLastActivityAt(now);
        LocalDateTime newExp = now.plusMinutes(currentSessionMinutes());
        s.setExpiresAt(newExp);
        return newExp;
    }

    /**
     * 남은 시간(초)
     * - 세션이 없거나 비활성(만료/해지)이면 0 반환(예외 없음)
     */
    @Transactional(readOnly = true)
    public long remainingSeconds(String sid) {
        Optional<AdminUserSession> opt = repo.findBySessionId(sid);
        if (opt.isEmpty()) return 0L;
        AdminUserSession s = opt.get();
        if (!isActive(s)) return 0L;
        long sec = Duration.between(LocalDateTime.now(), s.getExpiresAt()).toSeconds();
        return Math.max(0, sec);
    }

    /**
     * 현재 상태 판별(컨트롤러/필터에서 사유별 응답 매핑용)
     */
    @Transactional(readOnly = true)
    public SessionState requireActiveState(String sid) {
        Optional<AdminUserSession> opt = repo.findBySessionId(sid);
        if (opt.isEmpty()) return SessionState.NOT_FOUND;
        AdminUserSession s = opt.get();
        if (isRevoked(s)) return SessionState.REVOKED;
        if (isExpired(s)) return SessionState.EXPIRED;
        return SessionState.ACTIVE;
    }

    public enum SessionState { ACTIVE, REVOKED, EXPIRED, NOT_FOUND }

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

    /**
     * 프록시/로드밸런서 환경 고려한 IP 추출
     * 우선순위:
     *   1) X-Forwarded-For (맨 앞 IP)
     *   2) X-Real-IP
     *   3) CF-Connecting-IP (Cloudflare)
     *   4) request.getRemoteAddr()
     */
    private String extractIp(HttpServletRequest req) {
        String xff = headerFirstIp(req.getHeader("X-Forwarded-For"));
        if (hasText(xff)) return xff;

        String xr = req.getHeader("X-Real-IP");
        if (hasText(xr)) return xr.trim();

        String cf = req.getHeader("CF-Connecting-IP");
        if (hasText(cf)) return cf.trim();

        String ra = req.getRemoteAddr();
        return (ra == null ? "" : ra.trim());
    }

    /** "1.1.1.1, 2.2.2.2" → "1.1.1.1" */
    private String headerFirstIp(String header) {
        if (!hasText(header)) return null;
        String[] parts = header.split(",");
        return parts.length > 0 ? parts[0].trim() : header.trim();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
