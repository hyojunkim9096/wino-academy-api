package com.wino.academyapi.domain.course.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 반-과목 매핑 엔티티 - (구 ClassSubject)
 * ✅ 리네이밍: ClassSubject -> CourseSubject
 * ✅ 테이블명 유지: class_subject
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name="class_subject",
        indexes = {
                @Index(name="idx_cs_sort", columnList="class_id, sort_order"),
                @Index(name="idx_cs_teacher", columnList="teacher_id")
        },
        uniqueConstraints = @UniqueConstraint(name="uk_class_subject", columnNames={"class_id","subject_id"})
)
public class CourseSubject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DB 컬럼명 'class_id' 유지, 자바 필드명 'courseId'로 변경
    @Column(name="class_id", nullable=false)
    private Long courseId;

    @Column(name="subject_id", nullable=false)
    private Long subjectId;

    @Column(name="teacher_id")
    private Long teacherId;

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