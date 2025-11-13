// src/main/java/com/wino/academyapi/domain/student/entity/StudentHist.java
package com.wino.academyapi.domain.student.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 학생 변경 이력 (student_hist)
 * - DDL 트리거에 의해 자동으로 채워집니다.
 * - ✅ [수정] gender 필드 추가
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Immutable //
@Table(name = "student_hist", indexes = {
        @Index(name = "ix_student_hist_ev", columnList = "event_type, event_at"),
        @Index(name = "ix_student_hist_grade", columnList = "grade_group, grade_code")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_student_hist_ref_ver", columnNames = {"ref_id", "version"})
})
public class StudentHist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ref_id", nullable = false)
    private Long refId;
    @Column(nullable = false)
    private int version;
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;
    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;
    @Column(name = "event_by")
    private Long eventBy;

    // --- (Snapshot Columns) ---
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

    // ✅ [신규] gender 컬럼 추가
    @Column(name = "gender", length = 1)
    private String gender;

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
    @Column(name = "profile_image_id")
    private Long profileImageId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "updated_by")
    private Long updatedBy;
}