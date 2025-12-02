package com.wino.academyapi.domain.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 담당/시간 일괄 저장 DTO (기존 유지)
 * 필드명 classSubjectId 등은 프론트 호환을 위해 유지하거나
 * 필요 시 courseSubjectId로 변경 가능 (여기선 호환성을 위해 유지 권장)
 */
public class AssignDtos {

    @Data
    public static class SaveAssignmentsRequest {
        @NotNull
        private List<Item> items;

        @Data
        public static class Item {
            @NotNull private Long classSubjectId; // 프론트 호환 유지

            private Integer dayOfWeek;
            private String  startTime;
            private String  startTimeCode;
            private String  startTimeName;
            private String  room;
            private Long    roomId;
            private Boolean clearSlot;
            private Long    teacherId;
            private Boolean clearTeacher;
        }
    }
}