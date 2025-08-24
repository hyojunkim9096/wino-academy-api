// src/main/java/com/wino/academyapi/domain/auth/dto/PasswordResetRequest.java
package com.wino.academyapi.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 비밀번호 재설정 코드 요청 DTO */
@Getter @Setter
public class PasswordResetRequest {
    @NotBlank
    private String userId;
}
