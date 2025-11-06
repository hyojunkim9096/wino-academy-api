package com.wino.academyapi.domain.semester.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 학기 엔티티 — DDL과 1:1 매핑
 * - UNIQUE (school_stage, code)
 * - INDEX  (school_stage, use_yn, sort_order)
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "semester",
        indexes = @Index(name="idx_semester_stage", columnList="school_stage, use_yn, sort_order"),
        uniqueConstraints = @UniqueConstraint(name="uk_semester_stage_code", columnNames={"school_stage","code"})
)
public class Semester {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학부 코드 (예: E/M/H) */
    @Column(name="school_stage", length=32, nullable=false)
    private String schoolStage;

    /** 학기 코드 (학부 내 유니크) */
    @Column(length=80, nullable=false)
    private String code;

    /** 학기명 */
    @Column(length=120, nullable=false)
    private String name;

    /** 학기 유형 (REGULAR, EXAM_PREP) */
    @Enumerated(EnumType.STRING)
    @Column(name="semester_type", length=20, nullable=false)
    private SemesterType semesterType = SemesterType.REGULAR;

    /** 기간 */
    private LocalDate startDate;
    private LocalDate endDate;

    /** 사용 여부 */
    @Column(name="use_yn", columnDefinition="TINYINT(1)")
    private boolean useYn = true;

    /** 정렬 우선순위(낮을수록 먼저) */
    @Column(name="sort_order", nullable=false)
    private int sortOrder = 0;

    /** 감사 필드 */
    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}