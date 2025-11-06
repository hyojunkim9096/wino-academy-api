// src/main/java/com/wino/academyapi/domain/classs/dto/ClassOpsDtos.java
package com.wino.academyapi.domain.classs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/** 반 운영 작업(미리보기 / 스냅샷+마감 / 복원) DTO */
public class ClassOpsDtos {

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
        private String lastSnapshotAt; // NULL 가능 (YYYY-MM-DD HH:mm:ss)
    }

    /** 학기 마감(스냅샷 + 초기화) 요청 DTO */
    @Data
    public static class CloseReq {
        @NotBlank private String workLocationCode;
        @NotBlank private String schoolStage;
        @NotNull  private Long   semesterId;
        private String note; // event_note
    }

    /** 스냅샷 복원(선택 항목) 요청 DTO */
    @Data
    public static class RestoreReq {
        @NotBlank private String workLocationCode;
        @NotBlank private String schoolStage;
        @NotNull  private Long   semesterId;

        // 프론트 { class, subject, timeslot } → 서버 { clazz, subject, timeslot }
        @NotNull  private Targets targets;

        // 되돌리기 "직전" 현재 상태를 SNAP으로 남길지 여부(권장: true)
        //  - true  : 현재 상태를 SNAP으로 먼저 저장 → 이후 전체 복원
        //  - false : 바로 복원(기존 동작과 동일)
        private boolean preSnapshot = true;

        private String note; // event_note

        @Data
        public static class Targets {
            private boolean clazz    = true;  // 반/담임
            private boolean subject  = true;  // 과목 담당
            private boolean timeslot = true;  // 시간표
        }
    }

    /** 단순 결과 요약 */
    @Getter
    @AllArgsConstructor
    public static class SimpleAck {
        private String message;
        private long   affectedClasses;
        private long   affectedSubjects;
        private long   affectedSlots;   // 시간표는 del+ins 합계로 리턴
    }
}