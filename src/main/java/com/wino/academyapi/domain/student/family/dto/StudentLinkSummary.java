package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/** 보호자 기준: 연결된 학생 + 링크 정보 요약 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentLinkSummary {
    private Long id;                 // 링크 PK
    private Long studentId;          // 학생 PK
    private String studentName;      // 학생 이름
    private String schoolStage;      // 학부
    private String workLocationCode; // 소속관 코드
    private String status;           // 학생 상태

    private String relationCode;
    private boolean primary;
    private boolean legalGuardian;
    private boolean receiveNotice;
    private boolean receiveBilling;
}