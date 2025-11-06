// src/main/java/com/wino/academyapi/domain/school/controller/CommonSchoolController.java
package com.wino.academyapi.domain.school.controller;

import com.wino.academyapi.domain.school.dto.SchoolSummary;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 공통(공개) School 조회 API
 * ----------------------------------------------------------------------
 * - GET /api/common/schools
 *   파라미터:
 *     admPrefix     : 법정동코드 접두 (숫자 1~10자리)
 *     admCodePrefix : 동일 의미(대체 파라미터)
 *     admLike       : "41110%" → 접미 % 제거해 접두로 환산
 *     stage         : "E" | "M" | "H" |(유연) "elementary/초등/..." 등
 *     active        : "true/false/1/0/y/n" 등 (유연 파싱)
 *     keyword       : 학교명/주소 키워드
 *     page,size     : 페이지네이션(기본 0,20; size 1~100 제한)
 *
 * - 보안: SecurityConfig에서 GET /api/common/schools/** permitAll
 * - 정렬: DB 정렬(name ASC, id ASC) + 페이지 내 한글 Collator 2차 보정(가나다)
 */
@RestController
@RequestMapping(value = "/api/common/schools", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CommonSchoolController {

    private final SchoolService schoolService;

    /** 숫자(1~10자리) 검증용 */
    private static final Pattern NUMERIC_1_TO_10 = Pattern.compile("^\\d{1,10}$");

    /** 한국어 정렬 Collator (가나다 정렬용) */
    private static final Collator KO = Collator.getInstance(Locale.KOREAN);
    static { KO.setStrength(Collator.TERTIARY); }

    /** 페이지 크기 상한 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 목록 검색(공개) */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<SchoolSummary>> search(
            @RequestParam(required = false) String admPrefix,
            @RequestParam(required = false) String admCodePrefix,
            @RequestParam(required = false) String admLike,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // ---- 1) 지역 접두 파라미터 정규화 ----
        final String prefixRaw = firstNonBlank(
                trimToNull(admPrefix),
                trimToNull(admCodePrefix),
                likeToPrefix(admLike) // "41110%" → "41110"
        );

        if (prefixRaw != null && !NUMERIC_1_TO_10.matcher(prefixRaw).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid admPrefix (numeric, length 1~10)");
        }

        // ---- 2) stage/active 유연 파싱 ----
        final SchoolStage stageEnum = parseStageFlexible(stage);
        final Boolean activeBool    = parseBooleanFlexible(active);

        // ---- 3) 페이지네이션 + 기본 정렬 ----
        final int p = Math.max(0, page);
        final int s = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        final Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        final Pageable pageable = PageRequest.of(p, s, sort);

        // ---- 4) 서비스 검색 ----
        final Page<SchoolSummary> data = schoolService.search(
                prefixRaw,
                stageEnum,
                activeBool,
                trimToNull(keyword),
                pageable
        );

        // ---- 5) 페이지 content만 KO Collator(가나다)로 보정 + 동률 시 id ASC ----
        var comparator = Comparator
                .comparing(SchoolSummary::name, (a, b) -> KO.compare(nz(a), nz(b)))
                .thenComparing(SchoolSummary::id, Comparator.nullsLast(Long::compareTo));

        var sortedContent = data.getContent().stream()
                .sorted(comparator)
                .toList();

        Page<SchoolSummary> body = new PageImpl<>(sortedContent, data.getPageable(), data.getTotalElements());
        return ResponseEntity.ok(body);
    }

    // ===== 유틸리티 =====

    private static String trimToNull(String s) {
        if (s == null) return null;
        final String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** admLike("41110%") → "41110" (끝의 % 제거) */
    private static String likeToPrefix(String like) {
        final String t = trimToNull(like);
        if (t == null) return null;
        return t.replaceAll("%+$", "");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            final String t = trimToNull(v);
            if (t != null) return t;
        }
        return null;
    }

    /** stage 유연 파싱 */
    private static SchoolStage parseStageFlexible(String s) {
        final String t = trimToNull(s);
        if (t == null) return null;
        return SchoolStage.parseFlexible(t).orElse(null);
    }

    /** active 유연 파싱 */
    private static Boolean parseBooleanFlexible(String s) {
        final String t = trimToNull(s);
        if (t == null) return null;
        switch (t.toLowerCase(Locale.ROOT)) {
            case "true": case "1": case "y": case "yes":  return Boolean.TRUE;
            case "false": case "0": case "n": case "no":  return Boolean.FALSE;
            default: return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
