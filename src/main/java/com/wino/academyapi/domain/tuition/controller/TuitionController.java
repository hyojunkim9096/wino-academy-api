package com.wino.academyapi.domain.tuition.controller;

import com.wino.academyapi.domain.tuition.dto.TuitionDtos.*;
import com.wino.academyapi.domain.tuition.service.TuitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 수강료 카테고리/가격 관리용 Admin 컨트롤러
 * - base: /api/admin/tuition
 * - 학년 필터 파라미터는 gradeCode(camel)와 grade_code(snake)를 모두 지원한다.
 */
@RestController
@RequestMapping("/api/admin/tuition")
@RequiredArgsConstructor
public class TuitionController {

    private final TuitionService service;

    // ============================ Category ============================

    @GetMapping("/categories")
    public List<CategoryRes> listCategories(@RequestParam String stage,
                                            @RequestParam(required = false) Long parentId) {
        return service.listCategories(stage, parentId);
    }

    @PostMapping("/categories")
    public CategoryRes createCategory(@RequestHeader(value = "X-App-User-Id", required = false) Long appUserId,
                                      @RequestBody CategoryUpsertReq req) {
        return service.createCategory(req, appUserId);
    }

    @PutMapping("/categories/{id}")
    public CategoryRes updateCategory(@RequestHeader(value = "X-App-User-Id", required = false) Long appUserId,
                                      @PathVariable Long id,
                                      @RequestBody CategoryUpsertReq req) {
        return service.updateCategory(id, req, appUserId);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@RequestHeader(value = "X-App-User-Id", required = false) Long appUserId,
                                               @PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean force) {
        service.deleteCategory(id, force, appUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/categories/reorder")
    public ResponseEntity<Void> reorder(@RequestHeader(value = "X-App-User-Id", required = false) Long appUserId,
                                        @RequestBody ReorderReq req) {
        service.reorder(req.stage(), req.parentId(), req.orderedIds(), appUserId);
        return ResponseEntity.ok().build();
    }

    // ============================== Prices ==============================

    @GetMapping("/categories/{categoryId}/prices")
    public List<PriceRowRes> listPrices(@PathVariable Long categoryId) {
        return service.listPrices(categoryId);
    }

    @PutMapping("/categories/{categoryId}/prices")
    public ResponseEntity<Void> replacePrices(@RequestHeader(value = "X-App-User-Id", required = false) Long appUserId,
                                              @PathVariable Long categoryId,
                                              @RequestBody List<PriceRowReq> rows) {
        service.replacePrices(categoryId, rows, appUserId);
        return ResponseEntity.ok().build();
    }

    // ===================== Active Prices (flat) =====================

    /** 하위호환: /active-prices?stage=E */
    @GetMapping("/active-prices")
    public List<ActivePriceRes> listActivePrices(@RequestParam String stage) {
        return service.listActivePricesByStage(stage);
    }

    /**
     * 프런트 합의 경로 — /prices/active?stage=E&gradeCode=E01 (또는 grade_code=E01)
     * - gradeCode(camel) / grade_code(snake) 모두 수용한다.
     */
    @GetMapping("/prices/active")
    public List<ActivePriceRes> listActivePricesByStageAndGrade(
            @RequestParam String stage,
            @RequestParam(value = "gradeCode", required = false) String gradeCode,
            @RequestParam(value = "grade_code", required = false) String grade_code
    ){
        String gc = (gradeCode != null && !gradeCode.isBlank()) ? gradeCode : grade_code;
        return service.listActivePricesByStage(stage, gc);
    }

    /** 학부별 학년 코드 조회(공통코드 래핑) */
    @GetMapping("/grade-codes")
    public List<GradeCodeRes> listGradeCodes(@RequestParam String stage){
        return service.listGradeCodesByStage(stage);
    }
}