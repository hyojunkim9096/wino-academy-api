// src/main/java/com/wino/academyapi/domain/admin/staff/dto/AdminUserDtos.java
package com.wino.academyapi.domain.admin.staff.dto;

import com.fasterxml.jackson.annotation.JsonFormat; // ✅ 추가
import com.wino.academyapi.domain.admin.staff.entity.EmployeeType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDate;

public class AdminUserDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StaffSummary {
        private Long id;
        private String userId;
        private String userName;

        // ✅ [수정] 조회 시에도 포맷 맞춰서 내보냄
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate birthdate;

        private String email;
        private String phoneNumber;
        private String emergencyContact;
        private String roleCode;
        private String workLocation;
        private String status;
        private Long profileImageId;
        private EmployeeType employeeType;

        private String photoUrl;
        private String photoPath;

        @JsonProperty("postalCode")
        private String postalCode;
        private String address;
        @JsonProperty("detailAddress")
        private String detailAddress;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StaffUpdateRequest {
        private String roleCode;
        private String workLocation;
        private String status;
        private String email;
        private String phoneNumber;
        private String userName;

        // ✅ [수정] 수정 시에도 날짜 포맷 인식
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate birthdate;

        private String emergencyContact;
        private EmployeeType employeeType;

        @JsonProperty("postalCode")
        private String postalCode;
        private String address;
        @JsonProperty("detailAddress")
        private String detailAddress;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PasswordChangeRequest {
        private String newPassword;
    }
}