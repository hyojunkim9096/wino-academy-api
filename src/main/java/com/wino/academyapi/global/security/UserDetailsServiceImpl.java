package com.wino.academyapi.global.security;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security가 로그인을 시도할 때, 사용자 아이디(username)를 기반으로
 * 실제 DB에서 사용자 정보를 조회해오는 역할을 합니다.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        // 전달받은 userId로 DB에서 AdminUser를 찾습니다.
        AdminUser adminUser = adminUserRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId));

        // 찾은 AdminUser 정보를 UserDetailsImpl 객체로 감싸서 반환합니다.
        return new UserDetailsImpl(adminUser);
    }
}