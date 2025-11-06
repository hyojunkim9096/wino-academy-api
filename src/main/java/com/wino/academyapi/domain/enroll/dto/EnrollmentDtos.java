// src/main/java/com/wino/academyapi/domain/enroll/dto/EnrollmentDtos.java
package com.wino.academyapi.domain.enroll.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 반 배정 DTO 묶음
 *
 * ✅ 2025-10 Core DDL 반영
 *   - 메인/교차: classStatusCode (MAIN | CROSS)
 *   - 수강 요일: CSV가 아니라 student_enroll_timeslot 매핑을 통해
 *     DB 트리거가 student_class_enrollment.attend_days_mask(INT)로 자동 집계
 *
 * ✅ 서비스/컨트롤러 정합성
 *   - (요청) 생성/수정 시, 옵션으로 timeslotIds 전체 치환을 지원
 *     · 전달되면 현재 매핑을 싹 교체(빈 배열이면 전부 제거)
 *     · 미전달(null)이면 기존 매핑 유지
 *   - (응답) Summary에 attendDaysMask(정수), attendDaysLabel(표시용 라벨),
 *            attendDaysUpdatedAt(집계 갱신 시각),
 *            classStatusName(공통코드 라벨) 포함
 */
public class EnrollmentDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrollmentSummary {
        private Long id;
        private Long studentId;
        private Long classId;
        private LocalDate enrolledAt;
        private LocalDate leftAt;
        private String status;   // ENROLL_STATUS (ACTIVE/STOP/MOVE/...)
        private String memo;

        /** MAIN | CROSS (DDL: student_class_enrollment.class_status_code) */
        private String classStatusCode;

        /** ✅ 공통코드 라벨 (예: "메인", "교차") — 뷰: student_enrollment_ext_v.class_status_name */
        private String classStatusName;

        /**
         * 수강 요일 비트마스크 (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64)
         * - student_enroll_timeslot 기준으로 DB 트리거가 자동 집계
         * - 예) 월/목이면 1 + 8 = 9
         */
        private Integer attendDaysMask;

        /**
         * 표시용 라벨 (예: "월,수,금")
         * - 확장 뷰(student_enrollment_ext_v) 또는 서비스 보조 로직으로 채워짐
         */
        private String attendDaysLabel;

        /**
         * 수강 요일 집계 갱신 시각
         * - DDL: student_class_enrollment.attend_days_updated_at
         */
        private LocalDateTime attendDaysUpdatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrollmentCreateRequest {
        /** 필수: 배정할 반 ID */
        private Long classId;

        /** 필수: 배정 시작일 */
        private LocalDate enrolledAt;

        /** 선택: ENROLL_STATUS (기본 ACTIVE) */
        private String status;

        /** 선택: 메모 */
        private String memo;

        /** 선택: MAIN | CROSS — 교차 수업 여부 (기본 MAIN) */
        private String classStatusCode;

        /**
         * 🆕 (옵션) 타임슬롯 매핑 전체 치환
         * - 전달되면 해당 배열로 매핑을 싹 교체(빈 배열이면 모두 제거)
         * - null이면 기존 매핑 유지
         * - 유효성/집계는 DB 트리거가 담당
         */
        private List<Long> timeslotIds;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrollmentUpdateRequest {
        /** 선택: 배정 종료일 */
        private LocalDate leftAt;

        /** 선택: ENROLL_STATUS */
        private String status;

        /** 선택: 메모 */
        private String memo;

        /** 선택: MAIN | CROSS — 교차 수업 여부 */
        private String classStatusCode;

        /**
         * 🆕 (옵션) 타임슬롯 매핑 전체 치환
         * - 전달되면 해당 배열로 매핑을 싹 교체(빈 배열이면 모두 제거)
         * - null이면 기존 매핑 유지
         */
        private List<Long> timeslotIds;
    }
}