// src/main/java/com/wino/academyapi/domain/code/dto/CommonCodeDtos.java
package com.wino.academyapi.domain.code.dto;

import lombok.*;

public class CommonCodeDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CodeItemRequest {
        private String code;
        private String name;
        private Integer sortOrder;
        private Boolean enabled;
        private String metaJson; // JSON string (선택)
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CodeGroupRequest {
        private String groupCode;      // create에서 필수, update에선 path 변수 사용
        private String name;
        private String description;
        private Integer sortOrder;
        private Boolean enabled;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CodeSimple {
        private String code;
        private String name;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CodeGroupResponse {
        private String groupCode;
        private String name;
        private String description;
        private Integer sortOrder;
        private Boolean enabled;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CodeItemResponse {
        private String code;
        private String name;
        private Integer sortOrder;
        private Boolean enabled;
        private String metaJson;
    }
}
