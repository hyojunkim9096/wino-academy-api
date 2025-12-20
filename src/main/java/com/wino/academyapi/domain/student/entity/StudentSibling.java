package com.wino.academyapi.domain.student.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 형제/자매 연결 엔티티 (student_sibling)
 * --------------------------------------------------------
 * [동작 원리]
 * 1. Java 앱은 'studentId1', 'studentId2'에 값을 넣어 저장(INSERT)합니다.
 * 2. DB 트리거(trg_sibling_bi_normalize)가 자동으로 두 ID를 비교하여
 * 작은 값 -> low_id, 큰 값 -> high_id 컬럼에 값을 채워줍니다.
 * 3. Java 앱은 'low', 'high' 연관관계를 통해 정렬된 학생 정보를 조회합니다.
 * --------------------------------------------------------
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "student_sibling",
        uniqueConstraints = @UniqueConstraint(name = "uk_sibling_pair", columnNames = {"low_id", "high_id"}),
        indexes = {
                @Index(name = "idx_sibling_low", columnList = "low_id"),
                @Index(name = "idx_sibling_high", columnList = "high_id")
        }
)
public class StudentSibling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 입력용 학생 1 (DB 트리거가 사용)
     */
    @Column(name = "student_id_1", nullable = false)
    private Long studentId1;

    /**
     * 입력용 학생 2 (DB 트리거가 사용)
     */
    @Column(name = "student_id_2", nullable = false)
    private Long studentId2;

    /**
     * 정규화된 학생1 (ID가 더 작은 쪽) - 조회 전용 (Read-Only)
     * - DB 트리거가 low_id 컬럼을 채워줌
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "low_id", insertable = false, updatable = false)
    private Student low;

    /**
     * 정규화된 학생2 (ID가 더 큰 쪽) - 조회 전용 (Read-Only)
     * - DB 트리거가 high_id 컬럼을 채워줌
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "high_id", insertable = false, updatable = false)
    private Student high;

    @Column(name = "relation_note", length = 100)
    private String relationNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}