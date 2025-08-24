// src/main/java/com/wino/academyapi/domain/auth/entity/RoleMenuByCode.java
package com.wino.academyapi.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ✅ 공통코드 ROLE의 code 값을 그대로 보관하는 매핑 테이블
 * - admin_role_menu_code(role_code, menu_id)
 * - ROLE 목록은 공통코드에서 관리(생성/수정/삭제)
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@IdClass(RoleMenuByCodeId.class)
@Table(name = "admin_role_menu_code")
public class RoleMenuByCode {

    @Id
    @Column(name = "role_code", length = 100, nullable = false)
    private String roleCode;

    @Id
    @Column(name = "menu_id", nullable = false)
    private Long menuId;
}
