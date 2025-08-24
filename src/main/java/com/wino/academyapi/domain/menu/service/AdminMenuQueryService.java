// src/main/java/com/wino/academyapi/domain/menu/service/AdminMenuQueryService.java
package com.wino.academyapi.domain.menu.service;

import com.wino.academyapi.domain.auth.entity.RoleMenuByCode;
import com.wino.academyapi.domain.auth.repository.RoleMenuByCodeRepository;
import com.wino.academyapi.domain.menu.dto.MenuDtos;
import com.wino.academyapi.domain.menu.dto.MenuDtos.TreeNode;
import com.wino.academyapi.domain.menu.entity.MenuItem;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;
import com.wino.academyapi.domain.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMenuQueryService {

    private final MenuItemRepository menuRepo;
    private final RoleMenuByCodeRepository roleMenuRepo;

    @Transactional(readOnly = true)
    public List<TreeNode> mySidebar(Audience audience) {
        Set<String> roles = currentRoleCodes();

        boolean isSuper = roles.contains("ROLE_SYSTEM_ADMIN");

        // 기본 트리(visible & enabled)
        List<MenuItem> base = menuRepo.findEnabledVisibleForSidebar(audience);
        List<TreeNode> roots = MenuDtos.buildTreeFrom(base);

        if (isSuper) return roots;

        // 매핑 합집합
        Set<Long> allowed = roles.stream()
                .flatMap(rc -> roleMenuRepo.findByRoleCode(rc).stream())
                .map(RoleMenuByCode::getMenuId)
                .collect(Collectors.toSet());

        if (allowed.isEmpty()) return List.of();
        return MenuDtos.filterByAllowedIds(roots, allowed);
    }

    private Set<String> currentRoleCodes() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return Set.of();
        Set<String> out = new HashSet<>();
        for (GrantedAuthority ga : a.getAuthorities()) {
            String s = ga.getAuthority();
            if (s != null && s.startsWith("ROLE_")) out.add(s);
        }
        return out;
    }
}
