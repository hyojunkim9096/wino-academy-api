// api/src/main/java/com/wino/academyapi/domain/auth/entity/AdminUserSession.java
package com.wino.academyapi.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 관리자 세션 엔티티
 * - JWT sid 클레임과 1:1 매핑
 * - 단일 로그인 강제를 위해 동일 userId의 기존 세션을 모두 revoke 처리
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "admin_user_session", indexes = {
        @Index(name="idx_sess_user", columnList="user_id"),
        @Index(name="idx_sess_active", columnList="revoked_yn, expires_at")
})
public class AdminUserSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AdminUser.userId (로그인ID) */
    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    /** JWT sid 클레임에 담기는 세션ID (UUID-하이픈 제거 등) */
    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    /** 발급 시각 */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /** 마지막 활동 시각 (프론트에서 /api/auth/ping 호출로 갱신) */
    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    /** 절대 만료 시각 (예: 발급 + 60분) */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 해지 여부: Y=해지됨, N=활성 */
    @Column(name = "revoked_yn", nullable = false, length = 1)
    private String revokedYn;

    /** 감사 용도 */
    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** 만료 여부 */
    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }

    /** 해지 여부 */
    public boolean isRevoked() { return "Y".equalsIgnoreCase(revokedYn); }

    /** 활성 여부 */
    public boolean isActive() {
        return !"Y".equals(revokedYn)
                && expiresAt != null
                && expiresAt.isAfter(LocalDateTime.now());
    }
}
