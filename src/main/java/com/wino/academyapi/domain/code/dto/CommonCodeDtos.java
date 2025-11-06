// src/main/java/com/wino/academyapi/domain/code/dto/CommonCodeDtos.java
package com.wino.academyapi.domain.code.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 공통코드 DTO 모음
 *
 * 주의(스키마 정합성):
 * - group_code : VARCHAR(64)
 * - code       : VARCHAR(64)
 * - name       : VARCHAR(128)
 * - description: VARCHAR(255)
 * - meta_json  : TINYTEXT(~255 bytes) → 유효성은 문자 기준 255로 제한
 *
 * 컨트롤러에서 @Valid 를 파라미터에 붙여야 검증이 적용됩니다.
 */
public class CommonCodeDtos {

    // ───────────────────────────────── 요청 DTO ─────────────────────────────────

    /**
     * 코드 아이템 생성/수정 요청
     * - sortOrder/enabled 는 null 가능 → 부분 업데이트 시 사용
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodeItemRequest {
        @NotBlank
        @Size(max = 64)
        private String code;

        @NotBlank
        @Size(max = 128)
        private String name;

        @Min(0)
        private Integer sortOrder;   // null 허용

        private Boolean enabled;     // null 허용

        @Size(max = 255)             // DB: TINYTEXT ≈ 255 bytes (멀티바이트 주의)
        private String metaJson;     // JSON string (선택)
    }

    /**
     * 코드 그룹 생성/수정 요청
     * - create 에서는 groupCode 필수, update 에서는 path 변수 사용 권장
     *   → 여기서는 NotBlank 미적용(공용 DTO로 사용하기 위해)
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodeGroupRequest {
        @Size(max = 64)
        private String groupCode;      // create에서만 사용

        @Size(max = 128)
        private String name;

        @Size(max = 255)
        private String description;

        @Min(0)
        private Integer sortOrder;     // null 허용

        private Boolean enabled;       // null 허용
    }

    // ───────────────────────────────── 응답/간이 DTO ─────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodeSimple {
        private String code;
        private String name;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodeGroupResponse {
        private String groupCode;
        private String name;
        private String description;
        private Integer sortOrder;
        private Boolean enabled;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodeItemResponse {
        private String code;
        private String name;
        private Integer sortOrder;
        private Boolean enabled;
        private String metaJson;
    }
}
