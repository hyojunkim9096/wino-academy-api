// src/main/java/com/wino/academyapi/domain/auth/entity/RoleMenuByCodeId.java
package com.wino.academyapi.domain.auth.entity;

import lombok.*;
import java.io.Serializable;

/** ✅ 복합키: (role_code, menu_id) */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class RoleMenuByCodeId implements Serializable {
    private String roleCode;
    private Long menuId;
}
