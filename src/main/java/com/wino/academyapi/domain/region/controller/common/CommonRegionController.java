// src/main/java/com/wino/academyapi/domain/region/controller/common/CommonRegionController.java
package com.wino.academyapi.domain.region.controller.common;

import com.wino.academyapi.domain.region.dto.RegionResponse;
import com.wino.academyapi.domain.region.entity.Region;
import com.wino.academyapi.domain.region.repository.RegionRepository;
import com.wino.academyapi.domain.region.sync.DataGoKrRegionApiProvider;
import com.wino.academyapi.domain.region.sync.RegionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.text.Collator;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping(value = "/api/common/regions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CommonRegionController {

    private final RegionRepository repo;
    private final ObjectProvider<RegionSyncService> syncServiceProvider;

    // 로컬/개발 환경 여부 (스택/원인 응답 노출 제어)
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private static final Pattern NUMERIC = Pattern.compile("^\\d+$");
    private static final Pattern CODE10  = Pattern.compile("^\\d{10}$");

    // ★ Collator는 thread-unsafe → 프로토타입을 만들어 요청마다 clone 해서 사용
    private static final Collator KO_PROTO = Collator.getInstance(Locale.KOREAN);
    static { KO_PROTO.setStrength(Collator.TERTIARY); }
    private static Collator ko() { return (Collator) KO_PROTO.clone(); }

    // ✅ 1뎁스(시·도) 고정 순서 (서울→…→제주)
    private static final Map<String, Integer> SIDO_ORDER = new HashMap<>();
    static {
        putOrder("서울특별시", 1);
        putOrder("부산광역시", 2);
        putOrder("대구광역시", 3);
        putOrder("인천광역시", 4);
        putOrder("광주광역시", 5);
        putOrder("대전광역시", 6);
        putOrder("울산광역시", 7);
        putOrder("세종특별자치시", 8);
        putOrder("경기도", 9);
        putOrder("강원특별자치도", 10); putOrder("강원도", 10);
        putOrder("충청북도", 11);
        putOrder("충청남도", 12);
        putOrder("전북특별자치도", 13); putOrder("전라북도특별자치도", 13); putOrder("전라북도", 13);
        putOrder("전라남도", 14);
        putOrder("경상북도", 15);
        putOrder("경상남도", 16);
        putOrder("제주특별자치도", 17); putOrder("제주도", 17);
    }
    private static void putOrder(String name, int idx) { SIDO_ORDER.put(name, idx); }

    /** 1시간 캐시 헤더 */
    private static HttpHeaders cache1h() {
        HttpHeaders h = new HttpHeaders();
        h.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
        return h;
    }

    // ───────── null-safe 정렬 키 유틸 ─────────
    // Collator 비교에 null 들어가면 NPE → 항상 ""로 치환
    private static String nameKey(Region r) {
        String n = (r == null ? null : r.getName());
        return n == null ? "" : n.trim();
    }
    // depth도 null 가능성 대비 (prefix 정렬에서 사용)
    private static int depthKey(Region r) {
        Byte d = (r == null ? null : r.getDepth());
        return d == null ? Integer.MAX_VALUE : d.intValue();
    }

    // ======= 정렬 유틸(비교기 예외까지 방어하는 세이프가드) =======
    private static void sortDepth1(List<Region> rows, Collator KO) {
        try {
            rows.sort(
                    Comparator
                            .comparingInt((Region r) -> SIDO_ORDER.getOrDefault(r.getName(), Integer.MAX_VALUE))
                            .thenComparing(CommonRegionController::nameKey, Comparator.nullsLast(KO))
            );
        } catch (Throwable t) {
            // 혹시라도 Collator가 말썽이면 안전한 기본 비교로 폴백
            rows.sort(Comparator.comparing(CommonRegionController::nameKey, String::compareTo));
        }
    }

    private static void sortName(List<Region> rows, Collator KO) {
        try {
            rows.sort(Comparator.comparing(CommonRegionController::nameKey, Comparator.nullsLast(KO)));
        } catch (Throwable t) {
            rows.sort(Comparator.comparing(CommonRegionController::nameKey, String::compareTo));
        }
    }

    private static void sortDepthThenName(List<Region> rows, Collator KO) {
        try {
            rows.sort(
                    Comparator
                            .comparingInt(CommonRegionController::depthKey)
                            .thenComparing(CommonRegionController::nameKey, Comparator.nullsLast(KO))
            );
        } catch (Throwable t) {
            rows.sort(
                    Comparator
                            .comparingInt(CommonRegionController::depthKey)
                            .thenComparing(CommonRegionController::nameKey, String::compareTo)
            );
        }
    }

    // ======= 엔드포인트 =======

    /**
     * 공용 지역 조회
     * - depth=1~4 또는 parent(10자리)로 조회
     * - ?refresh=true 시 최신화
     *
     * ★ readOnly 트랜잭션으로 감싸서 JPA 세션 관련 예외(지연로드/세션종료 등) 방지
     * ★ 전체 try-catch로 감싸되, dev/local 프로파일에서는 원인 메시지를 함께 응답
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(@RequestParam(required = false) Integer depth,
                                  @RequestParam(required = false) String parent,
                                  @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        log.debug("[Region][list] depth={}, parent='{}', refresh={}", depth, parent, refresh);

        try {
            // ── (선택) 데이터 최신화
            if (refresh) {
                var svc = syncServiceProvider.getIfAvailable();
                if (svc != null) {
                    try {
                        int upserts = svc.syncAll();
                        log.info("[Region][refresh] syncAll upserts={}", upserts);
                    } catch (DataGoKrRegionApiProvider.RegionApiException ex) {
                        log.warn("[Region][refresh] 외부 지역 API 오류: {}", ex.getMessage());
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "외부 지역 API 오류: " + ex.getMessage(), ex);
                    }
                }
            }

            final Collator KO = ko(); // 요청 스코프 Collator
            final List<Region> rows;

            if (parent != null && !parent.isBlank()) {
                final String p = parent.trim();
                if (!CODE10.matcher(p).matches()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid parent code (10-digit)");
                }

                // DB ORDER BY 없는 메서드 → 컨트롤러에서 null-safe 정렬
                rows = repo.findByParentCodeAndUseYn(p, true);
                log.debug("[Region][list] rows(parent={}): {}", p, (rows == null ? 0 : rows.size()));

                if (rows == null || rows.isEmpty()) {
                    return ResponseEntity.ok().headers(cache1h()).body(List.of());
                }

                if (isDepth1Code(p)) {
                    // 2뎁스 중 '...구'는 "시 구" 기준 정렬
                    try {
                        rows.sort(Comparator.comparing(CommonRegionController::displayKeyForDepth2, Comparator.nullsLast(KO)));
                    } catch (Throwable t) {
                        rows.sort(Comparator.comparing(CommonRegionController::displayKeyForDepth2, String::compareTo));
                    }
                } else {
                    sortName(rows, KO);
                }

            } else {
                final int d = (depth == null ? 1 : depth);
                if (d < 1 || d > 4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid depth (1~4)");
                }

                rows = repo.findByDepthAndUseYn((byte) d, true);
                log.debug("[Region][list] rows(depth={}): {}", d, (rows == null ? 0 : rows.size()));

                if (rows == null || rows.isEmpty()) {
                    return ResponseEntity.ok().headers(cache1h()).body(List.of());
                }

                if (d == 1) sortDepth1(rows, KO);
                else        sortName(rows, KO);
            }

            // DTO 매핑 (여기서 NPE 등 터진 케이스를 추적하기 위해 보호)
            List<RegionResponse> body = new ArrayList<>(rows.size());
            for (Region r : rows) {
                try {
                    body.add(RegionResponse.from(r));
                } catch (Exception mapEx) {
                    log.error("[Region][list] mapping failed on code={}, name={}, depth={}, parent={}",
                            safe(r::getCode), safe(r::getName), safe(r::getDepth), safe(r::getParentCode), mapEx);
                    throw mapEx;
                }
            }

            return ResponseEntity.ok().headers(cache1h()).body(body);
        }
        // 400/502 류는 그대로 전달
        catch (ResponseStatusException e) {
            log.warn("[Region][list] bad request/remote error: {} ({})", e.getReason(), e.getStatusCode());
            throw e;
        }
        // 그 외 모든 예외는 500으로 매핑 (dev/local에서는 원인 메시지를 응답에 포함)
        catch (Throwable e) {
            log.error("[Region][list] INTERNAL ERROR depth={}, parent='{}'", depth, parent, e);
            if (isDevLike()) {
                Map<String, Object> debug = new LinkedHashMap<>();
                debug.put("code", "INTERNAL_ERROR");
                debug.put("message", e.getClass().getName() + ": " + (e.getMessage() == null ? "(no message)" : e.getMessage()));
                debug.put("hint", "서버 로그의 stacktrace 상단( Caused by: ... )을 확인하세요.");
                debug.put("path", "/api/common/regions");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(debug);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
        }
    }

    /** 접두(prefix)로 빠른 검색 (최대 50건) */
    @GetMapping("/prefix")
    @Transactional(readOnly = true)
    public ResponseEntity<?> prefix(@RequestParam String code) {
        final String c = (code == null ? "" : code.trim());
        if (c.isEmpty() || c.length() > 10 || !NUMERIC.matcher(c).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid code prefix (numeric, max 10)");
        }

        try {
            final Collator KO = ko();
            // DB ORDER BY 없는 메서드 → 컨트롤러에서 null-safe 정렬
            List<Region> rows = repo.findTop50ByCodeStartingWithAndUseYn(c, true);
            if (rows == null || rows.isEmpty()) {
                return ResponseEntity.ok().headers(cache1h()).body(List.of());
            }

            sortDepthThenName(rows, KO);

            return ResponseEntity.ok().headers(cache1h()).body(RegionResponse.fromAll(rows));
        } catch (Throwable e) {
            log.error("[Region][prefix] INTERNAL ERROR codePrefix='{}'", c, e);
            if (isDevLike()) {
                Map<String, Object> debug = new LinkedHashMap<>();
                debug.put("code", "INTERNAL_ERROR");
                debug.put("message", e.getClass().getName() + ": " + (e.getMessage() == null ? "(no message)" : e.getMessage()));
                debug.put("path", "/api/common/regions/prefix");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(debug);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
        }
    }

    // ======= 정렬 보조 유틸 =======
    private static boolean isDepth1Code(String code10) {
        return code10 != null
                && code10.length() == 10
                && code10.substring(2,5).equals("000")
                && code10.substring(5,8).equals("000")
                && code10.substring(8,10).equals("00");
    }

    // '...구'는 경로 끝 2토큰("수원시 권선구")을 정렬 키로 사용(null-safe)
    private static String displayKeyForDepth2(Region r) {
        String name = r == null ? "" : (r.getName() == null ? "" : r.getName().trim());
        if (name.endsWith("구")) {
            String path = (r == null ? null : r.getPathName());
            if (path != null && !path.isBlank()) {
                String[] tok = path.trim().split("\\s+");
                if (tok.length >= 2) {
                    return tok[tok.length - 2] + " " + tok[tok.length - 1];
                }
            }
        }
        return name; // 시/군은 본래 이름
    }

    // NPE 방지용 안전 호출
    private static <T> T safe(SupplierX<T> s) {
        try { return s.get(); } catch (Throwable ignore) { return null; }
    }
    @FunctionalInterface
    private interface SupplierX<T> { T get(); }

    // dev/local 프로파일인지
    private boolean isDevLike() {
        if (activeProfiles == null || activeProfiles.isBlank()) return false;
        String p = activeProfiles.toLowerCase(Locale.ROOT);
        return p.contains("local") || p.contains("dev");
    }
}
