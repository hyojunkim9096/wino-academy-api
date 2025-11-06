// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/entity/SubjectItem.java
// ----------------------------------------------------------------------------
// 리프 과목 평가 항목(SDL/DT)
// - 한 과목에 여러 개, 정렬(sortOrder)로 순서 보장
// ============================================================================

package com.wino.academyapi.domain.subject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name="subject_item",
        indexes = @Index(name="idx_subject_item", columnList="subject_id, kind, sort_order")
)
public class SubjectItem {

    public enum Kind { SDL, DT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="subject_id", nullable=false)
    private Long subjectId;             // FK(subject.id), isLeaf=true 대상

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=8)
    private Kind kind;                  // SDL/DT

    @Column(length=80, nullable=false)
    private String name;

    @Column(name="max_score", nullable=false)
    private int maxScore;

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