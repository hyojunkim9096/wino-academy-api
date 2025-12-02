// src/main/java/com/wino/academyapi/domain/tuition/controller/admin/StudentTuitionAdminController.java
package com.wino.academyapi.domain.tuition.controller.admin;

// ========================== 설명 ===========================
// 관리자용(Backoffice) 학생별 수강료/청구 API
// - 목록/추가/수정/삭제, 청구 생성, 최근 청구 조회
// - base: /api/admin/students/{studentId}
// - 서비스/DTO/엔티티/리포지토리 모두 tuition 도메인으로 이관
// =========================================================

import com.wino.academyapi.domain.tuition.dto.StudentTuitionDtos.*;
import com.wino.academyapi.domain.tuition.service.StudentTuitionAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/students/{studentId}")
@RequiredArgsConstructor
public class StudentTuitionAdminController {

    private final StudentTuitionAdminService service;

    /** 등록 목록 */
    @GetMapping("/tuitions")
    public List<TuitionAssignRes> list(@PathVariable Long studentId){
        return service.listAssignments(studentId);
    }

    /** 등록 추가: {tuitionPriceId, startMonth} */
    @PostMapping("/tuitions")
    public TuitionAssignRes add(@PathVariable Long studentId, @RequestBody TuitionAssignReq req){
        return service.addAssignment(studentId, req);
    }

    /** 등록 수정: {startMonth?, active?} (부분 수정) */
    @PutMapping("/tuitions/{assignId}")
    public TuitionAssignRes update(@PathVariable Long studentId,
                                   @PathVariable Long assignId,
                                   @RequestBody TuitionAssignUpdateReq req){
        return service.updateAssignment(studentId, assignId, req);
    }

    /** 등록 삭제: 연결 청구서가 있으면 400 (비활성 권장) */
    @DeleteMapping("/tuitions/{assignId}")
    public ResponseEntity<Void> delete(@PathVariable Long studentId,
                                       @PathVariable Long assignId,
                                       @RequestParam(defaultValue = "false") boolean force){
        service.deleteAssignment(studentId, assignId, force);
        return ResponseEntity.ok().build();
    }

    /** 월별 청구 생성 (현재 MONTH 단위만) */
    @PostMapping("/invoices/generate")
    public GenerateResult generate(@PathVariable Long studentId, @RequestBody GenerateInvoicesReq req){
        return service.generateMonthlyInvoices(studentId, req);
    }

    /** 최근 청구 N개 */
    @GetMapping("/invoices/recent")
    public List<InvoiceRes> recent(@PathVariable Long studentId, @RequestParam(defaultValue = "20") int size){
        return service.listRecentInvoices(studentId, size);
    }
}