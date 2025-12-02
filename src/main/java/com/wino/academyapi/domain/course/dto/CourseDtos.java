package com.wino.academyapi.domain.course.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 반(Course) 관련 DTO
 * ✅ ClassDtos -> CourseDtos 리네이밍
 */
public class CourseDtos {

    public record CourseUpsertReq(
            @NotBlank String workLocationCode,
            @NotBlank String schoolStage,
            Long semesterId,
            String gradeCode,
            String classCode,
            @NotBlank String code,
            @NotBlank String name,
            Integer sortOrder,
            Long homeroomTeacherId,
            Integer capacity,
            String status,
            String memo,
            Boolean useYn
    ) {}

    public record CourseRes(
            Long id, String workLocationCode, String schoolStage, Long semesterId,
            String gradeCode, String classCode,
            String code, String name, Integer sortOrder,
            Long homeroomTeacherId, Integer capacity, String status, String memo, boolean useYn
    ) {}

    public record CourseSubjectRes(
            Long id,
            Long courseId,   // 변경: classId -> courseId
            Long subjectId,
            String subjectName,
            Long teacherId,
            int sortOrder,
            boolean useYn
    ) {}

    public record AddCourseSubjectReq(
            @NotNull Long subjectId,
            Long teacherId
    ) {}

    public record ReorderReq(List<Long> courseSubjectIdsInOrder) {}

    public record ReorderCourseReq(List<Long> courseIdsInOrder) {}

    public record TimeslotReq(
            @NotNull Long courseSubjectId, // 변경: classSubjectId -> courseSubjectId
            @Min(1) @Max(7) int dayOfWeek,
            @NotBlank String startTime,
            @NotBlank String endTime,
            String room,
            Long roomId,
            String startTimeCode,
            String startTimeName,
            String startDate,
            String endDate,
            Boolean useYn
    ) {}
}