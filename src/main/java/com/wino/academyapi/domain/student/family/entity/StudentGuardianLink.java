// src/main/java/com/wino/academyapi/domain/student/family/entity/StudentGuardianLink.java
package com.wino.academyapi.domain.student.family.entity;

import com.wino.academyapi.domain.guardian.entity.Guardian;
import com.wino.academyapi.domain.student.entity.Student;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * DDL: student_guardian_link (학생-보호자 연결)
 *  - (student_id, guardian_id) 유니크
 *  - 다양한 불리언 플래그
 *  - 히스토리/트리거는 DB에서 처리(DDL 참조)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "student_guardian_link",
        uniqueConstraints = @UniqueConstraint(name = "uk_student_guardian",
                columnNames = {"student_id", "guardian_id"}),
        indexes = {
                @Index(name = "idx_sgl_student", columnList = "student_id, relation_code, is_primary"),
                @Index(name = "idx_sgl_guardian", columnList = "guardian_id")
        }
)
public class StudentGuardianLink {

    /** 링크 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학생 FK */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "student_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sgl_student")
    )
    private Student student;

    /** 보호자 FK */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "guardian_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sgl_guardian")
    )
    private Guardian guardian;

    /** 관계 코드 */
    @Column(name = "relation_code", nullable = false, length = 32)
    private String relationCode;

    /** 대표 여부 */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    /** 법정대리인 여부 */
    @Column(name = "legal_guardian", nullable = false)
    private boolean legalGuardian;

    /** 학사 알림 수신 여부 */
    @Column(name = "receive_notice", nullable = false)
    private boolean receiveNotice;

    /** 청구 알림 수신 여부 */
    @Column(name = "receive_billing", nullable = false)
    private boolean receiveBilling;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
