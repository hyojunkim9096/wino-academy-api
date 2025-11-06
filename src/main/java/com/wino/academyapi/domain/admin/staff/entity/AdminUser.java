// src/main/java/com/wino/academyapi/domain/admin/staff/entity/AdminUser.java
package com.wino.academyapi.domain.admin.staff.entity;

import com.wino.academyapi.domain.file.entity.AttachFile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 관리자(직원/선생님) 엔티티
 * - role/status를 공통코드의 "문자열 code"로 사용
 *   · role  : ROLE_* 형태 (예: ROLE_STAFF, ROLE_STAFF_INFO, ROLE_SYSTEM_ADMIN ...)
 *   · status: ACTIVE / TEMPORARY / INACTIVE / LOCKED
 * - 로그인 실패/잠금/해제 정책 포함
 * - 프로필 이미지: attach_file 테이블과 FK 연결(profile_image_id)
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "admin_user_info", indexes = {
        @Index(name = "idx_work_location", columnList = "work_location"),
        @Index(name = "idx_admin_status", columnList = "status"),
        @Index(name = "idx_admin_locked_until", columnList = "account_locked_until"),
        @Index(name = "idx_admin_last_login", columnList = "last_login"),
        @Index(name = "uk_admin_user_user_id", columnList = "user_id", unique = true)
})
public class AdminUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디(고유) */
    @Column(name = "user_id", nullable = false, unique = true, length = 120)
    private String userId;

    /** 암호화된 비밀번호 */
    @Column(name = "password", nullable = false, length = 200)
    private String password;

    /** 이름 */
    @Column(name = "user_name", nullable = false, length = 80)
    private String userName;

    /** 연락처/비상연락처 */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;

    /** 이메일(고유) */
    @Column(name = "email", unique = true, length = 160)
    private String email;

    /** 주소 */
    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    /** 소속 관 (공통코드 WORK_LOCATION: N/W 등, 기본값 N(나루관)) */
    @Column(name = "work_location", length = 100)
    private String workLocation;

    /** 직원 구분(기존 enum 유지; 필요 시 String 전환 가능) */
    @Enumerated(EnumType.STRING)
    @Column(name = "employee_type", nullable = false, length = 40)
    private EmployeeType employeeType;

    /** 권한 코드(공통코드 ROLE의 code). 예: ROLE_STAFF, ROLE_STAFF_INFO, ROLE_SYSTEM_ADMIN */
    @Column(name = "role", nullable = false, length = 40)
    private String role;

    /** 계정 상태 코드(공통코드 ACCOUNT_STATUS: ACTIVE/TEMPORARY/INACTIVE/LOCKED) */
    @Column(name = "status", nullable = false, length = 40)
    private String status;

    /** 생성/수정 정보 */
    @CreationTimestamp @Column(name = "create_date", updatable = false, nullable = false)
    private LocalDateTime createDate;

    @UpdateTimestamp @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /** 로그인 관련 */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "password_changed_date")
    private LocalDateTime passwordChangedDate;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    /** 프로필 이미지(첨부파일) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_id",
            foreignKey = @ForeignKey(name = "fk_admin_profile_image")) // 선택
    private AttachFile profileImage;

    /** 신규 저장 시 기본값 */
    @PrePersist
    public void prePersist() {
        if (role == null || role.isBlank()) role = "ROLE_STAFF";
        if (status == null || status.isBlank()) status = "TEMPORARY";
        if (employeeType == null) employeeType = EmployeeType.STAFF;
        if (workLocation == null || workLocation.isBlank()) workLocation = "N"; // 기본 나루관
        failedLoginCount = 0;
        accountLockedUntil = null;
    }

    /** 현재 잠김 상태인지 (LOCKED && 해제시각 미래) */
    public boolean isLockedNow() {
        return "LOCKED".equalsIgnoreCase(status)
                && accountLockedUntil != null
                && LocalDateTime.now().isBefore(accountLockedUntil);
    }

    /** 로그인 실패 처리: 임계 도달 시 LOCKED 전환 */
    public void markLoginFail(int maxFailedAttempts, int lockMinutes) {
        if ("INACTIVE".equalsIgnoreCase(status)) return;
        failedLoginCount++;
        if (failedLoginCount >= maxFailedAttempts) {
            status = "LOCKED";
            accountLockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
        }
    }

    /** 로그인 성공 처리: LOCK 해제 → ACTIVE, 실패카운트 초기화 */
    public void markLoginSuccess() {
        failedLoginCount = 0;
        if ("LOCKED".equalsIgnoreCase(status)) {
            accountLockedUntil = null;
            status = "ACTIVE";
        }
        lastLogin = LocalDateTime.now();
    }

    /** 잠금 정보만 해제 */
    public void clearLockOnly() {
        failedLoginCount = 0;
        accountLockedUntil = null;
    }

    /** 암호 변경(이미 암호화된 값 제공) */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedDate = LocalDateTime.now();
    }

    /**
     * 비밀번호 재설정 성공 시 상태 정책
     * - LOCKED → ACTIVE
     * - TEMPORARY/INACTIVE/ACTIVE → 그대로 유지
     */
    public void applyPasswordResetPolicyPreserveTemporary() {
        this.failedLoginCount = 0;
        this.accountLockedUntil = null;
        if ("LOCKED".equalsIgnoreCase(this.status)) {
            this.status = "ACTIVE";
        }
    }

    public boolean canLogin() { return "ACTIVE".equalsIgnoreCase(this.status); }
}
