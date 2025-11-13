// src/main/java/com/wino/academyapi/domain/student/entity/StudentSibling.java
package com.wino.academyapi.domain.student.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 형제/자매 연결(무방향) 엔티티 (student_sibling)
 * - DDL 트리거(trg_sibling_bi_normalize)가 studentId1/2를 low_id/high_id로 자동 정규화합니다.
 * - JPA는 studentId1, studentId2 필드에 값을 "쓰고",
 * - low, high 필드(FK)를 통해 정규화된 학생 정보를 "읽습니다".
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
     * 학생1 (임시 입력값)
     * - 이 필드는 오직 INSERT/UPDATE 시 DB 트리거에 값을 전달하기 위해 사용됩니다.
     * - 읽기(조회)에는 사용하지 않습니다.
     */
    @Column(name = "student_id_1", nullable = false, updatable = false)
    private Long studentId1;

    /**
     * 학생2 (임시 입력값)
     * - 이 필드는 오직 INSERT/UPDATE 시 DB 트리거에 값을 전달하기 위해 사용됩니다.
     * - 읽기(조회)에는 사용하지 않습니다.
     */
    @Column(name = "student_id_2", nullable = false, updatable = false)
    private Long studentId2;

    /**
     * 정규화된 학생1 (ID가 더 작은 쪽)
     * - DDL 트리거가 채우므로 insertable/updatable = false
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "low_id", nullable = false, insertable = false, updatable = false)
    private Student low;

    /**
     * 정규화된 학생2 (ID가 더 큰 쪽)
     * - DDL 트리거가 채우므로 insertable/updatable = false
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "high_id", nullable = false, insertable = false, updatable = false)
    private Student high;

    @Column(name = "relation_note", length = 100)
    private String relationNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}