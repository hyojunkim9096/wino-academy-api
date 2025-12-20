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
 * - JPA: StudentClassEnrollment 와 @ManyToOne 관계 설정 (조인 쿼리 지원)
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ✅ [수정] 단순 ID 대신 연관관계 매핑
     * - Repository에서 JOIN 쿼리를 사용하기 위해 필요
     * - FetchType.LAZY로 설정하여 불필요한 로딩 방지
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enroll_id", nullable = false)
    private StudentClassEnrollment enrollment;

    /** 타임슬롯 FK (class_timeslot.id) — 타임슬롯은 단순 참조용이라 ID 유지 */
    @Column(name = "timeslot_id", nullable = false)
    private Long timeslotId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}