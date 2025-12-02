// src/main/java/com/wino/academyapi/domain/consult/controller/admin/ConsultAdminController.java
package com.wino.academyapi.domain.consult.controller.admin;

import com.wino.academyapi.domain.consult.dto.ConsultDtos.*;
import com.wino.academyapi.domain.consult.service.ConsultAdminService;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/admin/students/{studentId}/consults — 학생 상세 화면용 프록시 컨트롤러
 *
 * 캐논컬(정규) 컬렉션은 /api/admin/consults.
 * 여기서는 학생 상세 화면 편의상 '학생별 목록/등록'을 제공합니다.
 *
 * ⚠️ JSON 바디의 일시(LocalDateTime)는 ISO-8601 형식(예: "2025-10-26T05:19:54")을 기대합니다.
 *    프런트에서 'T'가 포함된 문자열을 보내야 합니다. (스페이스 형식은 기본 파서가 불가)
 */
@RestController
@RequestMapping("/api/admin/students/{studentId}/consults")
@Validated
public class ConsultAdminController {

    private final ConsultAdminService service;

    public ConsultAdminController(ConsultAdminService service) {
        this.service = service;
    }

    /** 목록(학생별) */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<ConsultSummary>> list(
            @PathVariable Long studentId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="30") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ResponseEntity.ok(service.listByStudent(studentId, safePage, safeSize));
    }

    /** 단건 조회(학생 경로이지만 id로 바로 조회) */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsultSummary> get(@PathVariable Long studentId, @PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    /**
     * 생성 — Body의 studentId가 없어도 됨(경로 변수로 대체).
     * 여기서는 PathCreate 그룹 검증만 적용(consultMethod/type/title/content/consultAt 등 필수),
     * CanonicalCreate 그룹(@NotNull studentId)은 적용하지 않습니다.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> create(
            @PathVariable Long studentId,
            @RequestBody @Validated(PathCreate.class) ConsultCreateRequest req
    ) {
        // 검증 이후 경로 변수를 Body에 주입(여기서야 가능)
        req.setStudentId(studentId);
        Long id = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** 수정(부분 업데이트, 필드 검증 없음) */
    @PutMapping(value="/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(
            @PathVariable Long studentId,
            @PathVariable Long id,
            @RequestBody ConsultUpdateRequest req
    ) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long studentId, @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}