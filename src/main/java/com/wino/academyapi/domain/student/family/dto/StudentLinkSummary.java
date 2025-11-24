// src/main/java/com/wino/academyapi/domain/student/family/dto/StudentLinkSummary.java
package com.wino.academyapi.domain.student.family.dto;

import lombok.*;

/**
 * 보호자 기준: 연결된 학생 + 링크 정보 요약
 * - 학생/링크 + 학부/지점/상태/관계의 "코드 + 이름" 모두 포함
 * - GuardianDetailPanel 의 "연결된 학생" 테이블에서 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLinkSummary {

    // -----------------------------
    // 링크 / 학생 기본 정보
    // -----------------------------
    /** student_guardian_link.id (링크 PK) */
    private Long id;

    /** student.id (학생 PK) */
    private Long studentId;

    /** student.name (학생 이름) */
    private String studentName;

    // -----------------------------
    // 학부(SCHOOL_STAGE)
    // -----------------------------
    /** student.school_stage (코드) */
    private String schoolStage;

    /** 공통코드(SCHOOL_STAGE)에서 가져온 한글 이름 */
    private String schoolStageName;

    // -----------------------------
    // 소속관(WORK_LOCATION)
    // -----------------------------
    /** student.work_location_code (코드) */
    private String workLocationCode;

    /** 공통코드(WORK_LOCATION)에서 가져온 한글 이름 */
    private String workLocationName;

    // -----------------------------
    // 학생 상태(STUDENT_STATUS)
    // -----------------------------
    /** student.status (코드) */
    private String status;

    /** 공통코드(STUDENT_STATUS)에서 가져온 한글 이름 */
    private String statusName;

    // -----------------------------
    // 보호자와의 관계(FAMILY_REL)
    // -----------------------------
    /** student_guardian_link.relation_code (코드) */
    private String relationCode;

    /** 공통코드(FAMILY_REL)에서 가져온 한글 이름 */
    private String relationName;

    // -----------------------------
    // 링크 플래그
    // -----------------------------
    private boolean primary;
    private boolean legalGuardian;
    private boolean receiveNotice;
    private boolean receiveBilling;
}
