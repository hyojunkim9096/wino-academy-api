// src/main/java/com/wino/academyapi/domain/student/entity/Student.java
package com.wino.academyapi.domain.student.entity;

import com.wino.academyapi.domain.file.entity.AttachFile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DDL: student 테이블 매핑
 *  - 로그인 관련 컬럼은 없음(계정은 end_user / end_user_student_map에서 관리)
 *  - profile_image_id는 AttachFile FK
 *  - school_id / updated_by 는 단순 숫자 FK(연결 엔티티 없이 Long으로 보관)
 *  - ✅ memo 컬럼 제거 (메모는 student_memo 테이블에서 관리)
 *  - ✅ DDL 반영: grade_group / grade_code 필드 추가 (트리거가 grade_label → 코드 자동 정규화)
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "student", indexes = {
        @Index(name = "idx_student_stage_loc", columnList = "school_stage, work_location_code, status"),
        @Index(name = "idx_student_name", columnList = "name"),
        @Index(name = "idx_student_school", columnList = "school_id"),
        @Index(name = "idx_student_push", columnList = "push_user_key"),
        // ✅ DDL 보강 인덱스
        @Index(name = "ix_student_grade", columnList = "grade_group, grade_code"),
        @Index(name = "ix_student_stage_grade", columnList = "school_stage, grade_group, grade_code")
})
public class Student {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_location_code", nullable = false, length = 32)
    private String workLocationCode;

    @Column(name = "school_stage", nullable = false, length = 32) // E/M/H 등
    private String schoolStage;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // PENDING/ACTIVE...

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "birthdate")
    private LocalDate birthdate;

    @Column(name = "school_id")
    private Long schoolId; // 외부(학교) FK는 숫자만 보관

    // ===================== 🔽 DDL 추가 필드 반영(정규화) 🔽 =====================
    /** 공통코드 그룹(GRADE_E/M/H) — 트리거가 grade_label을 보고 자동 설정 */
    @Column(name = "grade_group", length = 64)
    private String gradeGroup;

    /** 학년 코드(E01..H03) — 트리거가 grade_label을 보고 자동 설정 */
    @Column(name = "grade_code", length = 64)
    private String gradeCode;
    // ===================== 🔼 DDL 추가 필드 반영(정규화) 🔼 =====================

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

    @Column(name = "push_user_key", length = 255) // DDL은 255, 기존 120 → 255로 상향
    private String pushUserKey;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    /** 첨부파일 FK — 삭제 시 SET NULL 정책 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_id",
            foreignKey = @ForeignKey(name = "fk_student_profile_img"))
    private AttachFile profileImage;

    // ✅ memo 필드 제거 (student_memo 사용)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 트리거/뷰에서 참조하는 마지막 수정자(@app_user_id) */
    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    public void prePersist() {
        // 상태 기본값 — 서비스에서 PENDING을 기본으로 주입하지만, 안전망 유지
        if (status == null || status.isBlank()) status = "PENDING";
        // 연락 수단 일관성(연락처/이메일이 없으면 동의 플래그 자동 false)
        if (preferSms && (phone == null || phone.isBlank()))   preferSms = false;
        if (preferEmail && (email == null || email.isBlank())) preferEmail = false;
        // grade_group/grade_code는 DB 트리거가 grade_label 기준으로 보정하므로 여기선 건드리지 않음
    }

    @PreUpdate
    public void preUpdate() {
        // 상태 기본값 안전망
        if (status == null || status.isBlank()) status = "ACTIVE";
        // 연락 수단 일관성 유지
        if (preferSms && (phone == null || phone.isBlank()))   preferSms = false;
        if (preferEmail && (email == null || email.isBlank())) preferEmail = false;
        // grade_group/grade_code는 DB 트리거가 grade_label 기준으로 보정
    }
}