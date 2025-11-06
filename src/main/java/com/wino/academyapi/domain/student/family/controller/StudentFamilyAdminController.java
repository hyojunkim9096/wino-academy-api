package com.wino.academyapi.domain.student.family.controller;

import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.dto.StudentLinkSummary;
import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.*;
import com.wino.academyapi.domain.student.family.service.StudentFamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/admin/links — 내부/백오피스/통계 보조용 엔드포인트(유지)
 * - 프런트 일반화면은 사용 안 해도 됨.
 * - 기존 /api/admin/links/* 를 대체(패키지만 이동).
 */
@RestController
@RequestMapping("/api/admin/links")
@RequiredArgsConstructor
public class StudentFamilyAdminController {

    private final StudentFamilyService service;

    /** 특정 학생 기준 가족(보호자) 목록 */
    @GetMapping(value = "/by-student/{studentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<GuardianLinkSummary>> listByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(service.listGuardiansByStudent(studentId));
    }

    /** 특정 보호자 기준 연결된 학생 목록 */
    @GetMapping(value = "/by-guardian/{guardianId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StudentLinkSummary>> listByGuardian(@PathVariable Long guardianId) {
        return ResponseEntity.ok(service.listStudentsByGuardian(guardianId));
    }

    /** 링크 생성 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> create(@RequestBody LinkCreateRequest req) {
        Long id = service.createLink(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** 링크 수정 */
    @PutMapping(value="/{linkId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long linkId, @RequestBody LinkUpdateRequest req) {
        service.updateLink(linkId, req);
        return ResponseEntity.noContent().build();
    }

    /** 링크 삭제 */
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> delete(@PathVariable Long linkId) {
        service.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }
}