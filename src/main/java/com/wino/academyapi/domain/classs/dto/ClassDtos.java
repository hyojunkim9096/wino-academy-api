// src/main/java/com/wino/academyapi/domain/classs/dto/ClassDtos.java
package com.wino.academyapi.domain.classs.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 반(클래스) / 과목 / 시간표 DTO (컨트롤러 <-> 클라이언트)
 * -----------------------------------------------------------------------------
 * - class_master.sort_order 반영
 * - 반 일괄 재정렬용 ReorderClassReq 추가
 * - ✅ 공통코드(gradeCode/classCode) 필드 추가
 * -----------------------------------------------------------------------------
 */
public class ClassDtos {

    /** 반 생성/수정 요청
     *  - sortOrder: 선택값. 미지정 시 신규 생성은 파티션 마지막 다음 값으로 자동 배정
     *  - ✅ NEW: gradeCode / classCode (공통코드 GRADE_*, CLASS_CODE)
     */
    public record ClassUpsertReq(
            @NotBlank String workLocationCode,
            @NotBlank String schoolStage,
            Long semesterId,
            // ✅ 공통코드
            String gradeCode,          // 예: E01/M02/H03
            String classCode,          // 예: G/C/P/S
            @NotBlank String code,
            @NotBlank String name,
            Integer sortOrder,          // (선택)
            Long homeroomTeacherId,
            Integer capacity,
            String status,
            String memo,
            Boolean useYn
    ) {}

    /** 반 응답
     *  - sortOrder 포함
     *  - ✅ NEW: gradeCode / classCode 포함
     */
    public record ClassRes(
            Long id, String workLocationCode, String schoolStage, Long semesterId,
            String gradeCode, String classCode,      // ✅ NEW
            String code, String name, Integer sortOrder,
            Long homeroomTeacherId, Integer capacity, String status, String memo, boolean useYn
    ) {}

    // 과목명 포함 응답
    public record ClassSubjectRes(
            Long id,
            Long classId,
            Long subjectId,
            String subjectName,
            Long teacherId,
            int sortOrder,
            boolean useYn
    ) {}

    public record AddClassSubjectReq(
            @NotNull Long subjectId,
            Long teacherId
    ) {}

    /** 반-과목 재정렬 요청 */
    public record ReorderReq(List<Long> classSubjectIdsInOrder) {}

    /** 반 일괄 재정렬 요청 (같은 지점×학부 파티션 내) */
    public record ReorderClassReq(List<Long> classIdsInOrder) {}

    /**
     * 단건 슬롯 추가 요청
     *  - 기존: startTime / endTime / room ...
     *  - 추가: startTimeCode / startTimeName (공통코드 기록)
     *  - roomId(FK) 추가 — DB/스냅샷(room_id)과 일치
     */
    public record TimeslotReq(
            @NotNull Long classSubjectId,
            @Min(1) @Max(7) int dayOfWeek,
            @NotBlank String startTime,
            @NotBlank String endTime,
            String room,
            Long roomId,                 // FK(선택)
            // (선택) 공통코드 스냅샷
            String startTimeCode,
            String startTimeName,
            String startDate,
            String endDate,
            Boolean useYn
    ) {}
}