// src/main/java/com/wino/academyapi/domain/menu/repository/MenuItemRepository.java
package com.wino.academyapi.domain.menu.repository;

import com.wino.academyapi.domain.menu.entity.MenuItem;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;   // ADMIN / USER
import com.wino.academyapi.domain.menu.entity.MenuItem.MenuType;  // FOLDER / SCREEN
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 관리자/사용자 메뉴 저장소
 *
 * ✅ 핵심
 *  - 기존 메서드 유지
 *  - 시스템 로그 ↔ admin_menu 매핑용 쿼리 제공
 *    * ADMIN + SCREEN + enabled + path != null/'' 조건
 *    * 최장 prefix 매칭: ":appPath like concat(m.path, '%')" + LENGTH(m.path) DESC
 *
 * ⚙ 성능/인덱스
 *  - 아래 인덱스와 궁합이 좋습니다(utf8mb4 + MySQL 5.x → path(191) prefix 권장):
 *    CREATE INDEX idx_admin_menu_aud_type_path
 *      ON admin_menu (audience, type, enabled, path(191));
 *
 * 🛡 Enum 비교
 *  - JPQL에서 중첩 enum FQN(Outer$Inner) 대신 파라미터로 Enum 값을 전달합니다.
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    // ───────────────────────────────────────────────────────────────────────
    // 기존 사용 메서드 (유지)
    // ───────────────────────────────────────────────────────────────────────

    /** Audience 전체 트리 정렬 조회 */
    List<MenuItem> findByAudienceOrderByParentIdAscSortOrderAsc(Audience audience);

    /** 특정 부모 아래 정렬 조회 */
    List<MenuItem> findByAudienceAndParentIdOrderBySortOrderAsc(Audience audience, Long parentId);

    /** 같은 부모 안에서 메뉴명 중복 여부 확인 (UniqueConstraint와 함께 사용) */
    boolean existsByAudienceAndParentIdAndName(Audience audience, Long parentId, String name);

    /** 권한 매핑 저장 시 존재 검증용 (있는 ID만 빠르게 확인) */
    @Query("select m.id from MenuItem m where m.id in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);

    /** 사이드바용: enabled+visible 필터 + 안정 정렬 */
    @Query("""
        select m from MenuItem m
         where m.audience = :audience
           and m.enabled = true
           and m.visible = true
         order by m.depth asc, m.sortOrder asc, m.id asc
    """)
    List<MenuItem> findEnabledVisibleForSidebar(@Param("audience") Audience audience);

    // ───────────────────────────────────────────────────────────────────────
    // 시스템 로그 화면 매핑용 쿼리
    // ───────────────────────────────────────────────────────────────────────

    /**
     * ADMIN + SCREEN + enabled + path!=null/'' 메뉴 "전체 후보" 목록
     *  - Resolver가 메뉴 전체를 캐시한 뒤, 앱단에서 prefix 매칭하는 방식에 사용
     */
    @Query("""
        select m from MenuItem m
         where m.audience = :audience
           and m.type     = :type
           and m.enabled  = true
           and m.path     is not null
           and m.path     <> ''
         order by m.depth asc, m.sortOrder asc, m.id asc
    """)
    List<MenuItem> findAdminScreenWithPathEnabled(
            @Param("audience") Audience audience,
            @Param("type")     MenuType type
    );

    /**
     * 주어진 appPath(예: "/admin/codes/123")에 대해
     *  - "해당 경로로 시작하는" 메뉴만 후보로 뽑아 길이순 정렬
     *  - 상위 1건이 '최장 prefix' 매칭
     *
     * 주의: LENGTH(...) 는 JPQL 표준 함수로 문자열 길이(문자) 기준입니다.
     */
    @Query("""
        select m from MenuItem m
         where m.audience = :audience
           and m.type     = :type
           and m.enabled  = true
           and m.path     is not null
           and m.path     <> ''
           and :appPath like concat(m.path, '%')
         order by length(m.path) desc, m.depth asc, m.sortOrder asc, m.id asc
    """)
    List<MenuItem> findAdminScreenCandidatesForPath(
            @Param("audience") Audience audience,
            @Param("type")     MenuType type,
            @Param("appPath")  String appPath
    );

    /**
     * (최적화) 위 쿼리와 동일하되, Pageable 로 "상위 1건만" 조회 가능.
     *  - Resolver에서 PageRequest.of(0, 1) 로 호출하여 DB 왕복/전송량 감소.
     */
    @Query("""
        select m from MenuItem m
         where m.audience = :audience
           and m.type     = :type
           and m.enabled  = true
           and m.path     is not null
           and m.path     <> ''
           and :appPath like concat(m.path, '%')
         order by length(m.path) desc, m.depth asc, m.sortOrder asc, m.id asc
    """)
    List<MenuItem> findAdminScreenTop1ForPath(
            @Param("audience") Audience audience,
            @Param("type")     MenuType type,
            @Param("appPath")  String appPath,
            Pageable pageable
    );

    // (참고) 파생 쿼리로도 유사 조건을 만들 수 있으나,
    //        "like concat(...)" + "length 정렬"은 @Query JPQL이 가장 명료합니다.
}
