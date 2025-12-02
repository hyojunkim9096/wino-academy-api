// src/main/java/com/wino/academyapi/domain/tuition/entity/StudentInvoice.java
package com.wino.academyapi.domain.tuition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 학생 청구서(월별 헤더)
 * - DB 트리거/프로시저가 paid_amount/paid_at/status를 관리
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "student_invoice",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_si_month_item",
                columnNames = {"student_id", "bill_month", "tuition_price_id"}
        ),
        indexes = {
                @Index(name = "ix_si_student_month", columnList = "student_id,bill_month"),
                @Index(name = "ix_si_status",       columnList = "status"),
                @Index(name = "ix_si_student_stat", columnList = "student_id,status,bill_month"),
                @Index(name = "ix_si_tuition",      columnList = "tuition_id")
        }
)
public class StudentInvoice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="student_id", nullable = false)
    private Long studentId;

    /** 어떤 등록으로부터 파생됐는지(통계/추적용) */
    @Column(name="tuition_id")
    private Long tuitionId;

    /** 가격표 PK(등록 당시 가격 기준) */
    @Column(name="tuition_price_id", nullable = false)
    private Long tuitionPriceId;

    /** 청구월 'YYYY-MM' */
    @Column(name="bill_month", nullable = false, length = 7)
    private String billMonth;

    /** 품목명(경로/라벨 합성값) */
    @Column(name="item_name", nullable = false, length = 200)
    private String itemName;

    /** 총 청구금액(정수원) */
    @Column(name="amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    /** 'PENDING' | 'PARTIAL' | 'PAID' | 'CANCELED' */
    @Column(name="status", nullable = false, length = 16)
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name="updated_at", nullable = false)
    private LocalDateTime updatedAt;
}