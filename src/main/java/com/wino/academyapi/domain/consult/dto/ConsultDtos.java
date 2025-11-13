package com.wino.academyapi.domain.consult.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/** 상담 DTO 묶음 */
public class ConsultDtos {

    /* ============================= 검증 그룹 ============================= */
    /** 검증 그룹: 학생 경로(/students/{id}/consults)에서의 생성 */
    public interface PathCreate {}
    /** 검증 그룹: 캐논컬(/consults)에서의 생성 */
    public interface CanonicalCreate {}

    /* ============================= 조회 DTO ============================= */

    /** * 조회 응답용 (참석자 + ✅ 학생/반/담임 정보 포함)
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConsultSummary {
        private Long id;
        private Long studentId;        // 학생 PK

        // ✅ [수정] 학생/반/담임/작성자 정보 추가
        private String studentName;
        private Long classId;
        private String className;
        private Long homeroomTeacherId;
        private String homeroomTeacherName;
        private Long writerId;         // 작성자(admin_user_info.id) — 최초 작성자
        private String writerName;

        private boolean homeroomOk;    // 담임(팀장) 승인 여부
        private String consultMethod;  // 예: CALL/VISIT 등
        private String consultType;    // 예: REGULAR/EMERGENCY 등
        private String title;
        private String content;
        private String actionPlan;
        private LocalDateTime consultAt;
        private LocalDateTime nextFollowupAt;
        private String visibilityRole; // 역할 기반 노출범위
        private boolean useYn;

        // 프런트 "생성:/수정:" 표기용
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private List<ConsultGuardianAttendee> attendees;
    }

    /* ============================= 생성 DTO ============================= */

    /** 생성 요청 */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConsultCreateRequest {
        /**
         * 캐논컬 생성(/consults)에서는 필수,
         * 학생 경로 생성(/students/{id}/consults)에서는 path 변수를 사용하므로 본문에 없어도 됨.
         */
        @NotNull(groups = CanonicalCreate.class)
        private Long studentId;

        /**
         * ⚠️ 서버에서 현재 로그인 사용자로 강제 세팅(최초 작성자).
         * 클라이언트가 넣어도 서버에서 무시/대체합니다. (하위 호환을 위해 필드만 유지)
         */
        private Long writerId;

        private boolean homeroomOk;

        @NotBlank(groups = { CanonicalCreate.class, PathCreate.class })
        private String consultMethod;   // CALL / VISIT / etc

        @NotBlank(groups = { CanonicalCreate.class, PathCreate.class })
        private String consultType;     // REGULAR / EMERGENCY (서버에서 검증/보정)

        @NotBlank(groups = { CanonicalCreate.class, PathCreate.class })
        private String title;

        @NotBlank(groups = { CanonicalCreate.class, PathCreate.class })
        private String content;

        private String actionPlan;

        @NotNull(groups = { CanonicalCreate.class, PathCreate.class })
        private LocalDateTime consultAt;

        private LocalDateTime nextFollowupAt;

        /** 없으면 null 허용 */
        private String visibilityRole;

        private boolean useYn = true;

        private List<ConsultGuardianAttendee> attendees; // 옵션
    }

    /* ============================= 수정 DTO ============================= */

    /** 수정 요청 (부분 업데이트) */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConsultUpdateRequest {
        private Boolean homeroomOk;
        private String consultMethod;
        private String consultType;
        private String title;
        private String content;
        private String actionPlan;
        private LocalDateTime consultAt;
        private LocalDateTime nextFollowupAt;
        private String visibilityRole;
        private Boolean useYn;

        private List<ConsultGuardianAttendee> attendees; // 전체 교체 방식
    }

    /* ============================= 참석자 DTO ============================= */

    /** 참석 보호자 스냅샷(한 명) */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConsultGuardianAttendee {
        private Long guardianId;     // null 가능
        private String relationCode; // 스냅샷 (예: FATHER/MOTHER 등, 대문자 권장)
        private String name;
        private String phone;
        private boolean presentYn = true;
        private String memo;
    }
}