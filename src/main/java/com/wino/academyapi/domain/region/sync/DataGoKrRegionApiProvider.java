// src/main/java/com/wino/academyapi/domain/region/sync/DataGoKrRegionApiProvider.java
package com.wino.academyapi.domain.region.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털(데이터.go.kr) 법정동 API 프로바이더
 *
 * ✅ 이번 수정 핵심
 *  1) HTTP 시도 URL 스킴 오타 수정 → https 실패 시 확실히 http로 시도( forceHttp 추가 )
 *  2) Connection reset 대응 소형 재시도(MAX_RETRY=3, 선형 backoff)
 *  3) 반환 직전 Row 정규화(RegionSourceProvider.normalizeAll)
 *  4) 일부 게이트웨이 대응: Accept에 text/plain 허용
 *  5) 프로빙(detectWorkingAttempt)과 본 페이징(fetch 루프) 모두 재시도 유틸 사용
 *
 * 동작 개요
 *  - 엔드포인트 보정: /StanReginCd → /StanReginCd/getStanReginCdList
 *  - 파라미터 자동 탐지: serviceKey/ServiceKey × type/returnType/resultType × raw/encoded 조합 프로빙
 *  - 페이징: pageNo/numOfRows로 1000건씩, totalCount 또는 마지막 페이지에서 종료
 *  - JSON 파싱: StanReginCd → head/row 구조 파싱
 */
@Slf4j
@Component
public class DataGoKrRegionApiProvider implements RegionSourceProvider {

    private final RegionSyncProperties props;
    private final RestTemplate http;
    private final ObjectMapper om = new ObjectMapper();

    public DataGoKrRegionApiProvider(
            RegionSyncProperties props,
            @Qualifier("regionRestTemplate") RestTemplate http
    ) {
        this.props = props;
        this.http = http;
    }

    // ───────────────────── 예외 타입 ─────────────────────

    /** 외부 API 예외를 내부 코드로 전달하기 위한 런타임 예외(컨트롤러에서 502로 매핑 권장). */
    public static class RegionApiException extends RuntimeException {
        public final String code;
        public RegionApiException(String code, String message) { super(message); this.code = code; }
        public RegionApiException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    }

    // ───────────────────── 재시도 유틸 ─────────────────────

    private static final int MAX_RETRY = 3;
    private static final long RETRY_SLEEP_MS = 500L;

    /** 공통 GET + 재시도(멱등 GET만 대상). */
    private ResponseEntity<String> httpGetWithRetry(String url) {
        RestClientException last = null;
        for (int i = 1; i <= MAX_RETRY; i++) {
            try {
                return http.exchange(
                        URI.create(url),
                        HttpMethod.GET,
                        new HttpEntity<>(defaultHeaders()),
                        String.class
                );
            } catch (RestClientException e) {
                last = e;
                log.warn("[RegionAPI] GET retry {}/{} url={} cause={}",
                        i, MAX_RETRY, maskKey(url), e.getMessage());
                try { Thread.sleep(RETRY_SLEEP_MS * i); } catch (InterruptedException ignored) {}
            }
        }
        throw new RegionApiException("HTTP_ERROR",
                "Region API request failed after retries: " + (last != null ? last.getMessage() : "unknown"), last);
    }

    // ───────────────────── 메인 진입 ─────────────────────

    @Override
    public List<Row> fetch(String sourceUrlOverride) throws Exception {
        String baseUrlRaw = StringUtils.hasText(sourceUrlOverride) ? sourceUrlOverride : props.getApiUrl();
        if (!StringUtils.hasText(baseUrlRaw))
            throw new IllegalStateException("Region API URL is empty. Set app.region.sync.api-url (REGION_API_URL)");
        if (!StringUtils.hasText(props.getApiKey()))
            throw new IllegalStateException("Region API KEY is empty. Set app.region.sync.api-key (REGION_API_KEY)");

        // (1) 엔드포인트 보정: /StanReginCd → /StanReginCd/getStanReginCdList
        String baseFixed = ensureMethod(baseUrlRaw);

        // (2) 키 원본/인코딩 준비
        String keyRaw = props.getApiKey();
        String keyEnc = keyNeedsEncoding(keyRaw) ? urlEncode(keyRaw) : keyRaw;

        // (3) 동작하는 파라미터 조합 자동 탐지 (재시도 포함)
        Attempt working = detectWorkingAttempt(baseFixed, keyRaw, keyEnc);
        log.info("[RegionAPI] selected attempt: {}", working.masked());

        // (4) 실제 페이징 호출
        int pageNo = 1;
        int numOfRows = 1000; // 대용량 수집 권장 값
        List<Row> out = new ArrayList<>();
        Integer totalCount = null;

        while (true) {
            String url = buildUrl(
                    working.baseUrl,
                    working.keyParam,
                    working.typeParam,
                    working.useEncodedKey ? keyEnc : keyRaw,
                    pageNo,
                    numOfRows
            );

            ResponseEntity<String> res = httpGetWithRetry(url);
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new RegionApiException("NON_2XX", "Region API non-2xx: " + res.getStatusCode());
            }

            Page page = parseJsonOrThrow(res.getHeaders().getContentType(), res.getBody());
            if (page.errorMessage != null && !page.errorMessage.isBlank()) {
                throw new RegionApiException("UPSTREAM_ERROR", page.errorMessage);
            }
            if (page.rows.isEmpty()) break;

            out.addAll(page.rows);
            if (totalCount == null) totalCount = page.totalCount;

            if (totalCount != null) {
                if (pageNo * numOfRows >= totalCount) break;
            } else {
                if (page.rows.size() < numOfRows) break;
            }
            pageNo++;
        }

        log.info("[RegionAPI] fetched rows={} (totalCount={})", out.size(), totalCount);

        // ✅ 반환 직전 정규화
        return RegionSourceProvider.normalizeAll(out);
    }

    // ───────────────────── 프로빙(파라미터 조합 자동탐지) ─────────────────────

    /** 시도 조합(https/http, key 이름/인코딩, type 파라미터 이름) */
    private static class Attempt {
        final String baseUrl;        // https://.../getStanReginCdList
        final String keyParam;       // serviceKey | ServiceKey
        final String typeParam;      // type | returnType | resultType
        final boolean useEncodedKey; // true → encoded key 사용
        Attempt(String baseUrl, String keyParam, String typeParam, boolean useEncodedKey) {
            this.baseUrl = baseUrl; this.keyParam = keyParam; this.typeParam = typeParam; this.useEncodedKey = useEncodedKey;
        }
        String masked() { return maskKey(buildUrl(baseUrl, keyParam, typeParam, "****", 1, 1)); }
    }

    private Attempt detectWorkingAttempt(String baseUrlFixed, String keyRaw, String keyEnc) {
        // https/https 모두 준비
        String httpsUrl = forceHttps(baseUrlFixed);
        // 🛠 FIX: http 폴백은 반드시 http:// 스킴을 강제
        String httpUrl  = forceHttp(baseUrlFixed);

        // 조합 확대: type/returnType/resultType × serviceKey/ServiceKey × raw/encoded
        List<Attempt> attempts = List.of(
                new Attempt(httpsUrl, "serviceKey", "type",        false),
                new Attempt(httpsUrl, "ServiceKey", "type",        false),
                new Attempt(httpsUrl, "serviceKey", "returnType",  false),
                new Attempt(httpsUrl, "ServiceKey", "returnType",  false),
                new Attempt(httpsUrl, "serviceKey", "resultType",  false),
                new Attempt(httpsUrl, "ServiceKey", "resultType",  false),

                new Attempt(httpsUrl, "serviceKey", "type",        true),
                new Attempt(httpsUrl, "ServiceKey", "type",        true),
                new Attempt(httpsUrl, "serviceKey", "returnType",  true),
                new Attempt(httpsUrl, "ServiceKey", "returnType",  true),
                new Attempt(httpsUrl, "serviceKey", "resultType",  true),
                new Attempt(httpsUrl, "ServiceKey", "resultType",  true),

                // 일부 구형 게이트웨이/프록시 대응용 http 시도
                new Attempt(httpUrl,  "serviceKey", "type",        false),
                new Attempt(httpUrl,  "ServiceKey", "type",        false)
        );

        for (Attempt a : attempts) {
            String url = buildUrl(a.baseUrl, a.keyParam, a.typeParam, a.useEncodedKey ? keyEnc : keyRaw, 1, 1);
            try {
                ResponseEntity<String> res = httpGetWithRetry(url);
                String body = res.getBody();
                MediaType ct = res.getHeaders().getContentType();
                if (!res.getStatusCode().is2xxSuccessful() || body == null) {
                    log.warn("[RegionAPI][probe] non-2xx status={} url={}", res.getStatusCode(), maskKey(url));
                    continue;
                }
                if (!looksJson(ct, body)) {
                    log.warn("[RegionAPI][probe] non-JSON sample={} url={}", snippet(body), maskKey(url));
                    continue;
                }
                parse(body); // 형태 검증
                return a;
            } catch (Exception e) {
                log.warn("[RegionAPI][probe] exception url={} msg={}", maskKey(url), e.getMessage());
            }
        }
        throw new RegionApiException("NON_JSON", "Region API returned non-JSON for all attempts.");
    }

    // ───────────────────── URL/HTTP 유틸 ─────────────────────

    /** 최종 호출 URL 생성 (중복 파라미터 자동 방지, type=JSON 강제) */
    private static String buildUrl(String baseUrl, String keyParamName, String typeParamName, String keyValue, int pageNo, int num) {
        String u = baseUrl;
        u = appendQueryIfAbsent(u, keyParamName, keyValue);
        u = appendQueryIfAbsent(u, "pageNo", String.valueOf(pageNo));
        u = appendQueryIfAbsent(u, "numOfRows", String.valueOf(num));
        u = appendQueryIfAbsent(u, typeParamName, "JSON");
        return u;
    }

    private static String appendQueryIfAbsent(String url, String name, String value) {
        if (hasParam(url, name)) return url;
        char sep = url.contains("?") ? '&' : '?';
        return url + sep + name + '=' + value;
    }
    private static boolean hasParam(String url, String name) {
        String n = name.replaceAll("([\\[\\]().?*+^$|\\\\-])", "\\\\$1");
        return url.matches("(?i).*[?&]" + n + "=.*");
    }

    /** 공통 헤더 — text/plain 수용 추가 */
    private HttpHeaders defaultHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.valueOf("text/json"),
                MediaType.TEXT_PLAIN,
                MediaType.ALL
        ));
        h.set(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
        h.set(HttpHeaders.USER_AGENT, "WINO-Academy/1.0");
        return h;
    }

    private Page parseJsonOrThrow(MediaType ct, String body) throws Exception {
        if (!looksJson(ct, body)) {
            throw new RegionApiException("NON_JSON", "Region API returned non-JSON. sample=" + snippet(body));
        }
        return parse(body);
    }

    private static boolean looksJson(MediaType ct, String body) {
        String c = (ct == null ? "" : ct.toString().toLowerCase());
        if (c.contains("application/json") || c.contains("text/json")) return true;
        if (body == null) return false;
        String t = body.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
        // (ct가 text/plain이어도 본문이 JSON 형태면 OK)
    }

    private static boolean keyNeedsEncoding(String key) {
        return key != null && !key.contains("%");
    }
    private static String urlEncode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return s; }
    }

    /** https 강제 */
    private static String forceHttps(String url) {
        if (url == null) return null;
        if (url.startsWith("https://")) return url;
        if (url.startsWith("http://"))  return "https://" + url.substring("http://".length());
        return "https://" + url; // 스킴 없으면 https 가정
    }

    /** 🛠 http 강제(폴백 용도) */
    private static String forceHttp(String url) {
        if (url == null) return null;
        if (url.startsWith("http://"))  return url;
        if (url.startsWith("https://")) return "http://" + url.substring("https://".length());
        return "http://" + url; // 스킴 없으면 http 가정
    }

    /** /StanReginCd → /StanReginCd/getStanReginCdList 보정 */
    private static String ensureMethod(String url) {
        if (url == null) return null;
        if (url.matches(".*/StanReginCd/?$")) {
            return url.endsWith("/") ? url + "getStanReginCdList" : url + "/getStanReginCdList";
        }
        return url;
    }

    private static String maskKey(String url) {
        if (url == null) return null;
        return url.replaceAll("(?i)(serviceKey|ServiceKey)=[^&]+", "$1=***");
    }
    private static String snippet(String s){
        if(s==null) return "";
        String t=s.strip();
        return t.substring(0, Math.min(180, t.length())).replaceAll("\\s+"," ");
    }

    // ───────────────────── JSON 파싱 ─────────────────────

    /**
     * 공식 응답(JSON) 예:
     * {
     *   "StanReginCd": [
     *     { "head": [ { "totalCount": ..., "RESULT": { "resultCode": "INFO-000", ... } } ] },
     *     { "row": [ { "region_cd": "....", "locathigh_cd": "...", "locatadd_nm": "...", "sgg_cd": "...", "umd_cd": "...", "ri_cd": "..." }, ... ] }
     *   ]
     * }
     */
    private Page parse(String body) throws Exception {
        JsonNode root = om.readTree(body);
        JsonNode arr = root.path("StanReginCd");
        Integer totalCount = null;
        String resultCode = null, resultMsg = null;
        List<Row> rows = new ArrayList<>();

        if (arr.isArray()) {
            for (JsonNode node : arr) {
                // 메타(헤더) 파싱
                if (node.has("head")) {
                    JsonNode heads = node.path("head");
                    if (heads.isArray()) {
                        for (JsonNode h : heads) {
                            if (totalCount == null) {
                                Integer tc = intOf(h, "totalCount");
                                if (tc == null) tc = intOf(h, "list_total_count");
                                if (tc != null) totalCount = tc;
                            }
                            if (h.has("RESULT")) {
                                JsonNode r = h.get("RESULT");
                                resultCode = textOf(r, "resultCode");
                                resultMsg  = textOf(r, "resultMsg");
                                if (resultCode == null) {
                                    resultCode = textOf(r, "CODE");
                                    resultMsg  = textOf(r, "MESSAGE");
                                }
                            }
                        }
                    }
                }
                // 데이터(row) 파싱
                if (node.has("row")) {
                    for (JsonNode r : node.path("row")) {
                        String code  = textOf(r, "region_cd");
                        String name  = textOf(r, "locatadd_nm"); // 전체 경로명
                        String high  = textOf(r, "locathigh_cd");
                        String sgg   = textOf(r, "sgg_cd");
                        String umd   = textOf(r, "umd_cd");
                        String ri    = textOf(r, "ri_cd");

                        if (!isCode10(code)) continue; // 방어: 10자리 숫자만

                        byte depth = depthOf(code, sgg, umd, ri);
                        String parent = parentOf(code, high, depth);
                        String path = name;
                        String leaf = leafName(name);

                        // 우리 표준 Row
                        rows.add(new Row(code, leaf, depth, parent, path, true));
                    }
                }
            }
        }

        String error = null;
        // INFO-000 / INFO-0 는 정상
        if (resultCode != null && !"INFO-0".equalsIgnoreCase(resultCode) && !"INFO-000".equalsIgnoreCase(resultCode)) {
            error = resultCode + (resultMsg != null ? ": " + resultMsg : "");
        }
        return new Page(rows, totalCount, error);
    }

    private static Integer intOf(JsonNode n, String f){
        try { JsonNode v = n.get(f); return (v==null||v.isNull())?null:v.asInt(); }
        catch(Exception e){ return null; }
    }
    private static String textOf(JsonNode n, String f){
        if(n==null) return null;
        JsonNode v = n.get(f);
        return (v==null||v.isNull())?null:v.asText();
    }
    private static boolean isCode10(String s){ return s!=null && s.matches("\\d{10}"); }

    /** depth 판정: sgg/umd/ri 유무로 1~4 결정 */
    private static byte depthOf(String code, String sgg, String umd, String ri) {
        String sgg3 = nz(sgg, "000"), umd3 = nz(umd, "000"), ri2 = nz(ri, "00");
        if ("000".equals(sgg3) && "000".equals(umd3) && "00".equals(ri2)) return 1;
        if (!"000".equals(sgg3) && "000".equals(umd3) && "00".equals(ri2)) return 2;
        if (!"000".equals(umd3) && "00".equals(ri2)) return 3;
        return 4;
    }

    /** parentCode 계산: high 가 유효하면 사용, 아니면 규칙적으로 생성 */
    private static String parentOf(String code, String high, byte depth) {
        if (isCode10(high) && depth > 1) return high;
        String sido = code.substring(0,2), sgg=code.substring(2,5), umd=code.substring(5,8);
        return switch (depth) {
            case 1 -> null;
            case 2 -> sido + "00000000";
            case 3 -> sido + sgg + "00000";
            default -> /*4*/ sido + sgg + umd + "00";
        };
    }

    private static String nz(String v, String d){ if(v==null) return d; String t=v.trim(); return t.isEmpty()?d:t; }

    /** pathName 에서 마지막 토큰(리프명) 추출 */
    private static String leafName(String full){
        if(!StringUtils.hasText(full)) return full;
        String[] tok=full.trim().split("\\s+");
        return tok[tok.length-1];
    }

    private static final class Page {
        final List<Row> rows; final Integer totalCount; final String errorMessage;
        Page(List<Row> rows, Integer totalCount, String errorMessage){ this.rows=rows; this.totalCount=totalCount; this.errorMessage=errorMessage; }
    }
}
