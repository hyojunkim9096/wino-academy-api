// src/main/java/com/wino/academyapi/domain/admin/dto/AdminUserDtos.java
package com.wino.academyapi.domain.admin.dto;

import com.wino.academyapi.domain.admin.entity.EmployeeType;   // ✅ 직원구분 enum(STAFF/TEACHER)
import lombok.*;

/**
 * Admin(직원) 관련 DTO 묶음
 * - 목록/상세 공용 요약: StaffSummary
 * - 일부 필드 수정:    StaffUpdateRequest
 * - 비밀번호 변경:     PasswordChangeRequest
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

        // ✅ 직원 구분을 변경할 수 있도록 유지
        private EmployeeType employeeType;
    }

    /** 비밀번호 변경 요청 DTO */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PasswordChangeRequest {
        private String newPassword;
    }
}
