// src/main/java/com/wino/academyapi/domain/school/entity/School.java
package com.wino.academyapi.domain.school.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NaturalId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 학교 엔티티 (핵심 필드 위주)
 *
 * 설계/운영 포인트
 * - externalCode: NOT NULL + UNIQUE + @NaturalId(mutable=false) → 동기화 업서트 키
 *   · 예: 학교알리미의 고유코드/공시코드 등 “외부 원천 코드”를 저장
 * - 인덱스: 단일 + 복합(@Table.indexes)로 검색 패턴 최적화
 *   · idx_school_stage_active(stage, active)
 *   · idx_school_adm_active_name(adm_code, active, name) — 지역·활성·이름 정렬/검색
 * - 필드 길이: DDL과 정확히 일치(name 200, address/detail/homepage 300, phone 50)
 * - 위경도: DECIMAL(10,7) 정밀도(엔티티/DDL 일치)
 * - createdAt/updatedAt: @PrePersist/@PreUpdate에서 Instant로 채움(UTC 가정)
 *
 * 주의
 * - stage는 EnumType.STRING으로 'E/M/H' 1글자 저장을 가정 → enum 상수명 1글자 유지 권장.
 * - detailAddress에는 "지번(옛 주소)" 저장(지오코딩 결과 처리 레이어 규칙).
 */
@Entity
@Table(
        name = "school",
        indexes = {
                // 단일 인덱스(가벼운 필터/정렬)
                @Index(name = "idx_school_name",        columnList = "name"),
                @Index(name = "idx_school_stage",       columnList = "stage"),
                @Index(name = "idx_school_active",      columnList = "active"),
                @Index(name = "idx_school_edu_office",  columnList = "edu_office_code"),
                @Index(name = "idx_school_adm",         columnList = "adm_code"),

                // ✅ 복합 인덱스(실사용 쿼리 패턴 최적화)
                // WHERE stage=? AND active=? (페이징/목록)
                @Index(name = "idx_school_stage_active",      columnList = "stage, active"),
                // WHERE adm_code LIKE '41%' AND active=TRUE ORDER BY name ASC
                @Index(name = "idx_school_adm_active_name",   columnList = "adm_code, active, name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_school_external_code", columnNames = "external_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class School {

    /* ======================== 기본 키 ======================== */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ======================== 기본 정보 ======================== */

    /** 학교명 (DDL: VARCHAR(200) NOT NULL) */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 학부(E/M/H) — EnumType.STRING으로 1글자 저장 가정
     * (상수명이 1글자가 아니라면 길이 초과 발생 가능)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private SchoolStage stage;

    /** 활성 여부 — Lombok Builder 사용 시에도 true 기본값 유지 */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** 시·도 교육청 코드(예: B10). 길이 여유(DDL: VARCHAR(50)) */
    @Column(name = "edu_office_code", length = 50)
    private String eduOfficeCode;

    /**
     * 외부 연동 식별자(학교알리미/공시 등) — 동기화/업서트 키
     * - NOT NULL + UNIQUE + @NaturalId
     * - mutable=false: NaturalId 값 변경 금지(논리 일관성)
     */
    @NaturalId(mutable = false)
    @Column(name = "external_code", length = 100, nullable = false)
    private String externalCode;

    /* ======================== 주소/지역/좌표 ======================== */

    /** 우편번호(신주소, 5자리 관행이나 DDL 10자 여유) */
    @Column(name = "postal_code", length = 10)
    private String postalCode;

    /** 도로명 주소(신주소) */
    @Column(length = 300)
    private String address;

    /** 상세주소 — ✅ 지번(옛 주소) 저장(지오코딩 규칙에 따름) */
    @Column(name = "detail_address", length = 300)
    private String detailAddress;

    /** 법정동 코드(2/5/8/10자리, 접두 LIKE 검색에 사용) */
    @Column(name = "adm_code", length = 10)
    private String admCode;

    /**
     * 위도/경도 정밀도: DECIMAL(10,7)
     * - 위도(lat):   -90  ~  90
     * - 경도(lng):  -180  ~ 180
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;   // latitude

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;   // longitude

    /* ======================== 링크/연락 ======================== */

    @Column(name = "homepage_url", length = 300)
    private String homepageUrl;

    @Column(length = 50)
    private String phone;

    /* ======================== 생성/수정 시각 ======================== */

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /* ======================== Lifecycle ======================== */

    @PrePersist
    public void prePersist() {
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

        // stage 누락 방어(실수 대비 — 검증/서비스에서도 보장하는 게 바람직)
        if (this.stage == null) this.stage = SchoolStage.E;
        // active는 @Builder.Default로 true 보장
        // externalCode는 NOT NULL + UNIQUE 제약 — 누락 시 명확히 실패
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
