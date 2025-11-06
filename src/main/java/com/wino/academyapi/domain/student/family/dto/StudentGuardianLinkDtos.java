package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/** 링크 생성/수정 요청 DTO 묶음 */
public class StudentGuardianLinkDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LinkCreateRequest {
        private Long studentId;      // 학생 PK (학생 하위 POST에서는 path로 보정됨)
        private Long guardianId;     // 보호자 PK
        private String relationCode; // 관계 코드 (예: FATHER/MOTHER/LEGAL_GUARDIAN/...)
        private boolean primary;        // 대표 여부
        private boolean legalGuardian = true;
        private boolean receiveNotice = true;
        private boolean receiveBilling = true;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LinkUpdateRequest {
        private String relationCode;
        private Boolean primary;
        private Boolean legalGuardian;
        private Boolean receiveNotice;
        private Boolean receiveBilling;
    }
}