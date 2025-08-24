// src/main/java/com/wino/academyapi/global/security/UserDetailsImpl.java
package com.wino.academyapi.global.security;

import com.wino.academyapi.domain.admin.entity.AdminUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 인증 사용자 정보
 * - 권한: DB의 role 문자열을 그대로 사용 (ROLE_ 접두사 포함으로 관리)
 * - 상태: 문자열 비교로 활성 여부 판단
 */
public class UserDetailsImpl implements UserDetails {

    private final AdminUser adminUser;

    public UserDetailsImpl(AdminUser adminUser) {
        this.adminUser = adminUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String r = adminUser.getRole();
        if (r == null || r.isBlank()) return Collections.emptyList();
        return Collections.singleton(new SimpleGrantedAuthority(r.trim().toUpperCase()));
    }

    @Override public String getPassword() { return adminUser.getPassword(); }
    @Override public String getUsername() { return adminUser.getUserId(); }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !adminUser.isLockedNow(); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return "ACTIVE".equalsIgnoreCase(adminUser.getStatus()); }
}
