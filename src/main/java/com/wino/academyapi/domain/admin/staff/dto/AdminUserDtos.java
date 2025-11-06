// src/main/java/com/wino/academyapi/domain/admin/staff/dto/AdminUserDtos.java
package com.wino.academyapi.domain.admin.staff.dto;

import com.wino.academyapi.domain.admin.staff.entity.EmployeeType;   // ✅ 직원구분 enum(STAFF/TEACHER)
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Admin(직원) 관련 DTO 묶음
 * - 목록/상세 공용 요약: StaffSummary
 * - 일부 필드 수정:    StaffUpdateRequest
 * - 비밀번호 변경:     PasswordChangeRequest
 *
 * ⚠️ JSON 네이밍 전략
 * - 프론트(JS/React)는 camelCase 사용 → 본 DTO도 camelCase 필드명을 사용합니다.
 * - DB 컬럼은 snake_case 여도 무관 (엔티티에서 @Column(name="postal_code") 등으로 매핑)
 * - 만약 글로벌로 SNAKE_CASE 네이밍 전략을 켠 상태라면(예: ObjectMapper 설정),
 *   아래 @JsonProperty("postalCode") 같은 강제 이름 지정을 유지하세요.
 *   (글로벌 전략이 없다면 @JsonProperty는 생략해도 됩니다)
 */
public class AdminUserDtos {

    /**
     * 목록/상세 공용으로 사용하는 간단 요약 DTO
     *
     * 프론트 규칙:
     *  - 이미지 src는 photoUrl(우선) → 없으면 photoPath를 /uploads/ 접두와 합쳐서 사용
     *    · photoUrl : 공개 URL (예: /uploads/staff/123/face.jpg)
     *    · photoPath: 상대 경로(백업용, 예: staff/123/face.jpg)
     *
     * 백엔드 구성:
     *  - StaffAdminService.toSummary(...)에서 AttachFile 메타를 읽어
     *    PublicUrlHelper.toPublicUrl(...)로 photoUrl을 계산하고,
     *    엔티티의 relativePath(또는 directory/savedName)로 photoPath를 채운다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StaffSummary {
        private Long id;
        private String userId;
        private String userName;
        private String email;
        private String phoneNumber;

        /** ✅ 비상 연락처 */
        private String emergencyContact;

        /** 문자열 role (예: ROLE_STAFF) */
        private String roleCode;

        /** 근무지 코드(예: N/W 등) */
        private String workLocation;

        /** 상태: ACTIVE/TEMPORARY/INACTIVE/LOCKED */
        private String status;

        /** 프로필 이미지 파일 메타 ID (null 가능) */
        private Long profileImageId;

        /** 직원 구분 (STAFF/TEACHER) */
        private EmployeeType employeeType;

        // ===================== 이미지 렌더링용 필드 (프론트 요구) =====================

        /**
         * 공개 URL (예: /uploads/staff/123/face.jpg)
         * - PublicUrlHelper.toPublicUrl(절대경로 또는 상대경로) 로 계산
         * - 있으면 프런트는 이 값을 그대로 <img src> 에 사용
         */
        private String photoUrl;

        /**
         * 상대 경로(백업) (예: staff/123/face.jpg)
         * - photoUrl이 null일 때 프론트에서 "/uploads/" + photoPath 로 사용
         */
        private String photoPath;

        // ===================== 주소 정보 (camelCase로 노출) =====================

        /** 우편번호 (DB는 postal_code) */
        @JsonProperty("postalCode")
        private String postalCode;

        /** 기본 주소 */
        private String address;

        /** 상세 주소 (개인정보 → 프런트에서 읽기 전용 시 마스킹 가능) */
        @JsonProperty("detailAddress")
        private String detailAddress;
    }

    /** 일부 필드만 수정하는 요청 DTO */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StaffUpdateRequest {
        private String roleCode;
        private String workLocation;
        private String status;
        private String email;
        private String phoneNumber;
        private String userName;

        /** ✅ 비상 연락처(프론트에서 보낸 값 바인딩) */
        private String emergencyContact;

        // ✅ 직원 구분 변경 가능
        private EmployeeType employeeType;

        // ✅ 주소 수정 필드 (camelCase)
        @JsonProperty("postalCode")
        private String postalCode;

        private String address;

        @JsonProperty("detailAddress")
        private String detailAddress;
    }

    /** 비밀번호 변경 요청 DTO */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PasswordChangeRequest {
        private String newPassword;
    }
}
