// src/main/java/com/wino/academyapi/domain/school/dto/SchoolSummary.java
package com.wino.academyapi.domain.school.dto;

import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 목록/검색 응답용 요약 DTO
 *
 * 설계 포인트
 * - record 사용: 불변, 얕은 값 전달에 적합
 * - Jackson: Java record 직렬화 지원(2.12+) / NULL 필드 미포함
 * - 문자열 필드 공백/빈문자 → null 로 정규화(from()/fromAll()에서 처리)
 * - 엔티티 → DTO 매핑 헬퍼 제공(컨트롤러/서비스에서 편하게 사용)
 *
 * 필드 설명
 * - id            : PK
 * - name          : 학교명
 * - stage         : 학부(E/M/H)
 * - homepageUrl   : 홈페이지 URL
 * - active        : 활성 여부 (⚠️ 원시 boolean → 항상 응답에 포함됨)
 * - postalCode    : 우편번호(신주소)
 * - address       : 도로명 주소(신주소)
 * - detailAddress : 지번(옛 주소) — 지오코딩 시 규칙적으로 저장
 * - admCode       : 법정동 코드(2/5/8/10자리 prefix 필터 가능)
 * - lat/lng       : 위/경도(DECIMAL; 스케일은 엔티티 정의 따름)
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // null 값은 응답에서 제외
@JsonPropertyOrder({
        "id","name","stage","homepageUrl","active",
        "postalCode","address","detailAddress","admCode","lat","lng"
})
public record SchoolSummary(

        @Schema(description = "학교 ID(PK)", example = "12345")
        Long id,

        @Schema(description = "학교명", example = "수원고등학교")
        String name,

        @Schema(description = "학부(E/M/H)", example = "H")
        SchoolStage stage,

        @Schema(description = "홈페이지 URL", example = "https://www.suwon.hs.kr")
        String homepageUrl,

        @Schema(description = "활성 여부", example = "true")
        boolean active,

        @Schema(description = "우편번호(신주소)", example = "16459")
        String postalCode,

        @Schema(description = "도로명 주소(신주소)", example = "경기도 수원시 권선구 권선로 123")
        String address,

        @Schema(description = "지번(구주소)", example = "경기도 수원시 권선구 고색동 123-4")
        String detailAddress,

        @Schema(description = "법정동 코드(2/5/8/10자리)", example = "4111500000")
        String admCode,

        @Schema(description = "위도", example = "37.2456789")
        BigDecimal lat,

        @Schema(description = "경도", example = "127.0123456")
        BigDecimal lng
) {
    /* ===========================================================
       엔티티 → DTO 매핑 헬퍼
       - 문자열 필드는 trim 후 빈문자면 null 로 통일(표시 일관성)
       - 좌표/코드는 엔티티 값을 그대로 사용(스케일/정밀도는 엔티티에 위임)
       =========================================================== */

    /** 단건 매핑 (null-safe) */
    public static SchoolSummary from(School s) {
        if (s == null) return null;
        return new SchoolSummary(
                s.getId(),
                t(s.getName()),
                s.getStage(),                       // enum은 그대로
                t(s.getHomepageUrl()),
                s.isActive(),                       // ✅ boolean 게터는 isActive()
                t(s.getPostalCode()),
                t(s.getAddress()),
                t(s.getDetailAddress()),
                t(s.getAdmCode()),
                s.getLat(),
                s.getLng()
        );
    }

    /** 컬렉션 일괄 매핑 (null/빈 컬렉션 안전) */
    public static List<SchoolSummary> fromAll(Collection<School> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<SchoolSummary> out = new ArrayList<>(rows.size());
        for (School s : rows) {
            SchoolSummary dto = from(s);
            if (dto != null) out.add(dto);
        }
        return out;
    }

    // ───────── 내부 유틸: trim→null ─────────
    private static String t(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}
