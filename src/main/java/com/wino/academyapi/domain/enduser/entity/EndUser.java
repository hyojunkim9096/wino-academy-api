// src/main/java/com/wino/academyapi/domain/enduser/entity/EndUser.java
package com.wino.academyapi.domain.enduser.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * end_user — 학생/보호자 공통 계정
 * - user_type: STUDENT/GUARDIAN
 * - login_id: 로컬 로그인 ID (NULL 허용, UNIQUE)
 * - password_hash: 해시(알고리즘은 password_algo 참고)
 * - ✅ [수정] 1:1 매핑 (studentMap, guardianMap) 추가
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "end_user", indexes = {
        @Index(name = "idx_end_user_type_status", columnList = "user_type, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_end_user_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uq_end_user_email", columnNames = "email")
})
public class EndUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_type", nullable = false, length = 16)
    private String userType; // STUDENT/GUARDIAN

    @Column(name = "login_id", length = 120)
    private String loginId;  // UNIQUE NULL (MySQL 은 NULL 중복 허용)

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "password_algo", length = 40)
    private String passwordAlgo; // ex) bcrypt

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // ACTIVE...

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // ✅ [신규] 학생 매핑 (1:1)
    //
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private EndUserStudentMap studentMap;

    // ✅ [신규] 보호자 매핑 (1:1)
    //
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private EndUserGuardianMap guardianMap;


    // ✅ DB default 가 없을 때도 안전하게 타임스탬프 확보
    @PrePersist
    void onCreate() {
        if (status == null || status.isBlank()) status = "ACTIVE";
        final LocalDateTime now = LocalDateTime.now();
        createdAt = (createdAt == null ? now : createdAt);
        updatedAt = (updatedAt == null ? now : updatedAt);
    }
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}