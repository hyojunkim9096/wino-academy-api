// src/main/java/com/wino/academyapi/domain/region/dto/RegionResponse.java
package com.wino.academyapi.domain.region.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wino.academyapi.domain.region.entity.Region;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 공개/관리 API 공통 응답 DTO
 *
 * 설계 포인트
 * - 프런트 정렬/렌더 안정성: code, name 은 항상 "문자열" 보장(= null 금지, 빈문자 허용)
 * - 의미 없는 공백은 제거(trimming). parentCode, pathName 은 빈문자 → null 로 치환하여 응답을 정돈
 * - depth 는 Byte → Integer 로 변환(없으면 null)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "name", "depth", "parentCode", "pathName"})
public class RegionResponse {

    @Schema(description = "법정동 코드(10자리)", example = "4111500000")
    private String code;        // ← 항상 non-null(빈문자 가능)

    @Schema(description = "명칭(리프명)", example = "권선구")
    private String name;        // ← 항상 non-null(빈문자 가능)

    @Schema(description = "깊이(1~4)", example = "2")
    private Integer depth;      // Byte → Integer

    @Schema(description = "상위 법정동 코드(1뎁스는 null)", example = "4110000000")
    private String parentCode;  // 빈문자면 null

    @Schema(description = "전체 경로명(예: 수원시 권선구 고색동)", example = "수원시 권선구")
    private String pathName;    // 빈문자면 null

    /** 엔티티 → DTO (null/공백 안전) */
    public static RegionResponse from(Region r) {
        // 방어: JPA 결과에 null row 가 들어오는 일은 드물지만, 혹시를 대비해 빈 DTO 반환
        if (r == null) {
            return RegionResponse.builder()
                    .code("")     // non-null 계약 유지
                    .name("")     // non-null 계약 유지
                    .depth(null)
                    .parentCode(null)
                    .pathName(null)
                    .build();
        }

        return RegionResponse.builder()
                // ⚠ code/name 은 프런트의 normalize 와 정렬에서 자주 쓰이므로 "항상 문자열"로 보장
                .code(trimToEmpty(r.getCode()))
                .name(trimToEmpty(r.getName()))
                // depth 는 값이 없으면 null 유지(프런트 normalize 에서 숫자 캐스팅)
                .depth(r.getDepth() == null ? null : r.getDepth().intValue())
                // 표현상 의미 없는 공백은 null 로 치환하여 응답 최소화
                .parentCode(trimToNull(r.getParentCode()))
                .pathName(trimToNull(r.getPathName()))
                .build();
    }

    /** 컬렉션 일괄 변환(안전) */
    public static List<RegionResponse> fromAll(Collection<Region> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<RegionResponse> out = new ArrayList<>(rows.size());
        for (Region r : rows) {
            // from(r)는 절대 null 을 반환하지 않음 → 리스트 내 null 원소 방지
            out.add(from(r));
        }
        return out;
    }

    // ───────────── 내부 유틸 ─────────────

    /** null → "", 그 외 trim */
    private static String trimToEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }

    /** null 유지, trim 결과가 빈문자면 null 로 */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
