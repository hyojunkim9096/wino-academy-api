package com.wino.academyapi.domain.student.family.controller;

import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.LinkCreateRequest;
import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.service.StudentFamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/admin/students/{studentId}/guardians  — 학생 하위 "가족(보호자) 연결" 서브리소스
 * - 프런트는 여기만 호출하면 됨(목록/추가/삭제)
 * - 내부 구현은 Service가 수행. 도메인 로직은 student.family 패키지에 응집.
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
     * Body 예:
     * {
     *   "guardianId": 10,
     *   "relationCode": "MOTHER",
     *   "primary": true,
     *   "legalGuardian": true,
     *   "receiveNotice": true,
     *   "receiveBilling": true
     * }
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> add(@PathVariable Long studentId, @RequestBody LinkCreateRequest req) {
        // path-variable 우선: body가 studentId를 안보내도 됨
        req.setStudentId(studentId);
        Long linkId = service.createLink(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkId);
    }

    /** 가족(보호자) 연결 삭제 */
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> remove(@PathVariable Long studentId, @PathVariable Long linkId) {
        // studentId는 권한/소유 검증에 활용 가능(여기선 linkId만으로 삭제)
        service.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }
}