// src/main/java/com/wino/academyapi/domain/school/sync/SchoolSyncService.java
package com.wino.academyapi.domain.school.sync;

import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.repository.SchoolRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * 학교 동기화/임포트 서비스
 *
 * 변경 사항(지오코딩 제거):
 * - Kakao/Geocoding 전면 제거에 따라 본 서비스는 외부 Provider(학교알리미 등)에서
 *   제공하는 텍스트/좌표만 반영한다.
 * - enrichGeoAndAdm() 는 하위 호환을 위해 남겨두되 no-op(0 반환)으로 변경.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolSyncService {

    /** 위경도 스케일(DECIMAL(10,7)과 일치) */
    private static final int GEO_SCALE = 7;

    private final SchoolRepository repo;

    /**
     * ✅ 중요: 리스트 필드는 **초기화하지 말고** 스프링이 주입하도록 둔다.
     *    동일 타입의 모든 Provider 구현체들이 자동 주입된다(0개일 수도 있음).
     */
    private final List<SchoolSyncProvider> providers;

    @PersistenceContext
    private EntityManager em;

    /** Provider가 1개도 없거나 disable이면 NO_PROVIDER 에러(컨트롤러에서 핸들) */
    private SchoolSyncProvider resolveProviderOrThrow() {
        for (SchoolSyncProvider p : providers) {
            if (p != null && p.isEnabled()) return p;
        }
        throw new IllegalStateException("NO_PROVIDER: no SchoolSyncProvider registered or enabled");
    }

    /* =========================================================
       원격 동기화 (학교알리미 등 Provider → DB 업서트)
       ========================================================= */
    @Transactional
    public SyncResult syncAll(Set<String> atptsOrScopes, Set<SchoolStage> stages, boolean dryRun) {
        SchoolSyncProvider provider = resolveProviderOrThrow();

        // 누적 집계
        final ResultCounter counter = new ResultCounter();
        final LinkedHashSet<String> tried = new LinkedHashSet<>();
        final LinkedHashMap<String, String> errors = new LinkedHashMap<>();

        // Provider 콜백: 1건씩 업서트
        Consumer<SchoolSyncProvider.RemoteSchool> sink = rs -> {
            tried.addAll(rs.getScopes() == null ? List.of() : rs.getScopes()); // 레코드가 소속된 scope 힌트
            if (dryRun) { counter.skipped++; return; }

            // externalCode를 키로 업서트
            School s = repo.findByExternalCode(rs.getExternalCode()).orElse(null);
            boolean created = false;
            if (s == null) {
                s = new School();
                s.setExternalCode(Objects.requireNonNull(rs.getExternalCode(), "externalCode required"));
                // 기본값 안전장치
                s.setActive(true);
                s.setStage(rs.getStage() != null ? rs.getStage() : SchoolStage.E);
                s.setName(nvl(trimToNull(rs.getName()), "학교명미상"));
                created = true;
            }
            boolean changed = applyRemote(s, rs);
            if (created) {
                repo.save(s);
                counter.upserts++;
            } else if (changed) {
                // JPA 변경감지로 업데이트
                counter.updated++;
            } else {
                counter.skipped++;
            }
            // 메모리 보호 (대량 동기화 대비)
            if ((counter.totalProcessed() % 1000) == 0) {
                em.flush(); em.clear();
            }
        };

        try {
            SchoolSyncProvider.FetchReport fr = provider.fetch(atptsOrScopes, stages, sink);
            if (fr != null) {
                if (fr.getTriedScopes() != null) tried.addAll(fr.getTriedScopes());
                if (fr.getErrors() != null) errors.putAll(fr.getErrors());
            }
        } catch (Exception e) {
            // Provider 예외는 상위 컨트롤러에서 500으로 매핑
            log.warn("[SchoolSync] provider.fetch failed: {}", e.getMessage(), e);
            throw new IllegalStateException("SYNC_FAILED: " + e.getMessage(), e);
        }

        return new SyncResult(counter.upserts, counter.updated, counter.skipped, new ArrayList<>(tried), errors);
    }

    /* =========================================================
       CSV 임포트 (간단 파서; 큰 파일은 배치/스트리밍 권장)
       - 기대 헤더(대소문자 무시):
         externalCode,name,stage,active,eduOfficeCode,postalCode,address,detailAddress,admCode,lat,lng,homepageUrl,phone
       - externalCode 없으면 CSV-UUID 로 생성(권장: 원본 코드 제공)
       ========================================================= */
    @Transactional
    public int importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) return 0;

        int changed = 0;

        try (var br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return 0;

            List<String> cols = parseCsvLine(header);
            Map<String,Integer> idx = new HashMap<>();
            for (int i = 0; i < cols.size(); i++) idx.put(cols.get(i).trim().toLowerCase(Locale.ROOT), i);

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

                School s = repo.findByExternalCode(externalCode).orElse(null);
                boolean created = false;
                if (s == null) {
                    s = new School();
                    s.setExternalCode(externalCode);
                    s.setActive(true);
                    s.setStage(SchoolStage.parseFlexible(stageStr).orElse(SchoolStage.E));
                    s.setName(nvl(trimToNull(name), "학교명미상"));
                    created = true;
                }

                // RemoteSchool 빌더로 생성 → applyRemote에서 공통 반영
                SchoolSyncProvider.RemoteSchool rs = SchoolSyncProvider.RemoteSchool.builder()
                        .externalCode(externalCode)
                        .name(name)
                        .stage(SchoolStage.parseFlexible(stageStr).orElse(null))
                        .active(parseBooleanFlexible(activeStr))
                        .eduOfficeCode(eduOffice)
                        .postalCode(postal)
                        .address(address)
                        .detailAddress(detail)
                        .admCode(admCode)
                        .lat(parseDecimal(latStr))
                        .lng(parseDecimal(lngStr))
                        .homepageUrl(homepage)
                        .phone(phone)
                        .build();

                boolean changedEntity = applyRemote(s, rs);
                if (created) {
                    repo.save(s);
                    changed++;
                } else if (changedEntity) {
                    changed++;
                }

                if ((row % 1000) == 0) { em.flush(); em.clear(); }
            }
        } catch (Exception e) {
            log.warn("[SchoolSync][CSV] import failed: {}", e.getMessage(), e);
            throw new IllegalStateException("IMPORT_FAILED: " + e.getMessage(), e);
        }

        return changed;
    }

    /* =========================================================
       지오/법정동 보정 (누락분 일괄 처리)
       - ⛔ 지오코딩 제거: 더 이상 수행하지 않음 (하위호환을 위해 no-op 유지)
       ========================================================= */
    @Transactional(readOnly = true)
    public int enrichGeoAndAdm() {
        // 지오코딩/카카오 제거 정책: 본 메서드는 더 이상 동작하지 않으며 0을 반환한다.
        // 컨트롤러/프론트에서 호출하더라도 부작용 없이 종료된다.
        return 0;
    }

    /* =========================================================
       내부 유틸
       ========================================================= */

    private boolean applyRemote(School s, SchoolSyncProvider.RemoteSchool rs) {
        boolean dirty = false;

        // 이름/학부/활성
        if (rs.getName() != null) {
            String v = trimToNull(rs.getName());
            if (v != null && !v.equals(s.getName())) { s.setName(v); dirty = true; }
        }
        if (rs.getStage() != null && rs.getStage() != s.getStage()) { s.setStage(rs.getStage()); dirty = true; }
        if (rs.getActive() != null && rs.getActive() != s.isActive()) { s.setActive(rs.getActive()); dirty = true; }

        // 연락/링크
        if (rs.getHomepageUrl() != null) {
            String v = trimToNull(rs.getHomepageUrl());
            if (!Objects.equals(v, s.getHomepageUrl())) { s.setHomepageUrl(v); dirty = true; }
        }
        if (rs.getPhone() != null) {
            String v = trimToNull(rs.getPhone());
            if (!Objects.equals(v, s.getPhone())) { s.setPhone(v); dirty = true; }
        }

        // 행정/주소/좌표
        if (rs.getEduOfficeCode() != null) {
            String v = trimToNull(rs.getEduOfficeCode());
            if (!Objects.equals(v, s.getEduOfficeCode())) { s.setEduOfficeCode(v); dirty = true; }
        }
        if (rs.getPostalCode() != null) {
            String v = trimToNull(rs.getPostalCode());
            if (!Objects.equals(v, s.getPostalCode())) { s.setPostalCode(v); dirty = true; }
        }
        if (rs.getAddress() != null) {
            String v = trimToNull(rs.getAddress());
            if (!Objects.equals(v, s.getAddress())) { s.setAddress(v); dirty = true; }
        }
        if (rs.getDetailAddress() != null) { // ✅ 지번(옛 주소) 규칙
            String v = trimToNull(rs.getDetailAddress());
            if (!Objects.equals(v, s.getDetailAddress())) { s.setDetailAddress(v); dirty = true; }
        }
        if (rs.getAdmCode() != null) {
            String v = trimToNull(rs.getAdmCode());
            if (!Objects.equals(v, s.getAdmCode())) { s.setAdmCode(v); dirty = true; }
        }
        if (rs.getLat() != null) {
            BigDecimal v = scale(rs.getLat());
            if (!Objects.equals(v, s.getLat())) { s.setLat(v); dirty = true; }
        }
        if (rs.getLng() != null) {
            BigDecimal v = scale(rs.getLng());
            if (!Objects.equals(v, s.getLng())) { s.setLng(v); dirty = true; }
        }
        return dirty;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(GEO_SCALE, RoundingMode.HALF_UP);
    }
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
    private static String nvl(String v, String dft) { return (v == null || v.isBlank()) ? dft : v; }

    private static Boolean parseBooleanFlexible(String s) {
        if (s == null) return null;
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "true": case "1": case "y": case "yes":  return Boolean.TRUE;
            case "false": case "0": case "n": case "no":  return Boolean.FALSE;
            default: return null;
        }
    }
    private static BigDecimal parseDecimal(String s) {
        try { return (s == null || s.isBlank()) ? null : new BigDecimal(s.trim()); }
        catch (Exception ignore) { return null; }
    }

    private static List<String> parseCsvLine(String line) {
        // 매우 간단한 CSV 파서 (따옴표 포함 값/내부 콤마 처리)
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"'); i++; // 이스케이프된 따옴표
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

    private static String get(List<String> f, Map<String,Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= f.size()) return null;
        String v = f.get(i);
        return (v == null || v.isEmpty()) ? null : v;
    }

    /* ===== 결과 DTO ===== */
    @Getter
    @AllArgsConstructor
    public static class SyncResult {
        public final int upserts;
        public final int updated;
        public final int skipped;
        public final List<String> triedAtpts;             // 컨트롤러 호환: 필드명 유지
        public final Map<String,String> errors;
    }

    private static class ResultCounter {
        int upserts = 0, updated = 0, skipped = 0;
        int totalProcessed() { return upserts + updated + skipped; }
    }
}
