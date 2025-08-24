// src/main/java/com/wino/academyapi/domain/syslog/entity/AdminSystemLog.java
package com.wino.academyapi.domain.syslog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 관리자 기능 변경 로그 엔티티
 *
 * ✅ DDL(현재 적용본)과 1:1 매핑
 *    - id BIGINT AUTO_INCREMENT
 *    - created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
 *    - 문자열 컬럼 길이: actor_user_id(120), method(10), path(500),
 *      screen_code(120), screen_name(200), resource_type(60), resource_id(80),
 *      action(80), ip_address(64), user_agent(512), error_message(1000)
 *    - request_body/extra_json: LONGTEXT
 *    - status_code(INT), elapsed_ms(BIGINT)
 *
 * ⚠ created_at 은 필터(AdminActionLoggingFilter)에서 직접 set 하므로
 *   여기서 @CreationTimestamp 등을 쓰지 않습니다.
 *
 * ⚙ 인덱스
 *   - JPA로 생성: created_at / (actor_user_id, created_at) / (method, created_at)
 *   - SQL DDL로 생성: (path(191), created_at), (screen_code, created_at)  ← MySQL 5.x + utf8mb4 prefix 고려
 */
@Entity
@Table(
        name = "admin_system_log",
        indexes = {
                @Index(name = "idx_admin_syslog_created", columnList = "created_at"),
                @Index(name = "idx_admin_syslog_actor",   columnList = "actor_user_id, created_at"),
                @Index(name = "idx_admin_syslog_method",  columnList = "method, created_at")
                // path/screen_code 인덱스는 MySQL 5.x에서 prefix(191) 필요 → 별도 DDL로 관리
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSystemLog {

    /** PK (AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기록 시각 — 필터에서 직접 set. DB default CURRENT_TIMESTAMP와 중복되지 않게 주도적으로 세팅한다. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 행위자(로그인 ID, 비로그인이면 NULL) */
    @Column(name = "actor_user_id", length = 120)
    private String actorUserId;

    /** HTTP 메서드 (예: GET/POST/PUT/PATCH/DELETE) */
    @Column(name = "method", length = 10, nullable = false)
    private String method;

    /** 요청 경로 (예: /api/admin/users/123) */
    @Column(name = "path", length = 500, nullable = false)
    private String path;

    /** 화면 코드(react_page 또는 path 등) — admin_menu 매칭으로 자동 보강 */
    @Column(name = "screen_code", length = 120)
    private String screenCode;

    /** 화면명(admin_menu.name) — admin_menu 매칭으로 자동 보강 */
    @Column(name = "screen_name", length = 200)
    private String screenName;

    /** 리소스 유형(옵션: APP_SETTING/SECURITY_RULE/DB_CONN 등) */
    @Column(name = "resource_type", length = 60)
    private String resourceType;

    /** 리소스 ID(문자열) */
    @Column(name = "resource_id", length = 80)
    private String resourceId;

    /** 액션 키워드(예: CREATE/UPDATE/DELETE/ACTIVATE 등) */
    @Column(name = "action", length = 80)
    private String action;

    /** 요청자 IP */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** User-Agent */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** 요청 본문(민감정보는 필터에서 마스킹; 캡처 크기도 필터에서 제한) */
    @Lob
    @Column(name = "request_body", columnDefinition = "LONGTEXT")
    private String requestBody;

    /** 추가 메타(JSON) */
    @Lob
    @Column(name = "extra_json", columnDefinition = "LONGTEXT")
    private String extraJson;

    /** 응답 코드 (예: 200/400/500) */
    @Column(name = "status_code")
    private Integer statusCode;

    /** 처리 소요 시간(ms) */
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /** 예외 메시지 요약(있으면) */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
