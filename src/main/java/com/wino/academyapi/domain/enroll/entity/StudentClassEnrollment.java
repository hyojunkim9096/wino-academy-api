// src/main/java/com/wino/academyapi/domain/enroll/entity/StudentClassEnrollment.java
package com.wino.academyapi.domain.enroll.entity;

import com.wino.academyapi.domain.student.entity.Student;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DDL 매핑: student_class_enrollment (기간성 반 배정)
 *
 * ✅ 2025-10 Core DDL 반영 사항
 *  - class_status_code 컬럼 도입: MAIN | CROSS (기존 roleCode → classStatusCode 로 명칭 정리)
 *  - 수강 요일은 CSV가 아닌 비트마스크로 관리: attend_days_mask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64)
 *    · 실제 집계는 student_enroll_timeslot 트리거가 수행
 *    · 본 엔티티는 집계 결과만 들고 있으며, 생성/수정 시 직접 세팅할 필요(의도) 없음
 *  - attend_days_updated_at: 요일 집계 갱신 시각(트리거가 관리)
 *
 * 인덱스/제약
 *  - UNIQUE(student_id, class_id, enrolled_at) — 동일 시작일 중복 방지
 *  - idx_enroll_student(student_id, status)
 *  - idx_enroll_class(class_id, status)
 *  - idx_enroll_daysmask(attend_days_mask)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "student_class_enrollment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enroll_active",
                columnNames = {"student_id", "class_id", "enrolled_at"}
        ),
        indexes = {
                @Index(name = "idx_enroll_student", columnList = "student_id, status"),
                @Index(name = "idx_enroll_class", columnList = "class_id, status"),
                @Index(name = "idx_enroll_daysmask", columnList = "attend_days_mask")
        }
)
public class StudentClassEnrollment {

    /** PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학생 FK (CASCADE on delete) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "student_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_enroll_student")
    )
    private Student student;

    /** 반 FK — class_master(id) (엔티티 없이 숫자 FK로만 보관) */
    @Column(name = "class_id", nullable = false)
    private Long classId;

    /** 배정 시작일 */
    @Column(name = "enrolled_at", nullable = false)
    private LocalDate enrolledAt;

    /** 배정 종료일(NULL=재학중) */
    @Column(name = "left_at")
    private LocalDate leftAt;

    /** ENROLL_STATUS (예: ACTIVE/STOP/MOVE/LEFT) — 기본 ACTIVE */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 메모(간이) */
    @Column(name = "memo", length = 255)
    private String memo;

    /**
     * 메인/교차 정보
     * - COMMON_CODE.GROUP = 'CLASS_STATUS' / CODE = 'MAIN' | 'CROSS'
     * - 기본값 MAIN (DB DEFAULT 및 PrePersist 보정)
     */
    @Column(name = "class_status_code", nullable = false, length = 32)
    private String classStatusCode;

    /**
     * 수강 요일 비트마스크 (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64)
     * - 실제 값은 student_enroll_timeslot 트리거가 fn_enroll_days_mask()로 집계하여 갱신
     * - 생성 직후(타임슬롯 미연결 상태)에는 0일 수 있음
     *
     * ⚠ 애플리케이션 레이어에서 임의로 값 세팅할 필요는 없음.
     *   타임슬롯 매핑 API를 통해 매핑을 추가/수정/삭제하면 DB가 알아서 갱신한다.
     */
    @Column(name = "attend_days_mask", nullable = false)
    private Integer attendDaysMask;

    /** 수강 요일 집계 갱신 시각(트리거가 관리) */
    @Column(name = "attend_days_updated_at")
    private LocalDateTime attendDaysUpdatedAt;

    /** 생성/수정 메타 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 마지막 수정자(@app_user_id=admin_user_info.id) — 서비스에서 세팅 */
    @Column(name = "updated_by")
    private Long updatedBy;

    /* ======================= 라이프사이클 보정 ======================= */

    @PrePersist
    public void prePersist() {
        // 상태 기본값 — 서비스에서도 보정하지만 안전망 유지
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        // 메인/교차 기본값 + 허용값 가드
        if (classStatusCode == null || classStatusCode.isBlank()) {
            classStatusCode = "MAIN";
        } else {
            classStatusCode = classStatusCode.trim().toUpperCase();
            if (!"MAIN".equals(classStatusCode) && !"CROSS".equals(classStatusCode)) {
                classStatusCode = "MAIN";
            }
        }
        // 요일 마스크: DB DEFAULT 0 이지만, null 방지 안전망
        if (attendDaysMask == null) {
            attendDaysMask = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (classStatusCode == null || classStatusCode.isBlank()) {
            classStatusCode = "MAIN";
        } else {
            classStatusCode = classStatusCode.trim().toUpperCase();
            if (!"MAIN".equals(classStatusCode) && !"CROSS".equals(classStatusCode)) {
                classStatusCode = "MAIN";
            }
        }
        if (attendDaysMask == null) {
            attendDaysMask = 0;
        }
    }
}