package com.wino.academyapi.domain.tuition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 납부 내역(간이 매핑)
 * - status/channel 등은 DB DEFAULT & 트리거로 관리(미매핑)
 * - DDL 컬럼/인덱스 길이와 일치
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "student_payment",
        indexes = @Index(name = "ix_sp_invoice", columnList = "invoice_id")
)
public class StudentPayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="invoice_id", nullable = false)
    private Long invoiceId;

    /** 납부일시 */
    @Column(name="paid_at", nullable = false)
    private LocalDateTime paidAt;

    /** 납부금액 */
    @Column(name="amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    /** 'CASH' | 'TRANSFER' | 'CARD' ... (NOT NULL, 서비스에서 기본값 보장) */
    @Column(name="method", nullable = false, length = 20)
    private String method;

    @Column(name="memo", length = 300) // DDL 동일(300)
    private String memo;

    @CreationTimestamp
    @Column(name="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}