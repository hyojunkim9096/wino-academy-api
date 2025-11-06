package com.wino.academyapi.domain.consult.controller;

import com.wino.academyapi.domain.consult.dto.ConsultDtos.*;
import com.wino.academyapi.domain.consult.service.ConsultAdminService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * /api/admin/consults — 상담의 "캐논컬" 상단 컬렉션.
 * - 검색/등록/단건조회/수정/삭제 제공
 * - 학생/교사 기준의 뷰(필터)는 쿼리스트링으로 처리 (studentId, writerId, from, to 등)
 *
 * ⚠️ 쿼리스트링의 from/to는 ISO-8601 형식(예: 2025-10-26T00:00:00)으로 받습니다.
 * ⚠️ JSON 바디(LocalDateTime)도 ISO-8601 형식(‘T’ 포함)을 기대합니다.
 */
@RestController
@RequestMapping("/api/admin/consults")
@Validated
public class ConsultAdminFlatController {

    private final ConsultAdminService service;

    public ConsultAdminFlatController(ConsultAdminService service) {
        this.service = service;
    }

    /** 검색(필터링) — 학생/작성자/기간 등 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<ConsultSummary>> search(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long writerId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="30") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ResponseEntity.ok(service.search(studentId, writerId, from, to, safePage, safeSize));
    }

    /**
     * 등록(캐논컬) — Body에 studentId 필수.
     * CanonicalCreate 그룹 검증을 적용하여 @NotNull(studentId) 포함 필수항목 검증.
     * (별도 수동 null 체크 불필요)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> create(
            @RequestBody @Validated(CanonicalCreate.class) ConsultCreateRequest req
    ) {
        Long id = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** 단건 조회 */
    @GetMapping(value="/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsultSummary> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** 수정(부분 업데이트, 필드 검증 없음) */
    @PutMapping(value="/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody ConsultUpdateRequest req) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}