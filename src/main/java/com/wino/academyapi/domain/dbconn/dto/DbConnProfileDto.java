// src/main/java/com/wino/academyapi/domain/dbconn/dto/DbConnProfileDto.java
package com.wino.academyapi.domain.dbconn.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DB 연결 프로필 DTO 모음 (요청/응답용)
 *
 * ✅ 구성
 *  - DbConnProfileDto        : 프로필 등록/수정/상세에 사용하는 기본 DTO
 *  - DbConnProfileDto.Deleted: 삭제 이력 조회용 DTO
 *
 * ✅ 비밀번호 정책
 *  - passwordPlain 은 "입력 시에만" 전달됩니다. 미입력(null/blank) 시 기존 암호를 유지합니다.
 *    (서비스에서 encPassword는 내부적으로 AES 암호화하여 보관)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DbConnProfileDto {

    /** 엔티티 PK (수정/상세 시 사용) */
    private Long id;

    /** 프로필 표시명 */
    private String profileName;

    /** 환경 코드: dev | prod  (조회 파라미터로는 all 을 쓰기도 함) */
    private String envCode;

    /** JDBC URL */
    private String jdbcUrl;

    /** DB 계정명 */
    private String username;

    /** 평문 비밀번호 (입력 시에만 사용; 서비스에서 암호화 저장) */
    private String passwordPlain;

    /** 드라이버 클래스 (기본: com.mysql.cj.jdbc.Driver) */
    private String driverClass;

    /** Hikari: 최대 풀 크기 */
    private Integer maximumPoolSize;

    /** Hikari: 최소 유휴 커넥션 수 */
    private Integer minimumIdle;

    /** Hikari: 유휴 타임아웃(ms) */
    private Long idleTimeoutMs;

    /** Hikari: 커넥션 최대 수명(ms) */
    private Long maxLifetimeMs;

    /** 활성 여부(같은 env 에서는 1개만 TRUE; 보통 /activate API로 전환) */
    private Boolean isActive;

    /** 비고 */
    private String remark;

    // ─────────────────────────────────────────────────────────────────────
    // 삭제 이력 조회 DTO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 삭제된 DB 연결 프로필 이력 DTO
     * - 프런트의 "삭제 이력" 테이블 스키마에 맞춰 구성
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Deleted {
        /** 삭제 이력 테이블 PK */
        private Long id;

        /** 원본 프로필 PK */
        private Long profileId;

        private String profileName;
        private String envCode;          // dev | prod
        private String jdbcUrl;
        private String username;

        private String driverClass;
        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Long idleTimeoutMs;
        private Long maxLifetimeMs;

        private Boolean isActive;        // 삭제 당시 상태(보통 false)
        private String remark;

        /** 삭제 시각 */
        private LocalDateTime deletedAt;

        /** 삭제자(계정 ID) */
        private String deletedBy;
    }
}
