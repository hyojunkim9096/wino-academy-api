package com.wino.academyapi.domain.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 반(Course) 엔티티 - (구 ClassMaster)
 * ✅ 리네이밍: ClassMaster -> Course
 * ✅ 테이블명 유지: class_master (기존 데이터 보존)
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "class_master",
        indexes = {
                @Index(name="idx_class_stage", columnList="work_location_code, school_stage, use_yn"),
                @Index(name="idx_class_semester", columnList="semester_id")
        },
        uniqueConstraints = @UniqueConstraint(name="uk_class_location_code", columnNames={"work_location_code","code"})
)
public class Course {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="work_location_code", length=32, nullable=false)
    private String workLocationCode;

    @Column(name="school_stage", length=32, nullable=false)
    private String schoolStage;

    @Column(name="semester_id")
    private Long semesterId;

    // 공통코드 저장용
    @Column(name="grade_code", length=32)
    private String gradeCode;

    @Column(name="class_code", length=32)
    private String classCode;

    @Column(length=80, nullable=false)
    private String code;

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

    @CreationTimestamp
    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}