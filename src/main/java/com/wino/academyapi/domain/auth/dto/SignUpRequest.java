// src/main/java/com/wino/academyapi/domain/auth/dto/SignUpRequest.java
package com.wino.academyapi.domain.auth.dto;

import com.wino.academyapi.domain.admin.staff.entity.EmployeeType;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 신규 등록 요청 DTO
 * - 권한(role)은 받지 않음 (기본 ROLE_STAFF)
 * - 직원 구분(employeeType)은 코드(ENUM)로 받음: STAFF/TEACHER
 * - 연락처/주소 항목 포함
 */
@Getter @Setter
public class SignUpRequest {

    @NotBlank
    @Size(max = 120)
    private String userId;

    @NotBlank
    @Size(max = 80)
    private String userName;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotNull
    private EmployeeType employeeType; // 화면에선 한글(직원/선생님) 라벨, 실제 값은 STAFF/TEACHER

    // 선택 항목 (NULL 허용)
    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 20)
    private String emergencyContact;

    @Size(max = 10)
    private String postalCode;

    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String detailAddress;

    @Size(max = 100)
    private String workLocation;
}
