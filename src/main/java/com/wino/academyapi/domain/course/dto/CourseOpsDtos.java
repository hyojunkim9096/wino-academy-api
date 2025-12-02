package com.wino.academyapi.domain.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

public class CourseOpsDtos {

    @Data
    public static class PreviewReq {
        @NotBlank private String workLocationCode;
        @NotBlank private String schoolStage;
        @NotNull  private Long   semesterId;
    }

    @Getter
    @AllArgsConstructor
    public static class PreviewRes {
        private long   classCount;
        private long   subjectCount;
        private long   slotCount;
        private String lastSnapshotAt;
    }

    @Data
    public static class CloseReq {
        @NotBlank private String workLocationCode;
        @NotBlank private String schoolStage;
        @NotNull  private Long   semesterId;
        private String note;
    }

    @Data
    public static class RestoreReq {
        @NotBlank private String workLocationCode;
        @NotBlank private String schoolStage;
        @NotNull  private Long   semesterId;
        @NotNull  private Targets targets;
        private boolean preSnapshot = true;
        private String note;

        @Data
        public static class Targets {
            private boolean clazz    = true;
            private boolean subject  = true;
            private boolean timeslot = true;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class SimpleAck {
        private String message;
        private long   affectedClasses;
        private long   affectedSubjects;
        private long   affectedSlots;
    }
}