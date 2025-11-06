// src/main/java/com/wino/academyapi/domain/guardian/dto/GuardianDtos.java
package com.wino.academyapi.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

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
}