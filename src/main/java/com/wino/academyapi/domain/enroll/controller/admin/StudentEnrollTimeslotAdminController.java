package com.wino.academyapi.domain.enroll.controller.admin;

import com.wino.academyapi.domain.enroll.service.StudentEnrollTimeslotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin") // 경로 주의: 클래스 레벨 매핑이 아닌 메서드 레벨로 조정
@RequiredArgsConstructor
public class StudentEnrollTimeslotAdminController {

    private final StudentEnrollTimeslotService service;

    // 배정별 타임슬롯 조회
    @GetMapping("/enrollments/{enrollId}/timeslots")
    public ResponseEntity<List<Long>> list(@PathVariable Long enrollId) {
        return ResponseEntity.ok(service.listTimeslotIds(enrollId));
    }

    // 배정별 타임슬롯 치환
    @PutMapping(value = "/enrollments/{enrollId}/timeslots", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Long>> replace(
            @PathVariable Long enrollId,
            @RequestBody(required = false) List<Long> timeslotIds
    ) {
        return ResponseEntity.ok(service.replaceTimeslots(enrollId, timeslotIds));
    }

    // ✅ [추가] 학생별 점유 타임슬롯 조회 (충돌 방지용)
    @GetMapping("/students/{studentId}/occupied-timeslots")
    public ResponseEntity<List<Long>> getOccupiedTimeslots(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long excludeEnrollId
    ) {
        return ResponseEntity.ok(service.getOccupiedTimeslotIds(studentId, excludeEnrollId));
    }
}