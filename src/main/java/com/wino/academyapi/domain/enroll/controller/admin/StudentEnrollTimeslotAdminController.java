// src/main/java/com/wino/academyapi/domain/enroll/controller/admin/StudentEnrollTimeslotAdminController.java
package com.wino.academyapi.domain.enroll.controller.admin;

import com.wino.academyapi.domain.enroll.service.StudentEnrollTimeslotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/admin/enrollments/{enrollId}/timeslots — 타임슬롯 매핑 관리
 *
 * - GET : 매핑된 timeslotId 배열 조회
 * - PUT : 전달받은 timeslotId 배열로 "전체 치환"
 *
 * 주의
 * - 요일 마스크(attend_days_mask) 갱신은 DB 트리거가 자동 처리
 * - 유효성(반-타임슬롯 일치, 기간, use_yn 등)도 트리거에서 검증됨
 */
@RestController
@RequestMapping("/api/admin/enrollments/{enrollId}/timeslots")
@RequiredArgsConstructor
public class StudentEnrollTimeslotAdminController {

    private final StudentEnrollTimeslotService service;

    /** 조회: 해당 배정의 timeslotId 배열 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Long>> list(@PathVariable Long enrollId) {
        return ResponseEntity.ok(service.listTimeslotIds(enrollId));
    }

    /**
     * 치환: 요청 바디는 단순 배열 형태를 권장
     * 예) [17, 19]  또는  []
     * null/빈 배열 → 모든 매핑 제거
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Long>> replace(
            @PathVariable Long enrollId,
            @RequestBody(required = false) List<Long> timeslotIds
    ) {
        return ResponseEntity.ok(service.replaceTimeslots(enrollId, timeslotIds));
    }
}