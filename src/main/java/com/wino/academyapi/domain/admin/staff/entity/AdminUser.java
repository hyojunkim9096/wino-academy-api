// src/main/java/com/wino/academyapi/domain/admin/staff/entity/AdminUser.java
package com.wino.academyapi.domain.admin.staff.entity;

import com.wino.academyapi.domain.file.entity.AttachFile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate; // ✅ DATE 타입 매핑
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "admin_user_info", indexes = {
        @Index(name = "idx_work_location", columnList = "work_location"),
        @Index(name = "idx_admin_status", columnList = "status"),
        @Index(name = "uk_admin_user_user_id", columnList = "user_id", unique = true)
})
public class AdminUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 120)
    private String userId;

    @Column(name = "password", nullable = false, length = 200)
    private String password;

    @Column(name = "user_name", nullable = false, length = 80)
    private String userName;

    // ✅ DB의 DATE 타입과 매핑됨
    @Column(name = "birthdate")
    private LocalDate birthdate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;

    @Column(name = "email", unique = true, length = 160)
    private String email;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @Column(name = "work_location", length = 100)
    private String workLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_type", nullable = false, length = 40)
    private EmployeeType employeeType;

    @Column(name = "role", nullable = false, length = 40)
    private String role;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @CreationTimestamp
    @Column(name = "create_date", updatable = false, nullable = false)
    private LocalDateTime createDate;

    @UpdateTimestamp
    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "password_changed_date")
    private LocalDateTime passwordChangedDate;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_id")
    private AttachFile profileImage;

    @PrePersist
    public void prePersist() {
        if (role == null || role.isBlank()) role = "ROLE_STAFF";
        if (status == null || status.isBlank()) status = "TEMPORARY";
        if (employeeType == null) employeeType = EmployeeType.STAFF;
        if (workLocation == null || workLocation.isBlank()) workLocation = "N";
        failedLoginCount = 0;
        accountLockedUntil = null;
    }

    public boolean isLockedNow() {
        return "LOCKED".equalsIgnoreCase(status)
                && accountLockedUntil != null
                && LocalDateTime.now().isBefore(accountLockedUntil);
    }

    public void markLoginFail(int maxFailedAttempts, int lockMinutes) {
        if ("INACTIVE".equalsIgnoreCase(status)) return;
        failedLoginCount++;
        if (failedLoginCount >= maxFailedAttempts) {
            status = "LOCKED";
            accountLockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
        }
    }

    public void markLoginSuccess() {
        failedLoginCount = 0;
        if ("LOCKED".equalsIgnoreCase(status)) {
            accountLockedUntil = null;
            status = "ACTIVE";
        }
        lastLogin = LocalDateTime.now();
    }

    public void clearLockOnly() {
        failedLoginCount = 0;
        accountLockedUntil = null;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedDate = LocalDateTime.now();
    }

    public void applyPasswordResetPolicyPreserveTemporary() {
        this.failedLoginCount = 0;
        this.accountLockedUntil = null;
        if ("LOCKED".equalsIgnoreCase(this.status)) {
            this.status = "ACTIVE";
        }
    }

    public boolean canLogin() { return "ACTIVE".equalsIgnoreCase(this.status); }
}