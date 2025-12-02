// src/main/java/com/wino/academyapi/domain/timetable/dto/TimetableDtos.java
package com.wino.academyapi.domain.timetable.dto;

import lombok.*;

/**
 * 시간표 API DTO
 * - teacherName       : 전체 교사 조회/리스트/그리드에서 교사명 표기에 사용
 * - workLocationCode  : 이벤트가 속한 '관 코드' (프론트에서 공통코드로 이름 매핑해 "[관이름]" 표기)
 */
public class TimetableDtos {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EventRes {
        private Integer dayOfWeek;        // 1~7
        private String  start;            // 'HH:mm'
        private String  end;              // 'HH:mm'
        private String  title;            // 반 이름 등 상단 타이틀
        private String  subtitle;         // 과목 / 강의실
        private String  teacherName;      // (옵션) 교사명
        private String  workLocationCode; // (추가) 관 코드 (예: 'N','W','Q'…)
    }
}