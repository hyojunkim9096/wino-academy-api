package com.wino.academyapi.domain.tuition.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Billing Console(일괄 청구) DTO 모음
 * - 프런트/백 계약을 한 파일로 관리
 * - month 형식: 'yyyy-MM'
 */
public class BillingConsoleDtos {

    /* =========================[ Preview ]========================= */

    /** 미리보기 요청 파라미터 */
    public record PreviewReq(
            String month,                // yyyy-MM (필수)
            String stage,                // E/M/H (선택)
            String workLocationCode,     // 지점 코드 (선택)
            Boolean onlyActiveStudents,  // ACTIVE 학생만? (기본 true)
            Integer limit                // 화면 상한 (기본 500)
    ) {}

    /** 미리보기 표 행 */
    public record ItemPreview(
            Long studentId,
            String studentName,
            String schoolStage,       // E/M/H
            String workLocationCode,  // 지점 코드

            Long tuitionId,           // student_tuition.id
            Long tuitionPriceId,      // tuition_price.id

            Long categoryId,          // 리프 카테고리 ID
            String categoryName,      // 리프 카테고리명
            String categoryPath,      // "영어/리딩" (없으면 categoryName로 대체)

            String itemLabel,         // 항목 라벨(가격표 unit기반 라벨)
            String unit,              // 'MONTH' ...
            BigDecimal price,         // 정가(원)

            String month,             // yyyy-MM
            boolean alreadyBilled,    // 해당 월 청구 존재?
            String skipReason         // 스킵 사유(비월단위, 중복 등)
    ) {}

    /** 미리보기 응답 */
    public record PreviewRes(
            String month,
            long totalCandidates,
            long alreadyBilledCount,
            long creatableCount,
            List<ItemPreview> rows
    ) {}

    /* =========================[ Run ]========================= */

    /** 실행 요청 */
    public record RunReq(
            String month,                 // yyyy-MM
            String stage,                 // E/M/H
            String workLocationCode,      // 지점
            Boolean onlyActiveStudents,   // ACTIVE 학생만
            List<Long> tuitionIds         // 선택 실행(교집합)
    ) {}

    /** 실행 결과(개별) */
    public record RunItemResult(
            Long tuitionId,
            Long studentId,
            String studentName,
            String result,   // CREATED | SKIPPED | ERROR
            String message
    ) {}

    /** 실행 결과(요약) */
    public record RunRes(
            String month,
            long attempted,
            long created,
            long skipped,
            long errors,
            List<RunItemResult> items
    ) {}
}