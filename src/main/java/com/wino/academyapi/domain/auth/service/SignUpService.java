// src/main/java/com/wino/academyapi/domain/auth/service/SignUpService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.entity.EmployeeType;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.auth.dto.SignUpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원가입 서비스
 * - role/status를 문자열 코드로 저장
 *   · role 기본값: ROLE_STAFF
 *   · status 기본값: TEMPORARY
 */
@Service
@RequiredArgsConstructor
public class SignUpService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(SignUpRequest req) {
        // 입력 정규화
        final String userId = trimToNull(req.getUserId());
        final String userName = trimToNull(req.getUserName());
        final String email = trimToNull(req.getEmail());
        final String phone = trimToNull(req.getPhoneNumber());
        final String emergency = trimToNull(req.getEmergencyContact());
        final String postal = trimToNull(req.getPostalCode());
        final String address = trimToNull(req.getAddress());
        final String detailAddress = trimToNull(req.getDetailAddress());
        final String workLocation = trimToNull(req.getWorkLocation());
        final EmployeeType employeeType = req.getEmployeeType() != null ? req.getEmployeeType() : EmployeeType.STAFF;

        if (userId == null || userName == null) throw new IllegalArgumentException("아이디와 이름은 필수입니다.");
        if (req.getPassword() == null || req.getPassword().length() < 8)
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");

        // 중복 사전검증
        if (adminUserRepository.existsByUserId(userId)) {
            throw new DataIntegrityViolationException("이미 존재하는 아이디입니다.");
        }
        if (email != null && adminUserRepository.existsByEmail(email)) {
            throw new DataIntegrityViolationException("이미 등록된 이메일입니다.");
        }

        // 엔티티 생성 (role/status 문자열로 저장)
        AdminUser user = AdminUser.builder()
                .userId(userId)
                .userName(userName)
                .password(passwordEncoder.encode(req.getPassword()))
                .email(email)
                .phoneNumber(phone)
                .emergencyContact(emergency)
                .postalCode(postal)
                .address(address)
                .detailAddress(detailAddress)
                .workLocation(workLocation == null ? "N" : workLocation)
                .employeeType(employeeType)
                .role("ROLE_STAFF")   // 문자열
                .status("TEMPORARY")  // 문자열
                .failedLoginCount(0)
                .accountLockedUntil(null)
                .build();

        adminUserRepository.save(user);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
