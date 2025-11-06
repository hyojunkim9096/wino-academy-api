// src/main/java/com/wino/academyapi/domain/student/dto/StudentDtos.java
package com.wino.academyapi.domain.student.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

/**
 * 학생 DTO 묶음 — 프런트 camelCase 규칙(표시/수정 모두 호환)
 * ✅ 메타(createdAt/createdByName/updatedAt/updatedByName) 필드 포함
 */
public class StudentDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentSummary {
        private Long id;

        /** 로그인 노출(엔드유저 계정 연결) */
        private Long userId;
        private String loginId;

        private String workLocationCode;
        private String schoolStage;
        private String status;
        private String name;
        private LocalDate birthdate;

        private Long schoolId;
        private String schoolName;   // ✅ 표시용(서버에서 resolve)
        private String gradeLabel;   // ✅ 화면/수정은 gradeLabel 사용

        private String phone;
        private String email;
        private boolean preferSms;
        private boolean preferEmail;
        private boolean preferPush;
        private String pushUserKey;

        @JsonProperty("postalCode")     private String postalCode;
        private String address;
        @JsonProperty("detailAddress")  private String detailAddress;

        private Long profileImageId;
        private String photoUrl;   // /uploads/** 완성 URL
        private String photoPath;  // 상대 경로(백업)
        private String memo;       // 요약메모(최신 student_memo 1건 반영)

        /* ===== ✅ 메타 정보 (student_hist + admin_user_info 조인 결과) ===== */
        private String createdAt;       // 예: "yyyy-MM-dd HH:mm:ss"
        private String createdByName;
        private String updatedAt;
        private String updatedByName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentCreateRequest {
        private String workLocationCode;
        private String schoolStage;
        private String status;
        private String name;
        private LocalDate birthdate;
        private Long schoolId;
        private String gradeLabel;   // ✅ 프런트에서 grade → gradeLabel 보정됨
        private String phone;
        private String email;
        private boolean preferSms = true;
        private boolean preferEmail;
        private boolean preferPush;
        private String pushUserKey;

        @JsonProperty("postalCode")     private String postalCode;
        private String address;
        @JsonProperty("detailAddress")  private String detailAddress;

        private String memo; // 전달 시 최신 student_memo로 append
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentUpdateRequest {
        private String workLocationCode;
        private String schoolStage;
        private String status;
        private String name;
        private LocalDate birthdate;
        private Long schoolId;
        private String gradeLabel;
        private String phone;
        private String email;
        private Boolean preferSms;
        private Boolean preferEmail;
        private Boolean preferPush;
        private String pushUserKey;

        @JsonProperty("postalCode")     private String postalCode;
        private String address;
        @JsonProperty("detailAddress")  private String detailAddress;

        private String memo; // 전달 시 최신 student_memo로 append
    }

    /** 업로드 응답 — 파일 PK */
    public record UploadResponse(Long fileId) {}

    /** 🔐 비밀번호 변경 요청 DTO */
    public record PasswordChangeRequest(String password) {}

    /** ✅ 계정 upsert 요청 DTO(loginId/password 중 전달된 것만 변경) */
    public record AccountUpsertRequest(String loginId, String password) {}

    /* ---------------- 메모 DTO ---------------- */

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentMemoSummary {
        private Long id;
        private Long studentId;
        private String content;
        private boolean pinned;        // ✅ 상단 고정 여부
        private String visibilityRole; // 열람 최소 권한(옵션)
        private String createdAt;      // 'yyyy-MM-dd HH:mm:ss'
        private Long createdBy;        // 작성자 app_user_id
    }

    /** 메모 생성 요청 DTO */
    public record StudentMemoCreateRequest(String content, String visibilityRole) {}

    /** 메모 업데이트 요청 DTO(본문/고정여부) */
    public record StudentMemoUpdateRequest(String content, Boolean pinned) {}
}