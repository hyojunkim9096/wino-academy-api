// src/main/java/com/wino/academyapi/domain/school/controller/admin/AdminSchoolController.java
package com.wino.academyapi.domain.school.controller.admin;

import com.wino.academyapi.domain.school.dto.SchoolSummary;
import com.wino.academyapi.domain.school.dto.SchoolUpsertRequest;
import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 관리자용 학교 관리 CRUD API
 * ----------------------------------------------------------------------
 * - 목록 검색(search): 학부(stage) / 지역 접두(admPrefix|admCodePrefix|admLike)
 *   / 활성(active) / 키워드(keyword) + 페이지네이션(page,size)
 * - 단건 조회(get), 생성(create), 부분 수정(patch), 삭제(delete)
 *
 * ✅ 설계 포인트
 *  1) GET 메서드는 @Transactional(readOnly = true)로 조회 성능/일관성 확보
 *  2) page/size 파라미터 방어 (page<0 → 0, size <1 →1, size >100 →100)
 *  3) 기본 정렬: name ASC → id ASC (같은 이름 다수일 때 결과 안정성)
 *  4) stage/active는 문자열로 받아 **유연 파싱** (빈값/이상값 → null = 필터 미적용)
 *     - stage: "E/M/H" + "초등/중/고", "elementary/middle/high" 등도 허용
 *  5) 지역 접두 파라미터 혼용 지원: admPrefix / admCodePrefix / admLike("41110%")
 *     → admLike는 접미 '%'를 제거해 접두로 환산하고, **숫자 1~10자리**만 허용
 */
@RestController
@RequestMapping(value = "/api/admin/schools", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdminSchoolController {

    private final SchoolService service;

    /** admPrefix 유효성: 숫자 1~10자리 (LIKE 접두로 쓰이므로 과도한 길이/문자 방지) */
    private static final Pattern NUMERIC_1_TO_10 = Pattern.compile("^\\d{1,10}$");

    /** 최대 페이지 크기 상한 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 목록 검색 */
    @GetMapping
    @Transactional(readOnly = true)
    public Page<SchoolSummary> search(@RequestParam(required = false) String stage,
                                      @RequestParam(required = false) String admPrefix,
                                      @RequestParam(required = false) String admCodePrefix,
                                      @RequestParam(required = false) String admLike,       // 예: "41110%"
                                      @RequestParam(required = false) String active,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {

        // ---- 1) 페이지 파라미터 방어 ----
        final int p = Math.max(0, page);
        final int s = Math.min(Math.max(1, size), MAX_PAGE_SIZE); // 1~100

        // ---- 2) stage/active 유연 파싱 (빈값/이상값 → null) ----
        final SchoolStage stageEnum = parseStageFlexible(stage);
        final Boolean activeBool    = parseBooleanFlexible(active);

        // ---- 3) admPrefix 우선순위 정규화: admPrefix → admCodePrefix → admLike('%' 제거) ----
        final String prefix = firstNonBlank(
                trimToNull(admPrefix),
                trimToNull(admCodePrefix),
                likeToPrefix(admLike)        // "41110%" → "41110"
        );

        // 숫자 1~10자리만 허용
        if (prefix != null && !NUMERIC_1_TO_10.matcher(prefix).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid admPrefix (numeric, length 1~10)");
        }

        // ---- 4) 키워드 정리 ----
        final String kw = trimToNull(keyword);

        // ---- 5) 기본 정렬: name ASC → id ASC (결과 안정성) ----
        final Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        final Pageable pageable = PageRequest.of(p, s, sort);

        // ---- 6) 서비스 호출 ----
        return service.search(prefix, stageEnum, activeBool, kw, pageable);
    }

    /** 단건 조회 */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public School get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 생성 (id 반환) */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Long> create(@RequestBody @Valid SchoolUpsertRequest dto) {
        final Long id = service.create(dto);
        return ResponseEntity.ok(id);
    }

    /** 부분 수정 (제공된 필드만 반영) */
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> patch(@PathVariable Long id, @RequestBody SchoolUpsertRequest dto) {
        service.patch(id, dto);
        return ResponseEntity.noContent().build();
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ===== 파라미터 유틸 =====

    private static String trimToNull(String s) {
        if (s == null) return null;
        final String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** admLike("41110%") → "41110" (끝에 연속된 '%' 모두 제거) */
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

    /** stage 유연 파싱: "E/M/H" + 동의어/한글 지원(잘못된 값/빈값 → null) */
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
}
