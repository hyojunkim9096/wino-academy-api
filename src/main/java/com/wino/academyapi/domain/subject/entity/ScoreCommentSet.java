// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/entity/ScoreCommentSet.java
// ----------------------------------------------------------------------------
// 점수 코멘트 세트 (주간/학기, 과목 전체/항목 단위, 버전관리)
// - 최신 버전 1건을 자주 조회 → 복합 인덱스(idx_comment_set_latest)
// - srcTemplateCode: 템플릿 적용 출처 기록(감사/재적용 용이)
// ============================================================================

package com.wino.academyapi.domain.subject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name="score_comment_set",
        indexes = @Index(
                name="idx_comment_set_latest",
                columnList="subject_id, school_stage, scope_type, scope_ref_id, period_type, version"
        )
)
public class ScoreCommentSet {

    public enum ScopeType { OVERALL, SDL_ITEM, DT_ITEM }
    public enum PeriodType { WEEK, TERM }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="subject_id", nullable=false)
    private Long subjectId;                // FK(subject.id)

    @Column(name="school_stage", length=32, nullable=false)
    private String schoolStage;            // 학부 코드

    @Enumerated(EnumType.STRING)
    @Column(name="scope_type", nullable=false, length=16)
    private ScopeType scopeType;           // OVERALL/SDL_ITEM/DT_ITEM

    /** scope_type이 *_ITEM 일 때 subject_item.id */
    @Column(name="scope_ref_id")
    private Long scopeRefId;

    @Enumerated(EnumType.STRING)
    @Column(name="period_type", nullable=false, length=8)
    private PeriodType periodType;         // WEEK/TERM

    @Column(nullable=false)
    private int version = 1;               // 버전, 기본 1

    private LocalDateTime validFrom;       // 사용 시작 (선택)
    private LocalDateTime validTo;         // 사용 종료 (선택)

    @Column(length=255)
    private String memo;

    /** (선택) 템플릿 적용 출처 코드 → 템플릿 시스템과의 연결고리 */
    @Column(name="src_template_code", length=80)
    private String srcTemplateCode;

    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}