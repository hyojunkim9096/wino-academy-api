// src/main/java/com/wino/academyapi/domain/student/entity/Student.java
package com.wino.academyapi.domain.student.entity;

import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.domain.enduser.entity.EndUserStudentMap;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set; // ✅ [신규]

/**
 * DDL: student 테이블 매핑
 * - ✅ [수정] 1:1 매핑 (endUserMap) 추가
 * - ✅ [수정] 'gender' 필드 추가
 * - ✅ [신규] 'siblings' (형제) 연관관계 추가
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "student", indexes = {
        @Index(name = "idx_student_stage_loc", columnList = "school_stage, work_location_code, status"),
        @Index(name = "idx_student_name", columnList = "name"),
        @Index(name = "idx_student_school", columnList = "school_id"),
        @Index(name = "idx_student_push", columnList = "push_user_key"),
        @Index(name = "ix_student_grade", columnList = "grade_group, grade_code"),
        @Index(name = "ix_student_stage_grade", columnList = "school_stage, grade_group, grade_code")
})
public class Student {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_location_code", nullable = false, length = 32)
    private String workLocationCode;
    @Column(name = "school_stage", nullable = false, length = 32)
    private String schoolStage;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "birthdate")
    private LocalDate birthdate;

    // ✅ [신규] DDL에 추가된 gender 컬럼
    @Column(name = "gender", length = 1)
    private String gender; // m, f, o

    @Column(name = "school_id")
    private Long schoolId;
    @Column(name = "grade_group", length = 64)
    private String gradeGroup;
    @Column(name = "grade_code", length = 64)
    private String gradeCode;
    @Column(name = "grade_label", length = 40)
    private String gradeLabel;
    @Column(name = "phone", length = 20)
    private String phone;
    @Column(name = "email", length = 160)
    private String email;
    @Column(name = "prefer_sms", nullable = false)
    private boolean preferSms;
    @Column(name = "prefer_email", nullable = false)
    private boolean preferEmail;
    @Column(name = "prefer_push", nullable = false)
    private boolean preferPush;
    @Column(name = "push_user_key", length = 255)
    private String pushUserKey;
    @Column(name = "postal_code", length = 10)
    private String postalCode;
    @Column(name = "address", length = 255)
    private String address;
    @Column(name = "detail_address", length = 255)
    private String detailAddress;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_id",
            foreignKey = @ForeignKey(name = "fk_student_profile_img"))
    private AttachFile profileImage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "updated_by")
    private Long updatedBy;

    // ✅ [신규] 엔드유저 계정 매핑 (1:1)
    @OneToOne(mappedBy = "student", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private EndUserStudentMap endUserMap;

    // ✅ [신규] 형제 관계 (N:M, self-referencing)
    //
    @OneToMany(mappedBy = "low", fetch = FetchType.LAZY)
    private Set<StudentSibling> siblingsAsLow;

    @OneToMany(mappedBy = "high", fetch = FetchType.LAZY)
    private Set<StudentSibling> siblingsAsHigh;

    @PrePersist
    public void prePersist() {
        if (status == null || status.isBlank()) status = "PENDING";
        if (preferSms && (phone == null || phone.isBlank()))   preferSms = false;
        if (preferEmail && (email == null || email.isBlank())) preferEmail = false;
    }

    @PreUpdate
    public void preUpdate() {
        if (status == null || status.isBlank()) status = "ACTIVE";
        if (preferSms && (phone == null || phone.isBlank()))   preferSms = false;
        if (preferEmail && (email == null || email.isBlank())) preferEmail = false;
    }
}