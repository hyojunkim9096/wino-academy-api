package com.wino.academyapi.domain.tuition.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "tuition_category",
        indexes = {
                @Index(name = "ix_tuition_category_parent", columnList = "parent_id"),
                @Index(name = "ix_tuition_category_stage",  columnList = "school_stage"),
                @Index(name = "ix_tuition_category_grade",  columnList = "grade_group, grade_code")
        }
)
public class TuitionCategory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 'E','M','H' */
    @Column(name = "school_stage", length = 1, nullable = false)
    private String schoolStage;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String code; // (stage, code) unique는 DB에서 보장

    @Lob
    private String description;

    @Column(nullable = false)
    private Integer depth = 1;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_leaf", nullable = false)
    private boolean leaf = false;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    // ★ 공통코드 연결(문자열 보관; 하드 FK는 DB가 관리)
    @Column(name = "grade_group", length = 64)
    private String gradeGroup;   // 'GRADE_E' | 'GRADE_M' | 'GRADE_H'

    @Column(name = "grade_code", length = 64)
    private String gradeCode;    // 'E01'..'H03'

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}