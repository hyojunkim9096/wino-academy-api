// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/dto/SubjectDtos.java
// ----------------------------------------------------------------------------
// API I/O DTO 묶음 (record 사용으로 불변·간결)
// - description: 카테고리일 때 {"text":"...", "totalLimit":100} JSON 문자열
// - Validation 애노테이션으로 기본 입력 검증(추가 검증은 서비스에서)
// ============================================================================

package com.wino.academyapi.domain.subject.dto;

import com.wino.academyapi.domain.subject.entity.ScoreCommentSet;
import com.wino.academyapi.domain.subject.entity.SubjectItem;
import jakarta.validation.constraints.*;

import java.util.List;

public class SubjectDtos {

    /** 과목(카테고리/리프) 업서트 요청 DTO */
    public record SubjectUpsertReq(
            @NotBlank String schoolStage,
            @NotBlank String name,
            @Min(1) @Max(3) int depth,
            Long parentId,
            @NotNull Integer sortOrder,
            Boolean isLeaf,          // null 허용 → 서비스에서 기본 false 처리 가능
            String code,             // 리프 위주로 사용, 카테고리는 null 가능
            String description,      // 카테고리 설명/옵션(JSON 문자열)
            Boolean useYn            // null이면 서비스에서 true 기본값 처리 권장
    ) {}

    /** 과목 응답 DTO */
    public record SubjectRes(
            Long id, String schoolStage, String name, String code, boolean isLeaf,
            int depth, Long parentId, int sortOrder, boolean useYn, String description
    ) {}

    /** 평가 항목 저장 요청 DTO */
    public record SubjectItemReq(
            Long id,                             // 전체 재저장 정책이라 없어도 됨
            @NotNull Long subjectId,
            SubjectItem.Kind kind,               // SDL/DT
            @NotBlank String name,
            @Min(0) int maxScore,
            @NotNull Integer sortOrder,
            Boolean useYn
    ) {}

    /** 평가 항목 응답 DTO */
    public record SubjectItemRes(
            Long id, Long subjectId, SubjectItem.Kind kind,
            String name, int maxScore, int sortOrder, boolean useYn
    ) {}

    /** 점수 밴드(구간) DTO */
    public record BandDto(
            Long id, String label, int minScore, int maxScore,
            String commentTemplate,
            boolean notifySms, boolean notifyEmail, boolean notifyPush,
            int sortOrder
    ) {}

    /** 점수 코멘트 세트 업서트 요청 DTO */
    public record BandSetUpsertReq(
            @NotNull Long subjectId,
            @NotBlank String schoolStage,
            ScoreCommentSet.ScopeType scopeType,   // OVERALL/SDL_ITEM/DT_ITEM
            Long scopeRefId,                       // *_ITEM일 때 대상 subject_item.id
            ScoreCommentSet.PeriodType periodType, // WEEK/TERM
            Integer version,                       // null이면 서비스에서 최신+1 등 정책
            String memo,
            List<BandDto> bands                    // null/빈 배열 가능(세트만 업서트)
    ) {}

    /** 점수 코멘트 세트 응답 DTO (밴드 포함 가능) */
    public record BandSetRes(
            Long id, Long subjectId, String schoolStage,
            ScoreCommentSet.ScopeType scopeType, Long scopeRefId,
            ScoreCommentSet.PeriodType periodType, int version,
            String memo, List<BandDto> bands
    ) {}

    /** ✅ 배치 정렬 저장 요청 DTO */
    public record SubjectOrderReq(
            @NotBlank String schoolStage,
            Long parentId,                 // null → Depth1, not null → 해당 parent의 Children
            @NotEmpty List<@NotNull Long> ids  // 화면에 보이는 순서대로의 subject.id 배열
    ) {}
}