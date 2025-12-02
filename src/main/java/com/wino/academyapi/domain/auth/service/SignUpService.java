// src/main/java/com/wino/academyapi/domain/auth/service/SignUpService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.member.entity.AdminUser;
import com.wino.academyapi.domain.member.entity.EmployeeType;
import com.wino.academyapi.domain.member.repository.AdminUserRepository;
import com.wino.academyapi.domain.auth.dto.SignUpRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 관리자 신규 등록 서비스
 *
 * 정책 요약:
 *  - birthdate: 필수(String) + yyyy-MM-dd 형식 + LocalDate로 변환 후 DATE 컬럼에 저장
 *  - employeeType 에 따라 role 자동 설정
 *      · TEACHER → ROLE_TEACHER_BASIC
 *      · STAFF   → ROLE_STAFF
 *      · 그 외   → ROLE_STAFF (안전 기본값)
 */
@Service
@RequiredArgsConstructor
public class SignUpService {

    private static final Logger log = LoggerFactory.getLogger(SignUpService.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(SignUpRequest req) {
        // ---------------------------------------------------------------------
        // 1. 문자열 정규화 (trim + 빈 문자열 → null)
        // ---------------------------------------------------------------------
        final String userId        = trimToNull(req.getUserId());
        final String userName      = trimToNull(req.getUserName());
        final String email         = trimToNull(req.getEmail());
        final String phone         = trimToNull(req.getPhoneNumber());
        final String emergency     = trimToNull(req.getEmergencyContact());
        final String postal        = trimToNull(req.getPostalCode());
        final String address       = trimToNull(req.getAddress());
        final String detailAddress = trimToNull(req.getDetailAddress());

        // 근무지 기본값 "N" (나루관) 고정
        final String workLocation = "N";

        final EmployeeType employeeType = req.getEmployeeType();

        // ---------------------------------------------------------------------
        // 2. 필수값 검증 (서버 방어 로직)
        //    - @Valid(@NotBlank, @NotNull) 에서 1차로 걸러지지만
        //      혹시 모를 상황에 대비해 Service 단에서도 최소한 한 번 더 체크
        // ---------------------------------------------------------------------
        if (userId == null || userName == null) {
            throw new IllegalArgumentException("필수 입력값이 누락되었습니다.");
        }

        final String rawPassword = req.getPassword();
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
        }

        // ---------------------------------------------------------------------
        // 3. 생년월일 String → LocalDate (필수)
        //
        //    DTO 단계:
        //      · @NotBlank  : null / "" 이면 "생년월일은 필수입니다."
        //      · @Pattern   : yyyy-MM-dd 형식이 아니면 "생년월일은 yyyy-MM-dd 형식이어야 합니다."
        //
        //    여기 서비스 단계에서는:
        //      · trimToNull 로 한 번 더 정규화
        //      · null이 나오면 (이론상 없어야 하지만) 방어적으로 "필수입니다" 던짐
        //      · 값이 있으면 LocalDate.parse 로 파싱
        // ---------------------------------------------------------------------
        String rawBirth = trimToNull(req.getBirthdate()); // null 또는 "1990-08-12"
        log.debug("SignUpService.register() - raw birthdate from DTO = '{}'", rawBirth);

        if (rawBirth == null) {
            // 정상 경로로는 오지 않지만 방어용 (DTO @NotBlank가 이미 막아야 함)
            throw new IllegalArgumentException("생년월일은 필수입니다.");
        }

        LocalDate birthDateVal;
        try {
            birthDateVal = LocalDate.parse(rawBirth);
        } catch (Exception e) {
            // DTO 정규식 검증을 우회한 이상 값이 들어온 경우 방어
            throw new IllegalArgumentException("생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }

        // ---------------------------------------------------------------------
        // 4. 직원 구분에 따른 role 결정
        // ---------------------------------------------------------------------
        String role;
        if (employeeType == EmployeeType.TEACHER) {
            role = "ROLE_TEACHER_BASIC";
        } else if (employeeType == EmployeeType.STAFF) {
            role = "ROLE_STAFF";
        } else {
            // Enum 이 앞으로 더 늘어나도 일단 STAFF 권한으로 제한
            role = "ROLE_STAFF";
        }

        // ---------------------------------------------------------------------
        // 5. 중복 체크 (userId / email)
        // ---------------------------------------------------------------------
        if (adminUserRepository.existsByUserId(userId)) {
            throw new DataIntegrityViolationException("이미 존재하는 아이디입니다.");
        }

        if (email != null && adminUserRepository.existsByEmail(email)) {
            throw new DataIntegrityViolationException("이미 등록된 이메일입니다.");
        }

        // ---------------------------------------------------------------------
        // 6. 엔티티 생성 및 저장
        // ---------------------------------------------------------------------
        AdminUser user = AdminUser.builder()
                .userId(userId)
                .userName(userName)
                .birthdate(birthDateVal)           // ✅ 필수 LocalDate → DATE 컬럼
                .password(passwordEncoder.encode(rawPassword))
                .email(email)
                .phoneNumber(phone)
                .emergencyContact(emergency)
                .postalCode(postal)
                .address(address)
                .detailAddress(detailAddress)
                .workLocation(workLocation)
                .employeeType(employeeType)
                .role(role)                        // ✅ 직원 구분에 따른 권한
                .status("TEMPORARY")               // 기본 상태: TEMPORARY
                .failedLoginCount(0)
                .accountLockedUntil(null)
                .build();

        adminUserRepository.save(user);
    }

    /** 공백 문자열을 null 로 치환 */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
