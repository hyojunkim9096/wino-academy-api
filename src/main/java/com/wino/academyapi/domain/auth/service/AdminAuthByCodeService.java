// src/main/java/com/wino/academyapi/domain/auth/service/AdminAuthByCodeService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.auth.entity.RoleMenuByCode;
import com.wino.academyapi.domain.auth.repository.RoleMenuByCodeRepository;
import com.wino.academyapi.domain.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminAuthByCodeService {

    private final RoleMenuByCodeRepository roleMenuRepo;
    private final MenuItemRepository menuRepo;

    /** 역할코드를 항상 ROLE_ 접두로 통일 */
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

        // 전체 삭제 후
        roleMenuRepo.deleteByRoleCode(rc);

        // 비어있으면 끝
        if (menuIds == null || menuIds.isEmpty()) return;

        // 유효한 메뉴ID만 남기고 중복 제거
        List<Long> existing = menuRepo.findExistingIds(menuIds);
        if (existing.isEmpty()) return;
        List<Long> uniq = existing.stream().distinct().toList();

        // 저장
        List<RoleMenuByCode> rows = uniq.stream()
                .map(mid -> RoleMenuByCode.builder().roleCode(rc).menuId(mid).build())
                .toList();
        try {
            roleMenuRepo.saveAll(rows);
        } catch (DataIntegrityViolationException e) {
            // 제약조건 위반 등은 400대로 전환하고 싶으면 Controller에서 잡아 400으로 내려도 됩니다.
            throw e;
        }
    }
}
