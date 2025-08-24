// src/main/java/com/wino/academyapi/domain/auth/entity/PasswordResetCode.java
package com.wino.academyapi.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정용 인증코드 엔티티
 * - issuedAt: 발급시각(정렬/최신코드 조회에 사용)
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

    /** 로그인 아이디(이메일 가능) */
    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    /** 코드(평문 대신 해시 저장) */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    /** 발급 시각(서비스 로직에서 최신 1건 조회에 사용) */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /** 만료 시각 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 사용 여부 */
    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    /** 행 생성 시각(감사용) */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /** 만료 여부 */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /** 사용 처리 */
    public void markUsed() {
        this.used = true;
    }
}
