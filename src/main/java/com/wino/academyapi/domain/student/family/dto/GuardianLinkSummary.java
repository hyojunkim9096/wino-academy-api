package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/**
 * 학생 기준: 연결된 가족(보호자) + 링크 정보 요약 DTO
 * - 프론트엔드에서 삭제/수정 시 사용할 식별자(linkId)를 포함합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardianLinkSummary {

    /** 링크 PK (student_guardian_link.id) - 삭제 시 사용 */
    private Long linkId;

    /** 보호자 정보 */
    private Long guardianId;
    private String guardianName;
    private String guardianPhone;
    private String guardianEmail;

    /** 링크 설정 정보 */
    private String relationCode;    // 관계 코드 (MOTHER, FATHER 등)
    private boolean isPrimary;      // 대표 보호자 여부
    private boolean legalGuardian;  // 법정대리인 여부
    private boolean receiveNotice;  // 알림 수신 여부
    private boolean receiveBilling; // 청구서 수신 여부

    /** 보호자 계정 연결 정보 (앱 사용 여부 확인용) */
    private Long userId;
    private String loginId;
}