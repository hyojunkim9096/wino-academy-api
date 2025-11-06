// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/entity/Subject.java
// ----------------------------------------------------------------------------
// 과목/카테고리 트리 (DDL과 1:1 매핑)
// - isLeaf=true → 리프 과목(항목/코멘트 대상)
// - isLeaf=false → 카테고리(자식 subject만 가짐)
// - description: 카테고리 설정 JSON(예: {"text":"...","totalLimit":100})
// ============================================================================

package com.wino.academyapi.domain.subject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name="subject",
        indexes = @Index(name="idx_subject_stage_depth", columnList="school_stage, depth, parent_id, sort_order"),
        uniqueConstraints = @UniqueConstraint(name="uk_subject_stage_code", columnNames={"school_stage","code"})
)
public class Subject {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="school_stage", length=32, nullable=false)
    private String schoolStage;

    @Column(length=120, nullable=false)
    private String name;

    /** 리프 과목 주사용(카테고리는 null 가능) */
    @Column(length=80)
    private String code;

    /** (선택) 과목 평가항목 템플릿 출처 코드 */
    @Column(name="item_tpl_code", length=80)
    private String itemTplCode;

    /** 카테고리 설명/총점 제한(JSON 문자열) */
    @Lob
    private String description;

    @Column(name="is_leaf", columnDefinition="TINYINT(1)")
    private boolean isLeaf;

    /** 트리 깊이: 1/2/3 */
    @Column(nullable=false)
    private int depth;

    @Column(name="parent_id")
    private Long parentId;          // FK(subject.id) - 앱 레벨 관리

    @Column(name="sort_order", nullable=false)
    private int sortOrder;

    @Column(name="use_yn", columnDefinition="TINYINT(1)")
    private boolean useYn = true;

    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}