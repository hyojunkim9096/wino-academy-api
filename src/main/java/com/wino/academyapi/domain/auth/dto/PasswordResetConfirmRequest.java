// src/main/java/com/wino/academyapi/domain/auth/dto/PasswordResetConfirmRequest.java
package com.wino.academyapi.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 비밀번호 재설정 최종 확인 DTO */
@Getter @Setter
public class PasswordResetConfirmRequest {
    @NotBlank
    private String userId;
    @NotBlank
    private String code;                // 6자리 코드 원문
    @NotBlank
    private String newPassword;
}
