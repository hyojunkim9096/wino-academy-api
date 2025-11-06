package com.wino.academyapi.domain.tuition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 학생별 수강료 "등록" 행
 * - 어떤 학생이 어떤 가격표(TuitionPrice)를 언제부터 적용받는지
 * - 청구 생성은 이 등록을 기준으로 월별로 파생 생성
 * - DDL의 UNIQUE/INDEX 명칭과 일치
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "student_tuition",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_stu_assign_active",
                        columnNames = {"student_id", "tuition_price_id", "active"}
                )
        },
        indexes = {
                @Index(name = "ix_stu_student_active", columnList = "student_id,active"),
                @Index(name = "ix_stu_price",          columnList = "tuition_price_id")
        }
)
public class StudentTuition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학생 PK */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 가격표 PK (tuition_price.id) */
    @Column(name = "tuition_price_id", nullable = false)
    private Long tuitionPriceId;

    /** 적용 시작월 'YYYY-MM' (CHAR(7)) */
    @Column(name = "start_month", nullable = false, length = 7)
    private String startMonth;

    /** 사용 여부 (중지 시 false) */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}