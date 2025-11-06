// src/main/java/com/wino/academyapi/domain/room/dto/RoomDtos.java
package com.wino.academyapi.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * 강의실(Room) 관리/배정 DTO 묶음
 * - 요청/응답에 필요한 타입을 한 곳에 모음
 */
public class RoomDtos {

    /* =========================================================================
       공용 Page 응답
       ========================================================================= */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class PageRes<T> {
        private List<T> items;
        private long total;
        private int page;
        private int size;
    }

    /* =========================================================================
       목록 조회 요청
       ========================================================================= */
    @Getter @Setter
    public static class ListReq {
        @NotBlank private String workLocationCode;
        private String keyword;
        private String status;
        private String useYn;
        @Min(1) private int page = 1;
        @Min(1) @Max(2000) private int size = 20; // 정렬 모드에서 한 번에 많이 로딩할 수 있게 상향
    }

    /* =========================================================================
       목록 Row
       ========================================================================= */
    @Getter @Setter @AllArgsConstructor
    public static class RoomRow {
        private Long id;
        private String workLocationCode;
        private String code;
        private String name;
        private Integer capacity;
        private Integer maxParallel;
        private String status;
        private String memo;
        private String useYn;
        private String createdAt;
        private String updatedAt;
    }

    /* =========================================================================
       생성 요청
       ========================================================================= */
    @Getter @Setter
    public static class SaveReq {
        @NotBlank private String workLocationCode;
        @NotBlank private String code;
        @NotBlank private String name;
        private Integer capacity;
        @Min(1) @Max(9) private int maxParallel = 1;
        @Pattern(regexp = "OPEN|CLOSED") private String status = "OPEN";
        private String memo;
        @Pattern(regexp = "0|1") private String useYn = "1";
    }

    /* =========================================================================
       수정 요청
       ========================================================================= */
    @Getter @Setter
    public static class UpdateReq {
        // 바디에는 읽기전용으로 두고, 컨트롤러에서 PathVariable로 주입
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private Long id;

        @NotBlank private String workLocationCode;
        @NotBlank private String name;
        private Integer capacity;
        @Min(1) @Max(9) private int maxParallel = 1;
        @Pattern(regexp = "OPEN|CLOSED") private String status = "OPEN";
        private String memo;
        @Pattern(regexp = "0|1") private String useYn = "1";
    }

    /* =========================================================================
       사용여부 토글
       ========================================================================= */
    @Getter @Setter
    public static class ToggleUseReq {
        @NotNull private Long id;
        @NotBlank private String workLocationCode;
        @Pattern(regexp = "0|1") private String useYn;
    }

    /* =========================================================================
       정렬 저장 요청
       ========================================================================= */
    @Getter @Setter
    public static class ReorderReq {
        @NotBlank private String workLocationCode;
        @NotEmpty private List<Long> orderedIds; // 최종 원하는 순서대로
    }

    /* =========================================================================
       배정 PREVIEW 요청
       ========================================================================= */
    @Getter @Setter
    public static class AssignPreviewReq {
        @NotBlank private String workLocationCode;
        @Min(1) @Max(7) private int dayOfWeek;
        @NotBlank private String classTimeCode;
    }

    /* =========================================================================
       방 가용 현황
       ========================================================================= */
    @Getter @Setter @AllArgsConstructor
    public static class RoomUsage {
        private Long roomId;        // room_master.id
        private String roomCode;    // room_master.code
        private String roomName;    // room_master.name
        private Integer capacity;   // room_master.capacity
        private Integer maxParallel;// room_master.max_parallel
        private String status;      // room_master.status
        private String useYn;       // room_master.use_yn
        private Integer assignedCount; // 현재 시간대 배정 수
        private Integer available;     // max_parallel - 배정 수
    }

    /* =========================================================================
       시간표 Row (PREVIEW 표출용)
       - room: 현재 배정된 방의 "이름"(null 가능)
       ========================================================================= */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class TimeslotRow {
        private Long timeslotId;
        private Long classId;
        private String classCode;
        private String className;     // 반 이름
        private String subjectName;
        private Integer dayOfWeek;
        private String classTimeCode;
        private String startTime;
        private String endTime;
        private String room;          // 표시용: 현재 배정된 방 "이름"
        private Long roomId;          // 현재 배정된 방 id(null 가능)
    }

    /* =========================================================================
       배정 EXEC 요청
       ========================================================================= */
    @Getter @Setter
    public static class AssignReq {
        @NotBlank private String workLocationCode;
        @Min(1) @Max(7) private int dayOfWeek;
        @NotBlank private String classTimeCode;
        private boolean dryRun = false;

        @NotEmpty private List<Item> items;

        @Getter @Setter @AllArgsConstructor @NoArgsConstructor
        public static class Item {
            @NotNull private Long timeslotId;
            // null → 해제, 아니면 해당 roomId 로 배정
            private Long roomId;
        }
    }

    /* =========================================================================
       배정 결과
       ========================================================================= */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class AssignAck {
        private String message;

        @JsonProperty("updated")
        private int updatedCount;

        @JsonProperty("cleared")
        private int clearedCount;
    }

    /* =========================================================================
       PREVIEW 응답 DTO (리스트 2개)
       ========================================================================= */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class AssignPreviewRes {
        private List<RoomUsage> rooms;
        private List<TimeslotRow> timeslots;
    }

    /* 하루 스케줄 요청 */
    @Getter @Setter
    public static class DayScheduleReq {
        @NotBlank private String workLocationCode;
        @Min(1) @Max(7) private int day;
        @NotEmpty private List<String> classTimeCodes;
    }

    /* 컬럼 헤더용 (가벼운 방 정보) */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class RoomHead {
        private Long id;
        private String code;
        private String name;
    }

    /* 하루 그리드의 셀 데이터 */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class DaySlot {
        private Long roomId;          // 키: roomId
        private String classTimeCode; // 키: 교시 코드('1630' 등)
        private String className;     // 표시용
        private String teacherName;   // 선택
        private String memo;          // 선택
    }

    /* 하루 스케줄 응답 */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class DayScheduleRes {
        private List<RoomHead> rooms; // 프론트가 r.id/r.code/r.name만 씀
        private List<DaySlot> slots;  // 셀 데이터
    }
}