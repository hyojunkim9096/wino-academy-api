// src/main/java/com/wino/academyapi/domain/consult/controller/admin/ConsultAdminFlatController.java
package com.wino.academyapi.domain.consult.controller.admin;

import com.wino.academyapi.domain.consult.dto.ConsultDtos.*;
import com.wino.academyapi.domain.consult.service.ConsultAdminService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/consults")
@Validated
public class ConsultAdminFlatController {

    private final ConsultAdminService service;

    public ConsultAdminFlatController(ConsultAdminService service) {
        this.service = service;
    }

    /** ✅ [수정] 검색(필터링) — 이름(studentName, writerName) 파라미터 추가 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<ConsultSummary>> search(
            // ID
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long writerId,
            //  ( )
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String writerName,
            //
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="30") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        // ✅ 서비스 호출 시 이름 파라미터 전달
        return ResponseEntity.ok(service.search(
                studentId, studentName,
                writerId, writerName,
                from, to, safePage, safeSize
        ));
    }

    /** 등록 (StudentAdminPage에서 수행하므로 여기서는 유지) */
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

    /** 수정 (모달에서 사용) */
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

    /** ✅ [신규] 팀장/관리자 승인 (유지) */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return ResponseEntity.ok().build();
    }
}