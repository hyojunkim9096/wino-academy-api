package com.wino.academyapi.external.alimi;

import com.wino.academyapi.domain.school.entity.SchoolStage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 학교알리미(SchoolInfo) OpenAPI 클라이언트
 *
 * - 항상 절대 URL(props.url)로 호출합니다. (rootUri 의존 금지)
 * - 재시도(429/5xx) + 스로틀(프로퍼티) 적용
 * - RestTemplate 후보가 여러 개라 @Qualifier 로 정확히 지정합니다.
 */
@Slf4j
@Component
public class SchoolAlimiClient {

    private final RestTemplate restTemplate;   // ✅ schoolInfoRestTemplate 만 주입
    private final SchoolInfoProperties props;

    /**
     * ✅ 명시 생성자 + @Qualifier 로 모호성 제거
     * (롬복 @RequiredArgsConstructor 사용 시 파라미터에 @Qualifier 가 안 붙어서 충돌남)
     */
    @Autowired
    public SchoolAlimiClient(
            @Qualifier("schoolInfoRestTemplate") RestTemplate restTemplate,
            SchoolInfoProperties props
    ) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    private static final int  DEFAULT_PAGE_SIZE = 1000; // 필요 시 실제 스펙대로 조정
    private static final int  MAX_RETRIES = 2;
    private static final long BACKOFF_BASE = 500L;
    private static final long JITTER_MS   = 150L;

    public boolean isEnabled() { return props != null && props.isEnabled(); }

    /* ──────────────────────────────────────────────────────────────
       단건 범위: 시도/시군구/학부 → 페이지 병합(스펙에 따라 단일 호출일 수도)
       ────────────────────────────────────────────────────────────── */
    public List<SchoolDto> fetchSchoolInfo(String sidoCode, String sggCode, SchoolStage stage) {
        if (!isEnabled()) return List.of();
        Objects.requireNonNull(stage, "stage must not be null");

        List<SchoolDto> out = new ArrayList<>();
        int page = 1;
        while (true) {
            Page p = listInternal(sidoCode, sggCode, EnumSet.of(stage), page, DEFAULT_PAGE_SIZE);
            if (!CollectionUtils.isEmpty(p.records)) out.addAll(p.records);
            if (!p.hasNext) break;
            page++;
        }
        return out;
    }

    /* ──────────────────────────────────────────────────────────────
       전국: 선택 스테이지(E/M/H) 이터레이터
       ────────────────────────────────────────────────────────────── */
    public Iterable<SchoolDto> fetchNationwide(EnumSet<SchoolStage> stages) {
        if (!isEnabled()) return Collections::emptyIterator;

        final EnumSet<SchoolStage> st = (stages == null || stages.isEmpty())
                ? EnumSet.of(SchoolStage.E, SchoolStage.M, SchoolStage.H)
                : stages.clone();

        return () -> new Iterator<>() {
            private int page = 1;
            private Iterator<SchoolDto> buf = Collections.emptyIterator();
            private boolean end = false;

            @Override public boolean hasNext() {
                if (buf.hasNext()) return true;
                if (end) return false;

                Page p = listInternal(null, null, st, page, DEFAULT_PAGE_SIZE);
                page++;
                if (CollectionUtils.isEmpty(p.records)) { end = true; return false; }
                buf = p.records.iterator();
                if (!p.hasNext && !buf.hasNext()) { end = true; return false; }
                return buf.hasNext();
            }
            @Override public SchoolDto next() {
                if (!hasNext()) throw new NoSuchElementException();
                return buf.next();
            }
        };
    }

    /* ──────────────────────────────────────────────────────────────
       내부: 절대 URL로 요청/파싱
       ────────────────────────────────────────────────────────────── */
    private Page listInternal(String sidoCode,
                              String sggCode,
                              Set<SchoolStage> stages,
                              int page, int size) {

        throttle();

        // 학교급코드(여러 개 지원 X 가정 → 첫 값 사용. 스펙 다르면 변경)
        List<String> knds = mapStagesToKndCodes(stages);
        String kndForApi = knds.isEmpty() ? "02" : knds.get(0);

        // ✅ 절대 URL 사용 (예: https://www.schoolinfo.go.kr/openApi.do)
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(props.getUrl())
                .queryParam("apiKey", safe(props.getApiKey()))
                .queryParam("apiType", "0")
                .queryParam("schulKndCode", kndForApi);

        if (StringUtils.hasText(sidoCode)) b.queryParam("sidoCode", sidoCode);
        if (StringUtils.hasText(sggCode))  b.queryParam("sggCode",  sggCode);

        // ※ 실제로 페이지 파라미터가 있으면 아래 주석을 맞춰 활성화
        // b.queryParam("pageIndex", Math.max(1, page));
        // b.queryParam("pageSize",  Math.max(1, size));

        URI uri = b.build().encode(StandardCharsets.UTF_8).toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> req = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> res = exchangeWithRetry(uri, req, Map.class);
            Map<?,?> body = res.getBody();
            if (body == null) return new Page(List.of(), false);

            List<Map<String,Object>> rows = extractRows(body);
            boolean hasNext = inferHasNext(body, rows, size, page);

            List<SchoolDto> list = new ArrayList<>();
            for (Map<String,Object> raw : rows) {
                SchoolDto dto = mapFromRaw(raw);
                if (dto != null) list.add(dto);
            }
            return new Page(list, hasNext);

        } catch (RestClientResponseException e) {
            throw mapError(uri, e);
        } catch (RestClientException e) {
            // ⛑ 여기까지 왔는데 "Target host is not specified" 가 또 뜨면
            // props.url 이 절대경로( http/https 로 시작 )인지 다시 점검하세요.
            throw new IllegalStateException("SchoolInfo IO error: " + e.getMessage(), e);
        }
    }

    /* ───────────────── HTTP(재시도/스로틀) ───────────────── */
    private void throttle() {
        try {
            int ms = Math.max(0, props.getThrottleMs());
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    private <T> ResponseEntity<T> exchangeWithRetry(URI uri, HttpEntity<?> req, Class<T> type) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ResponseEntity<T> res = restTemplate.exchange(uri, HttpMethod.GET, req, type);
                if (!res.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("SchoolInfo non-2xx: " + res.getStatusCode());
                }
                return res;

            } catch (RestClientResponseException e) {
                int code = e.getRawStatusCode();
                if ((code == 429 || (code >= 500 && code < 600)) && attempt <= (1 + MAX_RETRIES)) {
                    long wait = Math.max(200, (long)(BACKOFF_BASE * Math.pow(2, attempt - 1))
                            + ThreadLocalRandom.current().nextLong(-JITTER_MS, JITTER_MS));
                    log.warn("[SCHOOLINFO][RETRY] status={} attempt={} wait={}ms uri={}", code, attempt, wait, uri);
                    sleep(wait);
                    continue;
                }
                throw mapError(uri, e);

            } catch (RestClientException e) {
                if (attempt <= (1 + MAX_RETRIES)) {
                    long wait = Math.max(200, (long)(BACKOFF_BASE * Math.pow(2, attempt - 1))
                            + ThreadLocalRandom.current().nextLong(0, JITTER_MS));
                    log.warn("[SCHOOLINFO][RETRY-IO] attempt={} wait={}ms uri={} msg={}", attempt, wait, uri, e.getMessage());
                    sleep(wait);
                    continue;
                }
                throw new IllegalStateException("SchoolInfo IO error: " + e.getMessage(), e);
            }
        }
    }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private IllegalStateException mapError(URI uri, RestClientResponseException e) {
        int code = e.getRawStatusCode();
        String msg = e.getStatusText();
        String body = e.getResponseBodyAsString();
        if (body != null && body.length() > 200) body = body.substring(0, 200) + "...";
        log.warn("[SCHOOLINFO][HTTP] GET {} failed: {} body={}", uri, code, safe(body));
        return new IllegalStateException("SchoolInfo error " + code + " at " + uri + ": " + msg, e);
    }

    /* ───────────────── 파싱/매핑 유틸 ───────────────── */
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> extractRows(Map<?,?> body) {
        Object[] candidates = {
                body.get("data"), body.get("DATA"),
                body.get("content"), body.get("CONTENT"),
                body.get("records"), body.get("RECORDS"),
                body.get("items"), body.get("ITEMS"),
                body.get("result"), body.get("RESULT"),
                body.get("list"), body.get("LIST")
        };
        for (Object c : candidates) {
            if (c instanceof List<?> list) {
                List<Map<String,Object>> out = new ArrayList<>();
                for (Object el : list) if (el instanceof Map<?,?> m) out.add((Map<String,Object>) m);
                if (!out.isEmpty()) return out;
            }
        }
        if (body instanceof List<?> list) {
            List<Map<String,Object>> out = new ArrayList<>();
            for (Object el : list) if (el instanceof Map<?,?> m) out.add((Map<String,Object>) m);
            return out;
        }
        return List.of();
    }

    private static boolean inferHasNext(Map<?,?> body, List<?> rows, int reqSize, int page) {
        Boolean[] flags = {
                asBool(body.get("hasNext")), asBool(body.get("HAS_NEXT")),
                asBool(body.get("has_next")), asBool(body.get("HAS_NEXT")),
                inverseBool(body.get("is_end")), inverseBool(body.get("IS_END")),
                inverseBool(body.get("last")),    inverseBool(body.get("LAST"))
        };
        for (Boolean f : flags) if (f != null) return f;

        Integer cur = asInt(opt(body, "page","PAGE","pageNo","PAGE_NO","currentPage","CURRENT_PAGE"));
        Integer tot = asInt(opt(body, "totalPages","TOTAL_PAGES","total_pages","PAGE_COUNT"));
        if (cur != null && tot != null) return cur < tot;

        if (rows != null && reqSize > 0) return rows.size() >= reqSize;
        return false;
    }

    private SchoolDto mapFromRaw(Map<String,Object> m) {
        if (m == null || m.isEmpty()) return null;

        String externalCode = strAny(m, "SCHUL_CODE", "schul_code", "SCHULCODE");
        String name         = strAny(m, "SCHUL_NM", "schul_nm", "SCHULNM");

        String stageCode    = strAny(m, "SCHUL_KND_SC_CODE", "SCHUL_CRSE_SC_VALUE", "schul_knd_sc_code");
        SchoolStage stage   = parseStageFromKnd(stageCode).orElse(null);

        String eduOffice    = strAny(m, "ATPT_OFCDC_ORG_CODE", "atpt_ofcdc_org_code");
        String postal       = nvl(strAny(m, "SCHUL_RDNZC", "ZIP_CODE", "zip_code"), null);

        String roadMain     = strAny(m, "SCHUL_RDNMA", "schul_rdnma");
        String roadDetail   = strAny(m, "SCHUL_RDNDA", "schul_rdnda");
        String roadAddress  = joinSp(roadMain, roadDetail);

        String jibunMain    = strAny(m, "ADRES_BRKDN", "adres_brkdn");
        String jibunDetail  = strAny(m, "DTLAD_BRKDN", "dtlad_brkdn");
        String jibunAddress = joinSp(jibunMain, jibunDetail);

        String admCode      = strAny(m, "ADRCD_ID", "adrcd_id");

        BigDecimal lat      = decAny(m, "LTTUD", "lttud", "LAT", "lat", "latitude");
        BigDecimal lng      = decAny(m, "LGTUD", "lgtud", "LON", "lng", "longitude");

        String homepage     = strAny(m, "HMPG_ADRES", "hmpg_adres", "homepage", "HOMEPAGE");
        String phone        = strAny(m, "USER_TELNO", "user_telno", "TELNO", "tel");

        return new SchoolDto(
                safe(externalCode),
                trimToNull(name),
                stage,
                null, // active 값은 응답에 없으므로 null 유지
                trimToNull(eduOffice),
                trimToNull(postal),
                trimToNull(roadAddress),
                trimToNull(jibunAddress),
                trimToNull(admCode),
                lat, lng,
                trimToNull(homepage),
                trimToNull(phone)
        );
    }

    /* ───────────────── 공용 유틸 ───────────────── */
    private static String safe(String s) { return s == null ? "" : s; }
    private static String nvl(String v, String d) { return (v == null || v.isBlank()) ? d : v; }

    private static Object opt(Map<?,?> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }
    private static Integer asInt(Object o) {
        try { return o==null?null:Integer.parseInt(String.valueOf(o)); }
        catch (Exception ignore) { return null; }
    }
    private static Boolean asBool(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "1","true","y","yes" -> true;
            case "0","false","n","no" -> false;
            default -> null;
        };
    }
    private static Boolean inverseBool(Object o) { Boolean b = asBool(o); return b == null ? null : !b; }

    @SuppressWarnings("unchecked")
    private static String strAny(Map<String,Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
    private static BigDecimal decAny(Map<String,Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null) continue;
            try { return new BigDecimal(String.valueOf(v)); } catch (Exception ignore) {}
        }
        return null;
    }

    /** 02/03/04 → E/M/H */
    private static Optional<SchoolStage> parseStageFromKnd(String v) {
        if (!StringUtils.hasText(v)) return Optional.empty();
        return switch (v.trim()) {
            case "02" -> Optional.of(SchoolStage.E);
            case "03" -> Optional.of(SchoolStage.M);
            case "04" -> Optional.of(SchoolStage.H);
            default -> Optional.empty(); // 05/06/07 등은 현재 시스템 대상 아님
        };
    }
    private static List<String> mapStagesToKndCodes(Set<SchoolStage> stages) {
        if (stages == null || stages.isEmpty()) return List.of("02","03","04");
        List<String> out = new ArrayList<>();
        for (SchoolStage s : stages) {
            switch (s) {
                case E -> out.add("02");
                case M -> out.add("03");
                case H -> out.add("04");
            }
        }
        return out;
    }
    private static String joinSp(String a, String b) {
        a = trimToNull(a); b = trimToNull(b);
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a + " " + b;
    }
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /* ===== 내부 모델 ===== */
    @Data @AllArgsConstructor
    private static class Page {
        private List<SchoolDto> records;
        private boolean hasNext;
    }

    @Getter
    @AllArgsConstructor
    public static class SchoolDto {
        private final String externalCode;
        private final String name;
        private final SchoolStage stage;    // E/M/H
        private final Boolean active;

        private final String eduOfficeCode;
        private final String postalCode;

        private final String roadAddress;   // 도로명(+상세)
        private final String jibunAddress;  // 지번(+상세)
        private final String admCode;       // 법정동코드

        private final BigDecimal lat;       // 위도
        private final BigDecimal lng;       // 경도

        private final String homepageUrl;
        private final String phone;
    }
}
