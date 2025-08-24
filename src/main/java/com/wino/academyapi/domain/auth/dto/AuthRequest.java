// src/main/java/com/wino/academyapi/domain/auth/dto/AuthRequest.java
package com.wino.academyapi.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 로그인 요청 DTO */
public class AuthRequest {
    @Getter @Setter
    public static class Login {
        @NotBlank private String userId;
        @NotBlank private String password;
    }
}
