package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/**
 * 학생 기준: 연결된 가족(보호자) + 링크 정보 요약
 * - 프런트에서 쓰는 필드 이름에 맞춰 id = 링크ID 제공
 * - nameSnapshot/phoneSnapshot을 쓰는 화면을 위해 guardianName/guardianPhone 제공(프론트가 fallback 처리)
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuardianLinkSummary {
    private Long id;                // 링크 PK (linkId)
    private Long guardianId;        // 보호자 PK
    private String guardianName;    // 보호자 이름
    private String guardianPhone;   // 연락처
    private String guardianEmail;   // 이메일

    private String relationCode;    // 관계 코드
    private boolean primary;        // 대표 여부
    private boolean legalGuardian;  // 법정대리인
    private boolean receiveNotice;  // 학사 알림 수신
    private boolean receiveBilling; // 청구 알림 수신
}