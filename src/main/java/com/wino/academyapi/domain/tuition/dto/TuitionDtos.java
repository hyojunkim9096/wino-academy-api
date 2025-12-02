// src/main/java/com/wino/academyapi/domain/tuition/dto/TuitionDtos.java
package com.wino.academyapi.domain.tuition.dto;

import java.math.BigDecimal;
import java.util.List;

public class TuitionDtos {

    public record CategoryUpsertReq(
            String schoolStage,    // 'E' | 'M' | 'H'
            String name,
            String code,
            String description,
            Integer depth,
            Long parentId,
            Integer sortOrder,
            Boolean isLeaf,
            Boolean useYn,
            String gradeGroup,     // 'GRADE_E' | 'GRADE_M' | 'GRADE_H'
            String gradeCode       // 'E01'..'H03'
    ) {}

    public record CategoryRes(
            Long id,
            String schoolStage,
            String name,
            String code,
            String description,
            Integer depth,
            Long parentId,
            Integer sortOrder,
            boolean isLeaf,
            boolean useYn,
            String gradeGroup,
            String gradeCode
    ) {}

    public record ReorderReq(String stage, Long parentId, List<Long> orderedIds) {}

    public record PriceRowReq(String unit, BigDecimal price, Boolean enabled, String memo, Integer sortOrder) {}
    public record PriceRowRes(Long id, String unit, BigDecimal price, boolean enabled, String memo, Integer sortOrder) {}

    public record ActivePriceRes(
            Long id, Long categoryId, String categoryPath, String categoryName,
            String unit, String name, BigDecimal price, Integer sortOrder, boolean enabled
    ) {}

    public record GradeCodeRes(String code, String name) {}
}