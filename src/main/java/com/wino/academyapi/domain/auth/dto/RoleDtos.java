// src/main/java/com/wino/academyapi/domain/auth/dto/RoleDtos.java
package com.wino.academyapi.domain.auth.dto;

import lombok.*;
import java.util.List;

public class RoleDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoleSummary {
        private Long id;
        private String code;
        private String name;
        private String description;
        private boolean enabled;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoleUpsertRequest {
        private String code;         // 생성 시 필수
        private String name;
        private String description;
        private Boolean enabled;     // null이면 변경 안함
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoleMenusResponse {
        private Long roleId;
        private List<Long> menuIds;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoleMenusUpdateRequest {
        private List<Long> menuIds;  // 전체 교체
    }
}
