// src/main/java/com/wino/academyapi/domain/guardian/dto/GuardianDtos.java
package com.wino.academyapi.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

// ✅ [오류 수정] NotBlank, Size 애노테이션을 위한 import 2줄 추가
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 보호자 DTO 묶음 */
public class GuardianDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GuardianSummary {
        private Long id;
        private String name;
        private String phone;
        private String email;
        private boolean preferSms;
        private boolean preferEmail;
        private boolean preferPush;
        private String pushUserKey;
        @JsonProperty("postalCode")  private String postalCode;
        private String address;
        @JsonProperty("detailAddress") private String detailAddress;
        private String memo;
        private Long userId;
        private String loginId;
        private String userStatus;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GuardianCreateRequest {
        private String name;
        private String phone;
        private String email;
        private boolean preferSms = true;
        private boolean preferEmail;
        private boolean preferPush;
        private String pushUserKey;
        @JsonProperty("postalCode")  private String postalCode;
        private String address;
        @JsonProperty("detailAddress") private String detailAddress;
        private String memo;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GuardianUpdateRequest {
        private String name;
        private String phone;
        private String email;
        private Boolean preferSms;
        private Boolean preferEmail;
        private Boolean preferPush;
        private String pushUserKey;
        @JsonProperty("postalCode")  private String postalCode;
        private String address;
        @JsonProperty("detailAddress") private String detailAddress;
        private String memo;
    }

    // ✅ [신규] 보호자 계정 연결/생성 요청 DTO
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AccountLinkRequest {
        @NotBlank(message = "로그인 ID는 필수입니다.")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 6, message = "비밀번호는 6자리 이상이어야 합니다.")
        private String password;
    }
}