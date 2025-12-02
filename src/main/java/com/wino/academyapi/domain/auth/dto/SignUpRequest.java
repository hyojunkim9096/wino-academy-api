// src/main/java/com/wino/academyapi/domain/auth/dto/SignUpRequest.java
package com.wino.academyapi.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wino.academyapi.domain.member.entity.EmployeeType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 신규 등록 요청 DTO
 *
 * 프론트엔드에서 /api/admin/register 로 보내는 JSON 을 그대로 받는 클래스.
 *
 *  - 필수값:
 *      · userId
 *      · userName
 *      · birthdate (yyyy-MM-dd 형식)
 *      · password (8자 이상)
 *      · employeeType (STAFF / TEACHER)
 *      · email
 *
 *  - 선택값:
 *      · phoneNumber / emergencyContact / postalCode / address / detailAddress
 */
@Getter
@Setter
public class SignUpRequest {

    /** 로그인 ID (아이디) */
    @NotBlank
    @Size(max = 120)
    @JsonProperty("userId")
    private String userId;

    /** 이름 */
    @NotBlank
    @Size(max = 80)
    @JsonProperty("userName")
    private String userName;

    /**
     * 생년월일 (필수)
     *
     *  - 예: "1990-08-12"
     *  - null 또는 "" 이면 @NotBlank 에서 "생년월일은 필수입니다." 메시지로 걸림
     *  - 값이 있으면 @Pattern 에서 yyyy-MM-dd 형식만 허용
     */
    @NotBlank(message = "생년월일은 필수입니다.")
    @Size(max = 10)
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}$",
            message = "생년월일은 yyyy-MM-dd 형식이어야 합니다."
    )
    @JsonProperty("birthdate")
    private String birthdate;

    /** 비밀번호 (평문) */
    @NotBlank
    @Size(min = 8, max = 100)
    @JsonProperty("password")
    private String password;

    /** 직원 구분 (STAFF / TEACHER) */
    @NotNull
    @JsonProperty("employeeType")
    private EmployeeType employeeType;

    /** 이메일 (필수) */
    @NotBlank(message = "이메일은 필수입니다.")
    @Email
    @Size(max = 160)
    @JsonProperty("email")
    private String email;

    /** 핸드폰 번호 (선택) */
    @Size(max = 20)
    @JsonProperty("phoneNumber")
    private String phoneNumber;

    /** 비상 연락처 (선택) */
    @Size(max = 20)
    @JsonProperty("emergencyContact")
    private String emergencyContact;

    /** 우편번호 (선택) */
    @Size(max = 10)
    @JsonProperty("postalCode")
    private String postalCode;

    /** 주소 (선택) */
    @Size(max = 255)
    @JsonProperty("address")
    private String address;

    /** 상세 주소 (선택) */
    @Size(max = 255)
    @JsonProperty("detailAddress")
    private String detailAddress;

    // workLocation은 입력받지 않음 (Service에서 기본값 "N" 처리)
}
