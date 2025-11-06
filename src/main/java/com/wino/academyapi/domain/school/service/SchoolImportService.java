// src/main/java/com/wino/academyapi/domain/school/service/SchoolImportService.java
package com.wino.academyapi.domain.school.service;

import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.repository.SchoolRepository;
import com.wino.academyapi.external.alimi.SchoolAlimiClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 학교 데이터 임포트/동기화 서비스
 *
 * 구성:
 *  - importSchoolInfo(...)       : 시도/시군구/학부 단건 범위 동기화(알리미)
 *  - importSchoolInfoAll(...)    : 전국(학부 선택) 동기화 — ✅ 배치 커밋
 *  - importCsv(...)              : CSV 업서트(기존 유지)
 *
 * 핵심:
 *  - 대량 동기화는 SchoolBatchWriter.upsertBatch(...) 를 사용해
 *    배치 단위(REQUIRES_NEW)로 커밋하여 프론트 타임아웃/전체 롤백 리스크를 완화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolImportService {

    private static final int GEO_SCALE = 7;
    private static final int UPSERT_BATCH_SIZE = 1000; // ✅ 외부 API/메모리 상황에 맞게 조절

    private final SchoolRepository repo;
    private final SchoolBatchWriter batchWriter;        // ✅ 배치 커밋 담당
    private final SchoolAlimiClient alimi;              // ✅ 학교알리미 클라이언트

    @PersistenceContext
    private EntityManager em;

    /* =========================================================
     * 1) 알리미 — 단건 범위 동기화 (시도 + 시군구 + 학부)
     * ========================================================= */
    public ImportResult importSchoolInfo(String sidoCode, String sggCode, SchoolStage stage) {
        long t0 = System.nanoTime();

        List<SchoolAlimiClient.SchoolDto> list = alimi.fetchSchoolInfo(sidoCode, sggCode, stage);
        int fetched = list.size();
        int upserted = 0;
        int batches = 0;

        if (!CollectionUtils.isEmpty(list)) {
            for (int i = 0; i < list.size(); i += UPSERT_BATCH_SIZE) {
                List<SchoolAlimiClient.SchoolDto> slice =
                        list.subList(i, Math.min(list.size(), i + UPSERT_BATCH_SIZE));
                upserted += batchWriter.upsertBatch(slice); // ✅ 배치 단위 커밋
                batches++;
            }
        }

        long elapsedMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        return ImportResult.of(fetched, upserted, batches, elapsedMs,
                Map.of("sidoCode", nullToEmpty(sidoCode),
                        "sggCode",  nullToEmpty(sggCode),
                        "stage",    stage == null ? "" : stage.name()));
    }

    /* =========================================================
     * 2) 알리미 — 전국 동기화(학부 선택)  ✅ pseudo code 구현부
     *    → 컨트롤러에서 /sync-schoolinfo-all 로 호출
     * ========================================================= */
    public ImportResult importSchoolInfoAll(EnumSet<SchoolStage> stages) {
        long t0 = System.nanoTime();

        EnumSet<SchoolStage> st = (stages == null || stages.isEmpty())
                ? EnumSet.of(SchoolStage.E, SchoolStage.M, SchoolStage.H)
                : stages.clone();

        int fetched = 0;
        int upserted = 0;
        int batches = 0;

        // ⬇⬇⬇ 여기서 "pseudo code" 로 제시한 페이지 루프를 실제로 구현
        List<SchoolAlimiClient.SchoolDto> buf = new ArrayList<>(UPSERT_BATCH_SIZE);
        for (SchoolAlimiClient.SchoolDto dto : alimi.fetchNationwide(st)) {
            fetched++;
            buf.add(dto);

            if (buf.size() >= UPSERT_BATCH_SIZE) {
                upserted += batchWriter.upsertBatch(buf); // ✅ 배치 커밋
                batches++;
                buf.clear();
            }
        }
        // 꼬리 처리
        if (!buf.isEmpty()) {
            upserted += batchWriter.upsertBatch(buf);
            batches++;
            buf.clear();
        }
        // ⬆⬆⬆ 실제 배치 커밋 구현 끝

        long elapsedMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        return ImportResult.of(fetched, upserted, batches, elapsedMs,
                Map.of("stages", st.toString()));
    }

    /* =========================================================
     * 3) CSV 업서트(기존 유지)
     *    - readOnly 트랜잭션 아님! 행 단위 변경감지/저장을 수행
     * ========================================================= */
    @Transactional
    public int importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) return 0;

        int changed = 0;

        try (var br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return 0;

            // ── 헤더 인덱스 맵 구성(대소문자 무시) ──
            List<String> cols = parseCsvLine(header);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                idx.put(cols.get(i).trim().toLowerCase(Locale.ROOT), i);
            }

            String line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                row++;
                List<String> f = parseCsvLine(line);

                String externalCode = get(f, idx, "externalcode");
                String name         = get(f, idx, "name");
                String stageStr     = get(f, idx, "stage");
                String activeStr    = get(f, idx, "active");
                String eduOffice    = get(f, idx, "eduofficecode");
                String postal       = get(f, idx, "postalcode");
                String address      = get(f, idx, "address");
                String detail       = get(f, idx, "detailaddress");
                String admCode      = get(f, idx, "admcode");
                String latStr       = get(f, idx, "lat");
                String lngStr       = get(f, idx, "lng");
                String homepage     = get(f, idx, "homepageurl");
                String phone        = get(f, idx, "phone");

                if (externalCode == null || externalCode.isBlank()) {
                    externalCode = "CSV-" + UUID.randomUUID();
                }

                // ── 업서트 대상 조회 ──
                School s = repo.findByExternalCode(externalCode).orElse(null);
                boolean created = false;
                if (s == null) {
                    s = new School();
                    s.setExternalCode(externalCode);
                    s.setActive(true);
                    s.setStage(SchoolStage.parseFlexible(stageStr).orElse(SchoolStage.E));
                    s.setName(nvl(trimToNull(name), "학교명미상"));
                    repo.save(s);
                    created = true;
                }

                // ── 필드 매핑/정규화 후 반영 ──
                boolean dirty = false;

                // 기본 정보
                if (name != null) {
                    String v = trimToNull(name);
                    if (v != null && !v.equals(s.getName())) { s.setName(v); dirty = true; }
                }
                var stage = SchoolStage.parseFlexible(stageStr).orElse(null);
                if (stage != null && stage != s.getStage()) { s.setStage(stage); dirty = true; }
                var active = parseBooleanFlexible(activeStr);
                if (active != null && active != s.isActive()) { s.setActive(active); dirty = true; }

                // 연락/링크
                if (homepage != null) {
                    String v = trimToNull(homepage);
                    if (!Objects.equals(v, s.getHomepageUrl())) { s.setHomepageUrl(v); dirty = true; }
                }
                if (phone != null) {
                    String v = trimToNull(phone);
                    if (!Objects.equals(v, s.getPhone())) { s.setPhone(v); dirty = true; }
                }

                // 행정/주소/좌표
                if (eduOffice != null) {
                    String v = trimToNull(eduOffice);
                    if (!Objects.equals(v, s.getEduOfficeCode())) { s.setEduOfficeCode(v); dirty = true; }
                }
                if (postal != null) {
                    String v = trimToNull(postal);
                    if (!Objects.equals(v, s.getPostalCode())) { s.setPostalCode(v); dirty = true; }
                }
                if (address != null) {
                    String v = trimToNull(address);
                    if (!Objects.equals(v, s.getAddress())) { s.setAddress(v); dirty = true; }
                }
                if (detail != null) {
                    String v = trimToNull(detail);
                    if (!Objects.equals(v, s.getDetailAddress())) { s.setDetailAddress(v); dirty = true; }
                }
                if (admCode != null) {
                    String v = trimToNull(admCode);
                    if (!Objects.equals(v, s.getAdmCode())) { s.setAdmCode(v); dirty = true; }
                }
                var lat = parseDecimal(latStr);
                if (lat != null) {
                    BigDecimal v = scale(lat);
                    if (!Objects.equals(v, s.getLat())) { s.setLat(v); dirty = true; }
                }
                var lng = parseDecimal(lngStr);
                if (lng != null) {
                    BigDecimal v = scale(lng);
                    if (!Objects.equals(v, s.getLng())) { s.setLng(v); dirty = true; }
                }

                if (!created && dirty) {
                    // 변경감지로 dirty 반영
                }

                if ((row % 1000) == 0) { em.flush(); em.clear(); }
            }
        } catch (Exception e) {
            log.warn("[SchoolImport] CSV import failed: {}", e.getMessage(), e);
            throw new IllegalStateException("IMPORT_FAILED: " + e.getMessage(), e);
        }

        return changed; // (필요 시 created/updated 별도 카운트 가능)
    }

    /* ===================== 내부 유틸(공용) ===================== */

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(GEO_SCALE, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nvl(String v, String dft) {
        return (v == null || v.isBlank()) ? dft : v;
    }

    private static Boolean parseBooleanFlexible(String s) {
        if (s == null) return null;
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "true": case "1": case "y": case "yes":  return Boolean.TRUE;
            case "false": case "0": case "n": case "no":  return Boolean.FALSE;
            default: return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        try {
            return (s == null || s.isBlank()) ? null : new BigDecimal(s.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 매우 간단한 CSV 파서 */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"'); i++; // "" → "
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                out.add(sb.toString()); sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    /** 헤더 인덱스 도우미 */
    private static String get(List<String> fields, Map<String,Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= fields.size()) return null;
        String v = fields.get(i);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /* ===================== 결과 모델 ===================== */

    @Data
    @AllArgsConstructor(staticName = "of")
    public static class ImportResult {
        /** 외부에서 가져온 총 레코드 수(스캔 수) */
        private int fetched;
        /** 실제 DB에 upsert(생성+수정)가 발생한 레코드 수 */
        private int upserted;
        /** 배치 커밋 수(페이지 처리 횟수) */
        private int batches;
        /** 전체 소요(ms) */
        private long elapsedMs;
        /** 부가정보(스코프/스테이지 등) */
        private Map<String, Object> meta;
    }
}
