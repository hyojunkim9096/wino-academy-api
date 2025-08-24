// src/main/java/com/wino/academyapi/domain/dbconn/entity/DbConnectionProfileDeleted.java
package com.wino.academyapi.domain.dbconn.entity;

import com.fasterxml.jackson.annotation.JsonIgnore; // 응답 노출 차단(암호문)
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 삭제된 DB 연결 프로필 이력 엔티티
 *
 * 목적
 * - 운영 안전을 위해 실제 삭제 전에 당시 상태를 별도 테이블에 보존합니다.
 * - 화면의 "삭제 이력" 목록/감사 추적에 사용됩니다.
 *
 * 직렬화/보안
 * - encPassword(암호문)는 절대 API 응답으로 노출되지 않도록 @JsonIgnore 처리합니다.
 *
 * 인덱스
 * - env_code + deleted_at 복합 인덱스로 환경별, 시각 역순 조회 성능을 확보합니다.
 *
 * 비고
 * - 컬럼 길이는 현재 메인 엔티티와 유사 수준으로 유지하되, 실제 DB 스키마에 맞춰 조정하세요.
 */
@Entity
@Table(
        name = "db_connection_profile_deleted",
        indexes = {
                @Index(name = "ix_dbconn_profile_deleted_env", columnList = "env_code, deleted_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "encPassword") // 로그에 암호문이 섞이지 않도록 제외
public class DbConnectionProfileDeleted {

    // ───────────────────────── 기본 키 ─────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ───────────────────────── 원본 스냅샷 필드 ─────────────────────────

    /** 원본 프로필 PK */
    @Column(name = "profile_id")
    private Long profileId;

    /** 프로필 표시명 */
    @Column(name = "profile_name", length = 100)
    private String profileName;

    /** 환경 코드: dev | prod */
    @Column(name = "env_code", length = 50)
    private String envCode;

    /** JDBC URL */
    @Column(name = "jdbc_url", length = 500)
    private String jdbcUrl;

    /** DB 계정명 */
    @Column(name = "username", length = 190)
    private String username;

    /** 암호문 — 절대 응답으로 노출하지 않음 */
    @JsonIgnore
    @Column(name = "enc_password", columnDefinition = "TEXT")
    private String encPassword;

    /** 드라이버 클래스 */
    @Column(name = "driver_class", length = 190)
    private String driverClass;

    /** HikariCP 옵션 스냅샷 */
    @Column(name = "maximum_pool_size")
    private Integer maximumPoolSize;

    @Column(name = "minimum_idle")
    private Integer minimumIdle;

    @Column(name = "idle_timeout_ms")
    private Long idleTimeoutMs;

    @Column(name = "max_lifetime_ms")
    private Long maxLifetimeMs;

    /** 삭제 당시 활성여부 스냅샷 */
    @Column(name = "is_active")
    private Boolean isActive;

    /** 비고(스냅샷) */
    @Column(name = "remark", length = 500)
    private String remark;

    // ───────────────────────── 삭제 메타 ─────────────────────────

    /** 삭제 시각(서버 기준) */
    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    /** 삭제자(계정 ID) */
    @Column(name = "deleted_by", length = 120)
    private String deletedBy;

    // ───────────────────────── 라이프사이클 훅 ─────────────────────────

    /** deletedAt 누락 방지 */
    @PrePersist
    private void onPrePersist() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
        if (driverClass == null || driverClass.isBlank()) {
            driverClass = "com.mysql.cj.jdbc.Driver";
        }
    }

    // ───────────────────────── 팩토리(스냅샷 생성) ─────────────────────────

    /**
     * 현재 DbConnectionProfile 엔티티로부터 삭제 이력 스냅샷을 생성한다.
     *
     * @param p         원본 프로필 엔티티
     * @param deletedBy 삭제자 ID (없으면 null 가능)
     */
    public static DbConnectionProfileDeleted snapshotOf(DbConnectionProfile p, String deletedBy) {
        if (p == null) return null;
        return DbConnectionProfileDeleted.builder()
                .profileId(p.getId())
                .profileName(p.getProfileName())
                .envCode(p.getEnvCode())
                .jdbcUrl(p.getJdbcUrl())
                .username(p.getUsername())
                .encPassword(p.getEncPassword())
                .driverClass(p.getDriverClass())
                .maximumPoolSize(p.getMaximumPoolSize())
                .minimumIdle(p.getMinimumIdle())
                .idleTimeoutMs(p.getIdleTimeoutMs())
                .maxLifetimeMs(p.getMaxLifetimeMs())
                .isActive(p.getIsActive())
                .remark(p.getRemark())
                .deletedAt(LocalDateTime.now())
                .deletedBy(deletedBy)
                .build();
    }
}
