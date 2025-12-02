// src/main/java/com/wino/academyapi/domain/semester/dto/SemesterDtos.java
package com.wino.academyapi.domain.semester.dto;

import com.wino.academyapi.domain.semester.entity.SemesterType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * 학기 DTO
 * - DDL: DATE ↔ LocalDate, BOOLEAN ↔ boolean
 */
public class SemesterDtos {

    /**
     * 목록/상세 응답
     */
    public record SemesterRes(
            Long id,
            String schoolStage,
            String code,
            String name,
            SemesterType semesterType,
            LocalDate startDate,
            LocalDate endDate,
            boolean useYn,
            int sortOrder
    ) {}

    /**
     * 생성/수정 요청
     */
    public record SemesterUpsertReq(
            @NotBlank String schoolStage,
            @NotBlank String code,
            @NotBlank String name,
            @NotNull  SemesterType semesterType,   // ★ 추가
            @NotNull  LocalDate startDate,
            @NotNull  LocalDate endDate,
            Integer   sortOrder,
            Boolean   useYn
    ) {}
}