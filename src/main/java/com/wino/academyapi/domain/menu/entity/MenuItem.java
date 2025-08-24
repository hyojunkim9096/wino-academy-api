// src/main/java/com/wino/academyapi/domain/menu/entity/MenuItem.java
package com.wino.academyapi.domain.menu.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "admin_menu", indexes = {
        @Index(name = "idx_menu_parent", columnList = "parent_id"),
        @Index(name = "idx_menu_sort", columnList = "parent_id, sort_order"),
        @Index(name = "idx_menu_audience_parent", columnList = "audience, parent_id, sort_order")
}, uniqueConstraints = {
        // ✅ 같은 Audience + 같은 부모 아래에서 메뉴명 중복 금지
        @UniqueConstraint(name = "uk_menu_name_in_parent", columnNames = {"audience", "parent_id", "name"})
})
public class MenuItem {

    public enum MenuType { FOLDER, SCREEN }        // ✅ 1,2뎁스=FOLDER/SCREEN 허용, 3뎁스=SCREEN 강제
    public enum Audience { ADMIN, USER }           // ✅ 상단 토글(관리자/유저)로 필터링

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Audience audience;                     // ✅ 관리자/유저 구분

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MenuType type;                         // ✅ FOLDER or SCREEN

    /** 1~3 (서비스에서 계산/검증) */
    @Column(nullable = false)
    private int depth;                             // ✅ 최대 3

    @Column(nullable = false, length = 80)
    private String name;

    /** 라우팅 경로 (SCREEN에 필수) */
    @Column(length = 200)
    private String path;

    /** 프론트 컴포넌트 키 (SCREEN에 권장/필수) */
    @Column(name = "component_key", length = 120)
    private String componentKey;

    // 🆕 게시판 타입(공통코드: BOARD_TYPE.code) — USER+SCREEN에서만 의미
    @Column(name="board_type", length = 40)
    private String boardType;

    /** 아이콘 키 (선택) */
    @Column(length = 80)
    private String icon;

    /** 상위 메뉴 (최상위는 null) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 부모 내 정렬 순서 (0..N-1) */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 표시/활성 */
    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private boolean enabled;

    /** 권한(선택) */
    @Column(name = "required_role", length = 40)
    private String requiredRole;

    /** 편의: 같은 부모 안에서 이동 */
    public void moveTo(int newSortOrder) {
        this.sortOrder = Math.max(0, newSortOrder);
    }
}
