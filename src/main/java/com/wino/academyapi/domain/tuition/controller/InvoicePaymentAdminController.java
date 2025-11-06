package com.wino.academyapi.domain.tuition.controller;

import com.wino.academyapi.domain.tuition.dto.StudentTuitionDtos.PayReq;
import com.wino.academyapi.domain.tuition.service.StudentTuitionAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 납부 처리 전용(학생ID와 무관하게 청구서ID로 접근)
 * - base: /api/admin/invoices/{invoiceId}
 * - 요청 PayReq 에 memo(선택)가 포함됨.
 * - 서비스는 tuition 도메인으로 이동됨(재사용/응집 강화)
 */
@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
public class InvoicePaymentAdminController {

    private final StudentTuitionAdminService service;

    /** 납부완료 처리 (결제 레코드 저장 → 상태/누적금액은 DB 트리거가 집계) */
    @PostMapping("/{invoiceId}/pay")
    public ResponseEntity<Void> pay(@PathVariable Long invoiceId, @RequestBody PayReq req){
        service.payInvoice(invoiceId, req);
        return ResponseEntity.ok().build();
    }
}