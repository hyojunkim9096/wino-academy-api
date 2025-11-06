package com.wino.academyapi.domain.classs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 반(클래스) — class_master
 *  - sort_order 컬럼 추가: 지점×학부 파티션 내 표시/정렬 순서
 *  - ✅ NEW: grade_code / class_code 추가 (공통코드 GRADE_*, CLASS_CODE 저장)
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "class_master",
        indexes = {
                @Index(name="idx_class_stage", columnList="work_location_code, school_stage, use_yn"),
                @Index(name="idx_class_semester", columnList="semester_id"),
                // 주의: 실제 인덱스 ix_class_order(work_location_code, school_stage, sort_order, id)는
                // DDL에서 생성. 여기서는 선언만 참고용으로 유지.
        },
        uniqueConstraints = @UniqueConstraint(name="uk_class_location_code", columnNames={"work_location_code","code"})
)
public class ClassMaster {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="work_location_code", length=32, nullable=false)
    private String workLocationCode;

    @Column(name="school_stage", length=32, nullable=false)
    private String schoolStage;

    @Column(name="semester_id")
    private Long semesterId;

    // ✅ NEW: 공통코드 저장용
    @Column(name="grade_code", length=32)   // 예: E01, M02, H03 ...
    private String gradeCode;

    @Column(name="class_code", length=32)   // 예: G/C/P/S (CLASS_CODE)
    private String classCode;

    @Column(length=80, nullable=false)
    private String code;

    /** 정렬 순서 (0..n). null 허용 시 정렬 계산이 애매하므로 NOT NULL 권장 */
    @Column(name="sort_order", nullable=false)
    private Integer sortOrder = 0;

    @Column(length=120, nullable=false)
    private String name;

    @Column(name="homeroom_teacher_id")
    private Long homeroomTeacherId;

    private Integer capacity;

    @Column(length=16, nullable=false)
    private String status = "OPEN";

    @Lob
    private String memo;

    @Column(name="use_yn", columnDefinition="TINYINT(1)")
    private boolean useYn = true;

    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}