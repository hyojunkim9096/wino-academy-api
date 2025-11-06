// src/main/java/com/wino/academyapi/domain/region/sync/RegionSourceProvider.java
package com.wino.academyapi.domain.region.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 외부(예: 공공데이터포털)에서 지역 마스터를 읽어오는 공급자 인터페이스.
 *
 * 설계 목표
 * - 최소 요구사항: 전체 fetch 1회로 모든 Row를 반환한다.
 * - 선택 최적화: 구현체가 가능하면 prefix(법정동 코드 앞자리) 단위 부분 페치 제공.
 *   · 구현체가 미지원이면 기본 구현(디폴트 메서드)이 전체 fetch로 폴백한다 → 하위 호환 OK.
 * - 공통 정규화 헬퍼 제공: 공백/빈문자 → null, depth/useYn 기본값 등.
 *
 * 변경점(안전성 보강)
 * - normalize(): code는 반드시 "숫자 10자리"일 때만 유효로 간주(아니면 레코드 드롭).
 * - normalize(): parentCode도 "숫자 10자리"가 아니면 null로 보정.
 * - normalize(): depth는 0~4 범위로 클램프(예외 데이터 방어).
 */
public interface RegionSourceProvider {

    /** sourceUrl(없으면 설정값)에서 전체 지역 rows를 읽어온다. */
    List<Row> fetch(String sourceUrlOverride) throws Exception;

    // ───────────────── 선택 최적화(디폴트 구현 제공: 하위 호환 OK) ─────────────────

    /** 구현체가 prefix 기반 부분 페치를 지원하는지 여부 (기본 false). */
    default boolean supportsPrefixFetch() { return false; }

    /**
     * (선택) prefix 목록으로 부분 페치.
     * - 미지원 구현에서는 전체 fetch로 폴백한다(성능 최적화 없음).
     * - 지원 구현에서는 각 prefix 별 페이징/스로틀링 적용 가능.
     */
    default List<Row> fetchByPrefixes(List<String> prefixes, String sourceUrlOverride) throws Exception {
        // 기본 폴백: 전체 fetch 1회
        return fetch(sourceUrlOverride);
    }

    // ───────────────── 공통 정규화 유틸(선택 사용) ─────────────────

    /**
     * 외부 응답을 내부 표준 Row로 매핑할 때 사용할 수 있는 정규화 헬퍼.
     * - code/name trim
     * - code:     반드시 10자리 숫자일 때만 유효. 아니면 null 반환(= 드롭)
     * - parentCode/pathName: "" → null, parentCode가 10자리 숫자가 아니면 null
     * - depth null → 0 (방어적으로 0~4 클램프)
     * - useYn null → true
     *
     * ⚠️ 주의:
     * - 원본 Row의 name 이 null/빈문자면 record(Row)의 requireNonNull(name) 때문에 NPE.
     *   → 이 메서드에서 그런 Row는 null을 반환해 normalizeAll()에서 걸러지도록 한다.
     */
    static Row normalize(Row r) {
        if (r == null) return null;

        String code = trim(r.code);
        String name = trim(r.name);

        // 필수값 검증: code(숫자10자리)/name 이 유효하지 않으면 드롭
        if (code == null || name == null || name.isBlank() || !isCode10(code)) {
            return null;
        }

        // parentCode: 숫자10자리 아니면 null
        String parentRaw = trimToNull(r.parentCode);
        String parent = isCode10(parentRaw) ? parentRaw : null;

        // pathName: 공백 → null
        String path = trimToNull(r.pathName);

        // depth: null → 0, 방어적으로 0~4 클램프
        Byte depth = (r.depth == null) ? Byte.valueOf((byte) 0) : clampDepth(r.depth);

        // useYn: null → true
        Boolean use = (r.useYn == null) ? Boolean.TRUE : r.useYn;

        return new Row(code, name, depth, parent, path, use);
    }

    /** 리스트 정규화(편의) — normalize()가 null을 리턴하면 해당 Row는 드롭 */
    static List<Row> normalizeAll(List<Row> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            Row n = normalize(r);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    // ───────────────── 내부 유틸 ─────────────────

    Pattern DIGITS10 = Pattern.compile("^\\d{10}$");

    /** 법정동코드 유효성: 숫자 10자리 */
    private static boolean isCode10(String s) {
        return s != null && DIGITS10.matcher(s).matches();
    }

    /** depth 방어 클램프(0~4), null 방어는 호출부에서 이미 처리 */
    private static Byte clampDepth(Byte d) {
        int v = d.intValue();
        if (v < 0) v = 0;
        if (v > 4) v = 4;
        return (byte) v;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ───────────────── 표준 레코드 ─────────────────

    /**
     * 표준화된 한 줄(레코드)
     * - name 은 NOT NULL 보장(레코드 생성자에서 requireNonNull)
     */
    record Row(String code, String name, Byte depth, String parentCode, String pathName, Boolean useYn) {
        public Row {
            // 레코드 생성 시 가벼운 방어: null name 금지(엔티티에서 NOT NULL)
            Objects.requireNonNull(name, "name must not be null");
        }
    }
}
