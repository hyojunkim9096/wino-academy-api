// src/main/java/com/wino/academyapi/domain/student/family/dto/StudentGuardianLinkDtos.java
package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/**
 * 학생-보호자 링크 생성/수정 요청 DTO 묶음
 */
public class StudentGuardianLinkDtos {

    /**
     * 링크 생성 요청 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinkCreateRequest {

        /** 학생 PK (학생 하위 POST에서는 path로 보정됨) */
        private Long studentId;

        /** 보호자 PK */
        private Long guardianId;

        /** 관계 코드 (예: FATHER/MOTHER/LEGAL_GUARDIAN/...) */
        private String relationCode;

        /** 대표 여부 */
        private boolean primary;

        /** 법정대리인 여부 (기본 true) */
        private boolean legalGuardian = true;

        /** 학사 알림 수신 여부 (기본 true) */
        private boolean receiveNotice = true;

        /** 청구 알림 수신 여부 (기본 true) */
        private boolean receiveBilling = true;
    }

    /**
     * 링크 수정 요청 DTO
     * - null 아닌 필드만 부분 수정
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinkUpdateRequest {

        private String relationCode;
        private Boolean primary;
        private Boolean legalGuardian;
        private Boolean receiveNotice;
        private Boolean receiveBilling;
    }
}
