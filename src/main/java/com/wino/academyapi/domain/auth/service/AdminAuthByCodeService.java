// src/main/java/com/wino/academyapi/domain/auth/service/AdminAuthByCodeService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.auth.entity.RoleMenuByCode;
import com.wino.academyapi.domain.auth.repository.RoleMenuByCodeRepository;
import com.wino.academyapi.domain.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuthByCodeService {

    private final RoleMenuByCodeRepository roleMenuRepo;
    private final MenuItemRepository menuRepo;

    /** 역할코드 정규화 (ROLE_ 접두사 보장) */
    private String canonical(String roleCode) {
        if (roleCode == null) return null;
        String rc = roleCode.trim();
        if (rc.isEmpty()) return rc;
        return rc.startsWith("ROLE_") ? rc : "ROLE_" + rc;
    }

    /** 역할코드 기준으로 메뉴 ID 목록 조회 */
    @Transactional(readOnly = true)
    public List<Long> getRoleMenus(String roleCode) {
        String rc = canonical(roleCode);
        if (rc == null || rc.isBlank()) return List.of();
        return roleMenuRepo.findByRoleCode(rc).stream()
                .map(RoleMenuByCode::getMenuId)
                .toList();
    }

    /** 역할코드 기준으로 메뉴 매핑 전체 교체 */
    @Transactional
    public void updateRoleMenus(String roleCode, List<Long> menuIds) {
        String rc = canonical(roleCode);
        if (rc == null || rc.isBlank()) return;

        // 1. 기존 매핑 전체 삭제
        roleMenuRepo.deleteByRoleCode(rc);

        if (menuIds == null || menuIds.isEmpty()) return;

        // 2. 유효한 메뉴ID 필터링 (FK 제약 준수)
        List<Long> existing = menuRepo.findExistingIds(menuIds);
        if (existing.isEmpty()) return;

        // 3. 중복 제거 후 저장
        List<RoleMenuByCode> rows = existing.stream()
                .distinct()
                .map(mid -> RoleMenuByCode.builder().roleCode(rc).menuId(mid).build())
                .toList();

        roleMenuRepo.saveAll(rows);
    }
}