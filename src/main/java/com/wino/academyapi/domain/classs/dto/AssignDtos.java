package com.wino.academyapi.domain.classs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 담당/시간 일괄 저장 요청 DTO
 * - item 별 동작:
 *   A) clearSlot=true                             → 슬롯 삭제
 *   B) dayOfWeek+startTime(+room[+roomId])        → 슬롯 업서트(종료=시작+50)
 *      + (옵션) startTimeCode/startTimeName       → 공통코드 CLASS_TIME(code/name) 기록
 *   C) teacherId 지정 / clearTeacher=true         → 담당 교사 변경/초기화
 *   ※ B와 C는 함께 보낼 수 있음(같은 요청에서 시간/담당 둘 다 변경)
 *   ※ 아무 필드도 없으면(무동작) 서버에서 무시
 */
public class AssignDtos {

    @Data
    public static class SaveAssignmentsRequest {
        @NotNull
        private List<Item> items;

        @Data
        public static class Item {
            @NotNull private Long classSubjectId;

            // ─ 시간 슬롯 업서트 기준(둘 다 있어야 업서트로 판단)
            private Integer dayOfWeek;      // 1..7
            private String  startTime;      // "HH:mm"

            // ─ (선택) 공통코드 기록
            private String  startTimeCode;  // CLASS_TIME.code
            private String  startTimeName;  // CLASS_TIME.name

            private String  room;           // nullable (문자열 유지)
            // ✅ NEW: 강의실 FK도 함께 받기 (DDL: class_timeslot.room_id)
            private Long    roomId;         // nullable

            // ─ 슬롯 삭제
            private Boolean clearSlot;      // true면 해당 classSubject의 슬롯 전체 삭제

            // ─ 담당 교사 변경
            private Long    teacherId;      // null이면 '변경 없음'
            private Boolean clearTeacher;   // true면 teacherId = null 로 초기화
        }
    }

    public record ErrorResponse(String message) {}
}