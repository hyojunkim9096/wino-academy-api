package com.wino.academyapi.domain.enroll.dto;

import lombok.*;

import java.time.LocalTime;

/**
 * 교차수업 후보 타임슬롯 DTO
 * - EnrollmentSuggestService 의 Native SQL 결과를 그대로 매핑
 * - classTimeCode(예: "1720")와 classTimeLabel(예: "17:20") 제공
 * - ✅ 과목(subjectName/subjectCode) 포함
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimeslotSuggestion {
    /** 반 식별자 */
    private Long classId;
    /** 반 코드 */
    private String classCode;
    /** 반 이름 */
    private String className;

    /** 타임슬롯 식별자 */
    private Long timeslotId;
    /** 요일(1=월 ... 7=일) */
    private int dayOfWeek;
    /** 시작 시각 */
    private LocalTime startTime;
    /** 종료 시각 */
    private LocalTime endTime;

    /** 코드(예: "1720") — ts.class_time_code */
    private String classTimeCode;

    /** 라벨(예: "17:20") — ts.class_time_label */
    private String classTimeLabel;

    /** ✅ 과목명 */
    private String subjectName;
    /** ✅ 과목코드(없으면 null 가능) */
    private String subjectCode;
}