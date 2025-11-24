// src/main/java/com/wino/academyapi/domain/student/family/dto/GuardianLinkSummary.java
package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/**
 * 학생 기준: 연결된 가족(보호자) + 링크 정보 요약
 * - 프런트에서 쓰는 필드 이름에 맞춰 id = 링크ID 제공
 * - 계정 연결 정보(userId, loginId) 포함
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardianLinkSummary {

    /** 링크 PK (student_guardian_link.id) */
    private Long id;

    /** 보호자 PK */
    private Long guardianId;

    /** 보호자 이름 */
    private String guardianName;

    /** 보호자 연락처 */
    private String guardianPhone;

    /** 보호자 이메일 */
    private String guardianEmail;

    // 링크 정보
    private String relationCode;    // 관계 코드
    private boolean primary;        // 대표 여부
    private boolean legalGuardian;  // 법정대리인 여부
    private boolean receiveNotice;  // 학사 알림 수신 여부
    private boolean receiveBilling; // 청구 알림 수신 여부

    // 연결된 엔드유저(계정) 정보
    private Long userId;
    private String loginId;
}
