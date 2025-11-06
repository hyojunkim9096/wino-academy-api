// src/main/java/com/wino/academyapi/domain/region/controller/AdminRegionController.java
package com.wino.academyapi.domain.region.controller;

import com.wino.academyapi.domain.region.sync.DataGoKrRegionApiProvider.RegionApiException;
import com.wino.academyapi.domain.region.sync.RegionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.*;

/**
 * 지역 마스터 동기화 (관리자)
 *
 * 엔드포인트
 *  - POST /api/admin/regions/sync                : 전체 동기화(교체/업서트)
 *  - POST /api/admin/regions/sync-missing        : 누락/변경 상위 N건(점진)
 *  - POST /api/admin/regions/sync-missing-all    : 누락/변경 전량(배치 반복)
 *
 * 예외 매핑
 *  - IllegalArgumentException → 400
 *  - RegionApiException        → 502
 *  - HttpStatusCodeException   → 외부 상태코드 그대로
 *  - 기타 Exception            → 500
 *
 * 보안
 *  - SecurityConfig에서 ROLE_ADMIN/ROLE_SYSTEM_ADMIN 등으로 보호 필요
 */
@RestController
@RequestMapping(value = "/api/admin/regions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class AdminRegionController {

    private final RegionSyncService syncService;

    /* =========================================================
       1) 전체 동기화 (기존)
       ========================================================= */
    /**
     * 원격 소스에서 지역 마스터를 동기화합니다.
     *
     * @param mode      replace | upsert (기본 upsert)
     * @param sourceUrl (선택) 소스 URL 오버라이드
     */
    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestParam(defaultValue = "upsert") String mode,
                                  @RequestParam(required = false) String sourceUrl) {
        try {
            RegionSyncService.Mode m = "replace".equalsIgnoreCase(mode)
                    ? RegionSyncService.Mode.REPLACE
                    : RegionSyncService.Mode.UPSERT;

            var r = syncService.sync(m, sourceUrl);
            return ResponseEntity.ok(Map.of(
                    "mode", m.name().toLowerCase(),
                    "total", r.total(),
                    "inserted", r.inserted(),
                    "updated", r.updated(),
                    "disabled", r.disabled()
            ));
        } catch (IllegalArgumentException iae) {
            log.warn("[RegionSync] 400 Bad Request: {}", iae.getMessage());
            return badRequest(iae.getMessage());
        } catch (RegionApiException rae) {
            log.warn("[RegionSync] 502 Bad Gateway: {}", rae.getMessage());
            return badGateway(rae.getMessage(), rae.code);
        } catch (HttpStatusCodeException he) {
            log.warn("[RegionSync] Upstream error {}: {}", he.getRawStatusCode(), safeMsg(he.getResponseBodyAsString()));
            return ResponseEntity.status(he.getStatusCode()).body(Map.of(
                    "error", "upstream_error",
                    "status", he.getRawStatusCode(),
                    "message", safeMsg(he.getResponseBodyAsString())
            ));
        } catch (Exception e) {
            log.error("[RegionSync] 500 Internal Error", e);
            return internalError(e.getMessage());
        }
    }

    /* =========================================================
       2) 누락 상위 N건 — 점진 개선용
       ========================================================= */
    /**
     * 누락/변경된 지역 레코드 "상위 N건"만 동기화합니다.
     * - 추천: limit=100
     * - 내부적으로 RegionSyncService.syncMissingTopN(limit) 호출
     */
    @PostMapping("/sync-missing")
    public ResponseEntity<?> syncMissingTopN(@RequestParam(defaultValue = "100") int limit) {
        try {
            int processed = syncService.syncMissingTopN(clamp(limit, 1, 500));
            return ResponseEntity.ok(Map.of("processed", processed));
        } catch (IllegalArgumentException iae) {
            log.warn("[RegionSyncMissing] 400 Bad Request: {}", iae.getMessage());
            return badRequest(iae.getMessage());
        } catch (RegionApiException rae) {
            log.warn("[RegionSyncMissing] 502 Bad Gateway: {}", rae.getMessage());
            return badGateway(rae.getMessage(), rae.code);
        } catch (HttpStatusCodeException he) {
            log.warn("[RegionSyncMissing] Upstream error {}: {}", he.getRawStatusCode(), safeMsg(he.getResponseBodyAsString()));
            return ResponseEntity.status(he.getStatusCode()).body(Map.of(
                    "error", "upstream_error",
                    "status", he.getRawStatusCode(),
                    "message", safeMsg(he.getResponseBodyAsString())
            ));
        } catch (Exception e) {
            log.error("[RegionSyncMissing] 500 Internal Error", e);
            return internalError(e.getMessage());
        }
    }

    /* =========================================================
       3) 누락 전량 — chunk/prefix 반복 배치
       ========================================================= */
    /**
     * 누락/변경을 "전량" 동기화합니다(배치 반복).
     *
     * @param chunkSize       배치 크기(1~500, 기본 100)
     * @param prefixes        쉼표로 구분된 법정동 코드 prefix (예: "11,26,27").
     *                        비우면 서비스에서 시/도(2자리) 기본 분할 사용(표시용).
     * @param hardStopSeconds 안전 중단(초). 기본 900(=15분), 최솟값 60
     *
     * @return 리포트 JSON:
     *   {
     *     "processedTotal": 7723,
     *     "batches": 80,
     *     "perBatch": [100, 100, ..., 23],
     *     "elapsedMs": 123456,
     *     "prefixesUsed": ["11","26",...]
     *   }
     */
    @PostMapping("/sync-missing-all")
    public ResponseEntity<?> syncMissingAll(@RequestParam(defaultValue = "100") int chunkSize,
                                            @RequestParam(required = false) String prefixes,
                                            @RequestParam(name = "hardStopSeconds", defaultValue = "900") int hardStopSeconds) {
        try {
            int safeChunk = clamp(chunkSize, 1, 500);
            int safeHardStop = Math.max(60, hardStopSeconds);
            List<String> prefixList = parsePrefixes(prefixes);

            RegionSyncService.SyncAllReport r =
                    syncService.syncMissingAll(safeChunk, prefixList, safeHardStop);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("processedTotal", r.getProcessedTotal());
            body.put("batches", r.getBatches());
            body.put("perBatch", r.getPerBatchCounts());
            body.put("elapsedMs", r.getElapsedMs());
            body.put("prefixesUsed", r.getPrefixesUsed());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException iae) {
            log.warn("[RegionSyncAll] 400 Bad Request: {}", iae.getMessage());
            return badRequest(iae.getMessage());
        } catch (RegionApiException rae) {
            log.warn("[RegionSyncAll] 502 Bad Gateway: {}", rae.getMessage());
            return badGateway(rae.getMessage(), rae.code);
        } catch (HttpStatusCodeException he) {
            log.warn("[RegionSyncAll] Upstream error {}: {}", he.getRawStatusCode(), safeMsg(he.getResponseBodyAsString()));
            return ResponseEntity.status(he.getStatusCode()).body(Map.of(
                    "error", "upstream_error",
                    "status", he.getRawStatusCode(),
                    "message", safeMsg(he.getResponseBodyAsString())
            ));
        } catch (Exception e) {
            log.error("[RegionSyncAll] 500 Internal Error", e);
            return internalError(e.getMessage());
        }
    }

    /* =========================================================
       내부 유틸
       ========================================================= */

    /** 범위 클램프 */
    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * prefixes CSV를 List<String>으로 파싱
     *  - 비숫자 제거, 공백 트림
     *  - 중복 제거 + 입력 순서 유지(LinkedHashSet)
     */
    private List<String> parsePrefixes(String csv) {
        if (csv == null || csv.isBlank()) return List.of(); // 서비스에서 자동 분할
        String[] arr = csv.split("[,\\s]+");
        Set<String> set = new LinkedHashSet<>();
        for (String s : arr) {
            String t = (s == null ? "" : s.trim()).replaceAll("[^0-9]", "");
            if (!t.isEmpty()) set.add(t);
        }
        return new ArrayList<>(set);
    }

    /** 너무 긴 오류 메시지는 컷 (로그는 전체 남고, 응답은 2KB 제한) */
    private String safeMsg(String s) {
        if (s == null) return "";
        return (s.length() > 2000) ? s.substring(0, 2000) + "..." : s;
    }

    /** 400 공통 본문 생성 */
    private ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", safeMsg(msg)
        ));
    }

    /** 500 공통 본문 생성 */
    private ResponseEntity<Map<String, Object>> internalError(String msg) {
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "internal_error",
                "message", safeMsg(msg)
        ));
    }

    /** 502 공통 본문 생성 (외부 API 실패) */
    private ResponseEntity<Map<String, Object>> badGateway(String msg, String upstreamCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "bad_gateway");
        if (upstreamCode != null && !upstreamCode.isBlank()) body.put("upstreamCode", upstreamCode);
        body.put("message", safeMsg(msg));
        return ResponseEntity.status(502).body(body);
    }
}
