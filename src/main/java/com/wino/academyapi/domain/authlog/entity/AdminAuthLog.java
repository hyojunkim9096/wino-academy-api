// src/main/java/com/wino/academyapi/domain/authlog/entity/AdminAuthLog.java
package com.wino.academyapi.domain.authlog.entity;

import com.wino.academyapi.domain.authlog.model.AdminAuthEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 관리자 인증 이벤트 로그 엔티티
 * - DDL: admin_auth_log 테이블에 매핑
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_auth_log", indexes = {
        @Index(name = "idx_auth_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_auth_admin_created", columnList = "admin_id, created_at"),
        @Index(name = "idx_auth_event_created", columnList = "event_type, created_at"),
        @Index(name = "idx_auth_success_created", columnList = "success, created_at"),
        @Index(name = "idx_auth_ip_created", columnList = "ip_address, created_at"),
        @Index(name = "idx_auth_created", columnList = "created_at")
})
public class AdminAuthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK: admin_user_info.id (NULL 허용: 계정 삭제돼도 로그 보존) */
    @Column(name = "admin_id")
    private Long adminId;

    /** 사용자 ID 스냅샷 (항상 저장) */
    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    /** 이벤트 타입 */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AdminAuthEventType eventType;

    /** 성공 여부 (로그아웃은 1) */
    @Column(name = "success", nullable = false)
    private boolean success;

    /** 실패 사유/부가 정보 */
    @Column(name = "reason", length = 500)
    private String reason;

    /** 요청 IP (IPv4/IPv6 지원) */
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /** User-Agent */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** 세션/토큰 식별자(있으면 저장) */
    @Column(name = "session_id", length = 128)
    private String sessionId;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
