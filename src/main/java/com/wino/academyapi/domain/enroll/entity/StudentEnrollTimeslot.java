// src/main/java/com/wino/academyapi/domain/enroll/entity/StudentEnrollTimeslot.java
package com.wino.academyapi.domain.enroll.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * student_enroll_timeslot 매핑 엔티티
 *
 * - 각 행은 "한 배정(enroll) ↔ 한 타임슬롯(timeslot)" 연결을 의미
 * - DDL: UNIQUE (enroll_id, timeslot_id)
 * - 유효성/히스토리/요일집계는 DB 트리거가 처리
 *   · 유효성: 해당 enroll의 class_id에 속한 timeslot인지 검증
 *   · 히스토리: *_hist 테이블에 적재
 *   · 요일집계: fn_enroll_days_mask 로 student_class_enrollment.attend_days_mask 갱신
 *
 * 설계 포인트:
 * - 단순 FK 숫자 보관 전략(연관 엔티티 미지정)으로 의존성/로딩비용 최소화
 * - 치환(Replace) 정책을 서비스에서 구현 → 트리거가 마스크/이력 자동 반영
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "student_enroll_timeslot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_setl_unique",
                columnNames = {"enroll_id", "timeslot_id"}
        ),
        indexes = {
                @Index(name = "idx_setl_enroll", columnList = "enroll_id"),
                @Index(name = "idx_setl_timeslot", columnList = "timeslot_id")
        }
)
public class StudentEnrollTimeslot {

    /** PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 배정 FK (student_class_enrollment.id) — CASCADE */
    @Column(name = "enroll_id", nullable = false)
    private Long enrollId;

    /** 타임슬롯 FK (class_timeslot.id) — RESTRICT */
    @Column(name = "timeslot_id", nullable = false)
    private Long timeslotId;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 마지막 수정자(@app_user_id = admin_user_info.id). NULL 가능 */
    @Column(name = "updated_by")
    private Long updatedBy;
}