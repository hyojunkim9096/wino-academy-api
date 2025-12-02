// src/main/java/com/wino/academyapi/domain/enroll/controller/admin/EnrollmentSuggestController.java
package com.wino.academyapi.domain.enroll.controller.admin;

import com.wino.academyapi.domain.enroll.dto.TimeslotSuggestion;
import com.wino.academyapi.domain.enroll.service.EnrollmentSuggestService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 교차수업 후보 검색 API
 *
 * GET /api/admin/enrollments/suggest
 *   ?loc=N&stage=M&grade=M01
 *   &days=2,4           ← "1,3" CSV 또는 days=1&days=3 모두 허용
 *   &from=2025-03-01&to=2025-03-31
 *   &excludeStudentId=123
 *   &limit=50
 */
@RestController
@RequestMapping("/api/admin/enrollments/suggest")
@RequiredArgsConstructor
public class EnrollmentSuggestController {

    private final EnrollmentSuggestService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TimeslotSuggestion>> suggest(
            @RequestParam(name = "loc", required = false) String locCode,
            @RequestParam(name = "stage", required = false) String stage,
            @RequestParam(name = "grade", required = false) String gradeCode,

            // "1,3,5" 또는 반복 쿼리스트링(days=1&days=3) 모두 허용
            @RequestParam(name = "days", required = false) List<String> daysParam,

            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @RequestParam(name = "excludeStudentId", required = false) Long excludeStudentId,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ){
        List<Integer> days = parseDays(daysParam);
        List<TimeslotSuggestion> result = service.suggest(
                locCode, stage, gradeCode, days, from, to, excludeStudentId, limit
        );
        return ResponseEntity.ok(result);
    }

    /** "1,3,5" | ["1","3","5"] | ["1,3"] → [1,3,5] */
    private static List<Integer> parseDays(List<String> raw){
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> {
                    try { return Integer.parseInt(v); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .filter(n -> n >= 1 && n <= 7)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}