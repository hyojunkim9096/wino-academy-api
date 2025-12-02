// src/main/java/com/wino/academyapi/domain/tuition/controller/admin/BillingConsoleController.java
package com.wino.academyapi.domain.tuition.controller.admin;

import com.wino.academyapi.domain.tuition.dto.BillingConsoleDtos.PreviewReq;
import com.wino.academyapi.domain.tuition.dto.BillingConsoleDtos.PreviewRes;
import com.wino.academyapi.domain.tuition.dto.BillingConsoleDtos.RunReq;
import com.wino.academyapi.domain.tuition.dto.BillingConsoleDtos.RunRes;
import com.wino.academyapi.domain.tuition.service.BillingConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 수강료 일괄 청구 콘솔 Controller
 *
 * 경로 규칙: /api/admin/** (프로젝트 표준)
 * - 미리보기: GET  /api/admin/tuition/billing/preview
 * - 실행   : POST /api/admin/tuition/billing/run
 *
 * month 형식은 'yyyy-MM' 을 강제.
 */
@RestController
@RequestMapping("/api/admin/tuition/billing")
@RequiredArgsConstructor
public class BillingConsoleController {

    private final BillingConsoleService service;

    /** 미리보기(Dry-run). 예: /preview?month=2025-11&stage=E&workLocationCode=W&onlyActiveStudents=true&limit=500 */
    @GetMapping("/preview")
    public PreviewRes preview(
            @RequestParam String month,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String workLocationCode,
            @RequestParam(required = false, defaultValue = "true") boolean onlyActiveStudents,
            @RequestParam(required = false, defaultValue = "500") int limit
    ) {
        return service.preview(new PreviewReq(month, stage, workLocationCode, onlyActiveStudents, limit));
    }

    /** 실행(일괄 청구 생성). body에 month 등 조건과 선택 실행 시 tuitionIds 포함 가능 */
    @PostMapping("/run")
    public RunRes run(@RequestBody RunReq req) {
        return service.run(req);
    }
}