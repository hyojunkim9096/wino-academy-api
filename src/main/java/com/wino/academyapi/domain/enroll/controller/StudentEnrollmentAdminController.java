// src/main/java/com/wino/academyapi/domain/enroll/controller/StudentEnrollmentAdminController.java
package com.wino.academyapi.domain.enroll.controller;

import com.wino.academyapi.domain.enroll.dto.EnrollmentDtos.*;
import com.wino.academyapi.domain.enroll.service.StudentEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * /api/admin/students/{studentId}/enrollments — 반 배정 관리(특정 학생 기준)
 *
 * 기능
 * - 목록: Page<EnrollmentSummary>
 * - 생성: 201 + PK(Long)
 * - 수정: 204
 * - 삭제: 204
 *
 * 2025-10 스키마 반영
 * - ✅ classStatusCode 사용(MAIN/CROSS)
 * - ✅ 등원 요일은 student_enroll_timeslot 매핑으로 관리
 *
 * 사용 가이드
 * - 이 컨트롤러는 "배정 행(Row)" 자체를 생성/수정/삭제합니다.
 * - ⚠️ timeslotIds 전달 방식:
 *     · 생성/수정 요청 바디(EnrollmentCreateRequest/EnrollmentUpdateRequest)에
 *       timeslotIds를 **옵션으로 포함하면**, 서비스가 해당 배열로 **전체 치환**합니다.
 *       (빈 배열 -> 전부 제거, null -> 기존 유지)
 *     · 별도의 전용 엔드포인트
 *       /api/admin/enrollments/{enrollId}/timeslots (GET/PUT)도 제공합니다.
 *       초기 생성 후 따로 치환하고 싶을 때 사용하세요.
 * - attendDaysMask는 **DB 트리거**가 타임슬롯 매핑 기준으로 자동 집계합니다.
 */
@RestController
@RequestMapping("/api/admin/students/{studentId}/enrollments")
@RequiredArgsConstructor
public class StudentEnrollmentAdminController {

    private final StudentEnrollmentService service;

    /** 특정 학생 배정 목록 (최신 시작일 우선) */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<EnrollmentSummary>> list(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ResponseEntity.ok(service.listByStudent(studentId, safePage, safeSize));
    }

    /**
     * 배정 등록
     * - Body: EnrollmentCreateRequest
     *   · 필수: classId, enrolledAt
     *   · 선택: status, memo, classStatusCode(MAIN/CROSS)
     *   · 선택: timeslotIds (전달 시 해당 배열로 매핑 **전체 치환**)
     * - 반환: 생성된 enrollment PK
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> create(
            @PathVariable Long studentId,
            @RequestBody EnrollmentCreateRequest req
    ) {
        Long id = service.create(studentId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * 배정 수정
     * - Body: EnrollmentUpdateRequest
     *   · 선택: leftAt, status, memo, classStatusCode(MAIN/CROSS)
     *   · 선택: timeslotIds (전달 시 해당 배열로 매핑 **전체 치환**)
     * - 성공 시 204
     */
    @PutMapping(value = "/{enrollId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(
            @PathVariable Long studentId, // 경로 정합성용(서비스는 enrollId 기준 처리)
            @PathVariable Long enrollId,
            @RequestBody EnrollmentUpdateRequest req
    ) {
        service.update(enrollId, req);
        return ResponseEntity.noContent().build();
    }

    /** 배정 삭제 — 매핑(student_enroll_timeslot)은 FK CASCADE로 함께 삭제됨 */
    @DeleteMapping("/{enrollId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long studentId, // 경로 정합성용
            @PathVariable Long enrollId
    ) {
        service.delete(enrollId);
        return ResponseEntity.noContent().build();
    }
}