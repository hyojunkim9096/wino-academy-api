// src/main/java/com/wino/academyapi/domain/guardian/controller/admin/GuardianAdminController.java
package com.wino.academyapi.domain.guardian.controller.admin;

import com.wino.academyapi.domain.guardian.dto.GuardianDtos.*;
import com.wino.academyapi.domain.guardian.service.GuardianAdminService;
import com.wino.academyapi.domain.student.family.dto.StudentLinkSummary;
import com.wino.academyapi.domain.student.family.service.StudentFamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

/**
 * /api/admin/guardians — 보호자 관리
 */
@RestController
@RequestMapping("/api/admin/guardians")
@RequiredArgsConstructor
public class GuardianAdminController {

    private final GuardianAdminService service;

    // ✅ 가족(학생) 조회용 서비스 주입
    private final StudentFamilyService familyService;

    /** 목록 (N+1 해결된 DTO 프로젝션 사용) */
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

    /** 상세 (계정 정보 포함) */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuardianSummary> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** 생성 (프로필만 생성) */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuardianSummary> create(@RequestBody GuardianCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /** 수정 (프로필 수정 + 연결된 계정 정보 동기화) */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long id,
                                       @RequestBody GuardianUpdateRequest req) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }

    /** 삭제 (프로필 + 계정 연결(map) 동시 삭제) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // ✅ 계정 연결/해제 API
    // =====================================================================

    /**
     * 보호자 프로필에 신규 EndUser 계정을 생성하고 연결합니다.
     * @param id  보호자(guardian) ID
     * @param req 계정 정보 (loginId, password)
     */
    @PostMapping(value = "/{id}/link-account", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> linkAccount(@PathVariable Long id,
                                            @Valid @RequestBody AccountLinkRequest req) {
        service.linkAccount(id, req);
        return ResponseEntity.ok().build();
    }

    /**
     * 보호자 프로필과 EndUser 계정의 연결을 해제합니다.
     * (계정 자체는 삭제하지 않습니다)
     * @param id 보호자(guardian) ID
     */
    @PostMapping(value = "/{id}/unlink-account")
    public ResponseEntity<Void> unlinkAccount(@PathVariable Long id) {
        service.unlinkAccount(id);
        return ResponseEntity.ok().build();
    }

    // =====================================================================
    // ✅ 보호자 기준 학생 목록 조회 API
    //   - 프런트 GuardianDetailPanel 에서 사용
    //   - 내부 구현은 student.family 도메인(StudentFamilyService) 재사용
    // =====================================================================

    /**
     * 보호자와 연결된 학생 목록 조회
     * GET /api/admin/guardians/{id}/students
     *
     * 반환 타입: StudentLinkSummary
     * - id: 링크 PK
     * - studentId / studentName / schoolStage / workLocationCode / status
     * - relationCode / primary / legalGuardian / receiveNotice / receiveBilling
     */
    @GetMapping(value = "/{id}/students", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StudentLinkSummary>> listStudents(@PathVariable Long id) {
        return ResponseEntity.ok(familyService.listStudentsByGuardian(id));
    }
}
