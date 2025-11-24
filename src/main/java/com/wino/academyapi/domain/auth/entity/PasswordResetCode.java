// src/main/java/com/wino/academyapi/domain/auth/entity/PasswordResetCode.java
package com.wino.academyapi.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정용 인증코드 엔티티
 * - issuedAt: 발급시각
 * - expiresAt: 만료시각
 * - used: 사용여부
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "password_reset_code", indexes = {
        @Index(name = "idx_prc_user", columnList = "user_id")
})
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /** 만료 여부 */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /** ✅ [수정] 사용 처리 (상태 + 시각 동시 변경) */
    public void markUsed() {
        this.used = true;
        this.usedAt = LocalDateTime.now();
    }
}