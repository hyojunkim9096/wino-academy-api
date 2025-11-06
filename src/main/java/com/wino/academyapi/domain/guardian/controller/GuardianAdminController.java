// src/main/java/com/wino/academyapi/domain/guardian/controller/GuardianAdminController.java
package com.wino.academyapi.domain.guardian.controller;

import com.wino.academyapi.domain.guardian.dto.GuardianDtos.*;
import com.wino.academyapi.domain.guardian.service.GuardianAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** /api/admin/guardians — 보호자 관리 */
@RestController
@RequestMapping("/api/admin/guardians")
@RequiredArgsConstructor
public class GuardianAdminController {

    private final GuardianAdminService service;

    /** 목록 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<GuardianSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(service.list(keyword, safePage, safeSize));
    }

    /** 상세 */
    @GetMapping(value="/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuardianSummary> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** 생성 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuardianSummary> create(@RequestBody GuardianCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /** 수정 */
    @PutMapping(value="/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody GuardianUpdateRequest req) {
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