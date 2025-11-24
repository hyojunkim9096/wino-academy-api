// src/main/java/com/wino/academyapi/domain/guardian/dto/GuardianDtos.java
package com.wino.academyapi.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

// Validation 애노테이션 (계정 생성용 요청 DTO에서 사용)
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 보호자(Guardian) 관련 DTO 묶음
 */
public class GuardianDtos {

    // ---------------------------------------------------------------------
    // 보호자 프로필 요약/상세용 DTO
    // ---------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GuardianSummary {

        private Long id;
        private String name;
        private String phone;
        private String email;
        private boolean preferSms;
        private boolean preferEmail;
        private boolean preferPush;
        private String pushUserKey;

        @JsonProperty("postalCode")
        private String postalCode;

        private String address;

        @JsonProperty("detailAddress")
        private String detailAddress;

        private String memo;

        // EndUser 계정 정보(있을 수도, 없을 수도 있음)
        private Long userId;      // end_user.id
        private String loginId;   // end_user.login_id
        private String userStatus; // end_user.status (ACTIVE / LOCKED 등)
    }

    // ---------------------------------------------------------------------
    // 보호자 생성 요청 DTO
    // ---------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GuardianCreateRequest {

        private String name;
        private String phone;
        private String email;
        private boolean preferSms = true;
        private boolean preferEmail;
        private boolean preferPush;
        private String pushUserKey;

        @JsonProperty("postalCode")
        private String postalCode;

        private String address;

        @JsonProperty("detailAddress")
        private String detailAddress;

        private String memo;
    }

    // ---------------------------------------------------------------------
    // 보호자 수정 요청 DTO
    //  - PATCH 성격으로 null 이 아닌 필드만 반영
    // ---------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GuardianUpdateRequest {

        private String name;
        private String phone;
        private String email;
        private Boolean preferSms;
        private Boolean preferEmail;
        private Boolean preferPush;
        private String pushUserKey;

        @JsonProperty("postalCode")
        private String postalCode;

        private String address;

        @JsonProperty("detailAddress")
        private String detailAddress;

        private String memo;
    }

    // ---------------------------------------------------------------------
    // 보호자 계정 생성/연결 요청 DTO
    //   - /api/admin/guardians/{id}/link-account 에 사용
    // ---------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccountLinkRequest {

        @NotBlank(message = "로그인 ID는 필수입니다.")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 6, message = "비밀번호는 6자리 이상이어야 합니다.")
        private String password;
    }

    // ❌ [정리] GuardianStudentSummary 는 더 이상 사용하지 않으므로 제거
    //  - 보호자 기준 학생 목록은 StudentLinkSummary( student.family.dto )를 재사용함.
}
