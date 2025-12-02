// src/main/java/com/wino/academyapi/domain/tuition/dto/StudentTuitionDtos.java
package com.wino.academyapi.domain.tuition.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 학생 수강료/청구/납부 DTO 모음
 * - 프런트/백 간 계약 객체
 * - startMonth/billMonth: 'YYYY-MM'
 * - paidAt: 'yyyy-MM-dd HH:mm:ss'
 */
public class StudentTuitionDtos {

    /* ======================= 수강료 등록(Assignment) ======================= */

    /** 등록 요청: 가격표 ID + 시작월(YYYY-MM) */
    public record TuitionAssignReq(
            Long tuitionPriceId,
            String startMonth   // "YYYY-MM"
    ) {}

    /** 등록 수정 요청: 시작월/활성 여부 (부분 수정) */
    public record TuitionAssignUpdateReq(
            String startMonth,  // nullable
            Boolean active      // nullable
    ) {}

    /** 등록 응답/목록 행 */
    public record TuitionAssignRes(
            Long id,
            Long priceId,
            String unit,          // 'MONTH' | 'TERM' | 'SESSION'
            BigDecimal price,
            String startMonth,    // 'YYYY-MM'
            boolean active,
            String priceName,     // "월 수강료"
            String categoryPath   // "수학/중1/심화"
    ) {}

    /* =========================== 청구(Invoice) ============================ */

    public record GenerateInvoicesReq(
            String fromMonth, // 'YYYY-MM'
            String toMonth    // 'YYYY-MM'
    ) {}

    public record InvoiceRes(
            Long id,
            String billMonth,     // 'YYYY-MM'
            String itemName,
            BigDecimal amount,
            String status         // 'PENDING' | 'PARTIAL' | 'PAID' | 'CANCELED'
    ) {}

    public record GenerateResult(int createdCount) {}

    /* =========================== 납부(Payment) ============================ */

    public record PayReq(
            String paidAt,    // 'yyyy-MM-dd HH:mm:ss'
            BigDecimal amount,
            String method,    // 'CASH' | 'CARD' | 'TRANSFER' ... (서비스에서 기본값 보정)
            String memo       // 선택
    ) {}

    public record PayRes(
            Long invoiceId,
            String status,       // 'PAID'
            LocalDateTime paidAt
    ) {}
}