// src/main/java/com/wino/academyapi/domain/timetable/controller/admin/TimetableController.java
package com.wino.academyapi.domain.timetable.controller.admin;

import com.wino.academyapi.domain.timetable.dto.TimetableDtos.EventRes;
import com.wino.academyapi.domain.timetable.service.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 시간표 API
 * - 단일 교사    : GET /api/admin/timetable/teacher-events?teacherId=10[&semesterId=1][&workLocation=W]
 * - 전체 교사    : GET /api/admin/timetable/teacher-events-all[&semesterId=1][&workLocation=W]
 * - 반 기준      : GET /api/admin/timetable/class-events?workLocation=GN&classId=123
 *
 * ★ 학기(semesterId)는 선택 파라미터.
 *   - 미지정/빈값이면 "전체 학기"로 간주 → SQL에서 학기 조건 제외
 */
@RestController
@RequestMapping("/api/admin/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService service;

    /** 단일 교사 시간표 (학기/관 선택적) */
    @GetMapping("/teacher-events")
    public ResponseEntity<List<EventRes>> teacherEvents(
            @RequestParam Long teacherId,
            @RequestParam(required = false) Long semesterId,   // ★ optional
            @RequestParam(required = false) String workLocation
    ){
        return ResponseEntity.ok(service.getTeacherEvents(teacherId, semesterId, workLocation));
    }

    /** 반 기준 시간표 (관/반 필수) */
    @GetMapping("/class-events")
    public ResponseEntity<List<EventRes>> classEvents(
            @RequestParam String workLocation,
            @RequestParam Long classId
    ){
        return ResponseEntity.ok(service.getClassEvents(workLocation, classId));
    }

    /** 전체 교사 시간표 (학기/관 선택적) */
    @GetMapping("/teacher-events-all")
    public ResponseEntity<List<EventRes>> allTeacherEvents(
            @RequestParam(required = false) Long semesterId,   // ★ optional
            @RequestParam(required = false) String workLocation
    ){
        return ResponseEntity.ok(service.getAllTeacherEvents(semesterId, workLocation));
    }
}