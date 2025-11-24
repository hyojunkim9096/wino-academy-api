// src/main/java/com/wino/academyapi/domain/student/family/controller/StudentFamilyController.java
package com.wino.academyapi.domain.student.family.controller;

import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.LinkCreateRequest;
import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.LinkUpdateRequest;
import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.service.StudentFamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/admin/students/{studentId}/guardians — 학생 하위 "가족(보호자) 연결" 관리
 * - 프런트는 여기만 호출하면 됨(목록/추가/수정/삭제)
 */
@RestController
@RequestMapping("/api/admin/students/{studentId}/guardians")
@RequiredArgsConstructor
public class StudentFamilyController {

    private final StudentFamilyService service;

    /** 가족(보호자) 연결 목록 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<GuardianLinkSummary>> list(@PathVariable Long studentId) {
        return ResponseEntity.ok(service.listGuardiansByStudent(studentId));
    }

    /**
     * 가족(보호자) 연결 추가
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> add(@PathVariable Long studentId, @RequestBody LinkCreateRequest req) {
        req.setStudentId(studentId);
        Long linkId = service.createLink(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkId);
    }

    /** ✅ [통합] 가족(보호자) 연결 수정 (관계, 대표여부 등)
     * - 기존 StudentFamilyAdminController 기능 통합
     */
    @PutMapping(value = "/{linkId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long studentId,
                                       @PathVariable Long linkId,
                                       @RequestBody LinkUpdateRequest req) {
        service.updateLink(linkId, req);
        return ResponseEntity.noContent().build();
    }

    /** 가족(보호자) 연결 삭제 */
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> remove(@PathVariable Long studentId, @PathVariable Long linkId) {
        service.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }
}