// src/main/java/com/wino/academyapi/domain/school/entity/SchoolStage.java
package com.wino.academyapi.domain.school.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.*;

/**
 * 학부 단계: 초/중/고
 *
 * 설계 포인트
 * - DB 저장: EnumType.STRING(1글자)로 E/M/H 저장 (School.stage 컬럼 length=1과 호환)
 * - JSON 직렬화: @JsonValue 로 "E/M/H" 반환 → API 응답이 항상 1글자 코드로 일관
 * - JSON 역직렬화: @JsonCreator 로 다양한 입력을 유연 파싱
 *   · 허용 예시:
 *       - 초등: "E", "e", "el", "elem", "elementary", "primary", "초", "초등", "초교", "초등학교"
 *       - 중학: "M", "ms", "mid", "middle", "junior high", "중", "중학", "중학교"
 *       - 고등: "H", "hs", "high", "senior high", "고", "고교", "고등", "고등학교"
 *   · 공백/하이픈/언더스코어/점(.) 등 구분자는 무시("junior-high" == "junior high")
 *
 * 내구성 보강
 * - 괄호 등 부가 표기가 붙은 값("고등학교(자율형)")도 매핑되도록 전처리 + contains 휴리스틱 추가
 */
public enum SchoolStage {
    E("E", "초등학교", Set.of(
            // 코드/영문
            "E", "EL", "ELEM", "ELEMENTARY", "ELEMENTARYSCHOOL",
            "PRIMARY", "PRIMARYSCHOOL",
            // 한글
            "초", "초등", "초교", "초등학교"
    )),
    M("M", "중학교", Set.of(
            // 코드/영문
            "M", "MS", "MID", "MIDDLE", "MIDDLESCHOOL",
            "JUNIORHIGH", "JUNIORHIGHSCHOOL", "JH",
            // 한글
            "중", "중학", "중학교"
    )),
    H("H", "고등학교", Set.of(
            // 코드/영문
            "H", "HS", "HIGH", "HIGHSCHOOL", "SENIORHIGH", "SENIORHIGHSCHOOL",
            // 한글
            "고", "고교", "고등", "고등학교"
    ));

    /** 1글자 코드: "E" | "M" | "H" (DB/JSON 저장값) */
    private final String code;

    /** 한국어 라벨(표시용) */
    private final String koLabel;

    /** 동의어/별칭(정규화된 문자열 세트; 대소문자/구분자 무시) */
    private final Set<String> synonymsUpper;

    SchoolStage(String code, String koLabel, Set<String> synonyms) {
        this.code = code;
        this.koLabel = koLabel;

        // 동의어 세트 정규화(대소문자/구분자 무시) + 자기 자신의 코드도 포함
        Set<String> u = new HashSet<>();
        if (synonyms != null) {
            for (String s : synonyms) {
                String n = norm(s);
                if (!n.isEmpty()) u.add(n);
            }
        }
        u.add(norm(code));
        this.synonymsUpper = Collections.unmodifiableSet(u);
    }

    /* ======================= 직렬화/표시 ======================= */

    /** JSON 직렬화 시 1글자 코드("E/M/H")로 출력 */
    @JsonValue
    public String code() { return code; }

    /** 한국어 라벨(표시용) */
    public String labelKo() { return koLabel; }

    /** 로깅/디버깅 시에도 일관되게 1글자 코드 출력 */
    @Override
    public String toString() { return code; }

    /* ======================= 파싱 유틸 ======================= */

    /** 기본 코드 파싱 ("E"|"M"|"H" 전용, 대소문자 무시) */
    public static Optional<SchoolStage> fromCode(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "E": return Optional.of(E);
            case "M": return Optional.of(M);
            case "H": return Optional.of(H);
            default:  return Optional.empty();
        }
    }

    /**
     * 유연 파싱(별칭/한글/복합표현 포함)
     * - 공백/하이픈/언더스코어/점(.) 등 구분자 무시
     * - 괄호/대괄호 등의 부가 표기는 먼저 제거 ("고등학교(자율형)" → "고등학교")
     * - 동의어 매칭 실패 시 한글 키워드 포함 검사("초등/중학/중학교/고등/고교")
     */
    public static Optional<SchoolStage> parseFlexible(String s) {
        if (s == null || s.isBlank()) return Optional.empty();

        // 1) 빠른 코드 파싱 시도
        Optional<SchoolStage> byCode = fromCode(s);
        if (byCode.isPresent()) return byCode;

        // 2) 괄호 등 부가 표기를 먼저 제거 → 표준 정규화 후 동의어 매칭
        String stripped = stripParenthetical(s);
        String key = norm(stripped);
        for (SchoolStage st : values()) {
            if (st.synonymsUpper.contains(key)) return Optional.of(st);
        }

        // 3) 휴리스틱: 원문에 한글 키워드가 포함되면 매핑
        String raw = s.trim();
        if (containsElemKo(raw)) return Optional.of(E);
        if (containsMidKo(raw))  return Optional.of(M);
        if (containsHighKo(raw)) return Optional.of(H);

        // 4) 영문 일반 키워드(동의어에 없을 경우 대비)
        String rawU = raw.toUpperCase(Locale.ROOT);
        if (rawU.contains("ELEMENTARY") || rawU.contains("PRIMARY")) return Optional.of(E);
        if (rawU.contains("MIDDLE") || rawU.contains("JUNIOR"))      return Optional.of(M);
        if (rawU.contains("HIGH")   || rawU.contains("SENIOR"))      return Optional.of(H);

        return Optional.empty();
    }

    /**
     * Jackson 역직렬화 지원
     * - body에 "stage": "초등학교" / "elementary" / "E" 등 들어와도 매핑
     * - 실패 시 IllegalArgumentException → 400(Bad Request)로 매핑하기 좋음
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SchoolStage jsonCreate(String s) {
        return parseFlexible(s).orElseThrow(() ->
                new IllegalArgumentException("Unknown stage: " + s));
    }

    /* ======================= 내부 정규화/전처리 ======================= */

    /** 괄호류와 그 안의 내용을 제거: "(...)", "[...]", "{...}" */
    private static String stripParenthetical(String s) {
        if (s == null) return "";
        String t = s;
        t = t.replaceAll("\\(.*?\\)", ""); // ( ... )
        t = t.replaceAll("\\[.*?\\]", ""); // [ ... ]
        t = t.replaceAll("\\{.*?\\}", ""); // { ... }
        return t.trim();
    }

    /** 한글 키워드 포함 검사 (원문 기준) */
    private static boolean containsElemKo(String raw) {
        return raw.contains("초등") || raw.contains("초교") || raw.contains("초등학교");
    }
    private static boolean containsMidKo(String raw) {
        return raw.contains("중학") || raw.contains("중학교");
    }
    private static boolean containsHighKo(String raw) {
        return raw.contains("고등") || raw.contains("고교") || raw.contains("고등학교");
    }

    /**
     * 파싱 정규화:
     * - trim → upper → 구분자 제거(공백/하이픈/언더스코어/점 등)
     * - 한글/영문/숫자만 남기고 모두 제거하여 비교 강건성↑
     *   예: "Junior High" → "JUNIORHIGH"
     */
    private static String norm(String s) {
        if (s == null) return "";
        String t = s.trim().toUpperCase(Locale.ROOT);
        // 괄호/대괄호/중괄호 내용은 stripParenthetical 에서 제거했으므로 여기선 폭넓은 구분자 제거
        t = t.replaceAll("[^0-9A-Z가-힣]+", ""); // 영숫자/한글 외 제거
        return t;
    }
}
