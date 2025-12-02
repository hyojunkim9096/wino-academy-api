// src/main/java/com/wino/academyapi/domain/enroll/controller/admin/EnrollmentQueryController.java
package com.wino.academyapi.domain.enroll.controller.admin;

import com.wino.academyapi.domain.enroll.service.StudentEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * /api/admin/enrollments — 배정 관련 “조회 전용” 컨트롤러
 *  - 현재원(STATUS=ACTIVE) 집계 (ROLE 무관)
 */
@RestController
@RequestMapping("/api/admin/enrollments")
@RequiredArgsConstructor
public class EnrollmentQueryController {

    private final StudentEnrollmentService service;

    /** classIds[]에 대한 ACTIVE 현재원 집계 */
    @GetMapping("/active-count")
    public ResponseEntity<Map<Long, Long>> activeCount(@RequestParam("classIds") List<Long> classIds){
        return ResponseEntity.ok(service.countActiveByClassIds(classIds));
    }
}