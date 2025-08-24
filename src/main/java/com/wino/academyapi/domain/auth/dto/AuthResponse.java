// api/src/main/java/com/wino/academyapi/domain/auth/dto/AuthResponse.java
package com.wino.academyapi.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 응답 DTO: { "token": "Bearer ..." } */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    @JsonProperty("token")
    private String token;
}
