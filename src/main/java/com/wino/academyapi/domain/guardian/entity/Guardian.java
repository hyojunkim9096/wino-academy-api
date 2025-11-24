// src/main/java/com/wino/academyapi/domain/guardian/entity/Guardian.java
package com.wino.academyapi.domain.guardian.entity;

import com.wino.academyapi.domain.enduser.entity.EndUserGuardianMap;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * DDL: guardian 테이블 매핑
 * - EndUserGuardianMap 을 통한 1:1 계정 매핑 포함
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "guardian",
        indexes = {
                @Index(name = "idx_guardian_name", columnList = "name"),
                @Index(name = "idx_guardian_push", columnList = "push_user_key"),
                // DDL에 idx_guardian_user가 있다면 반영
                @Index(name = "idx_guardian_user", columnList = "user_id")
        }
)
public class Guardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 프로필 필드들
    @Column(name = "name", nullable = false, length = 100)
    private String name;

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

    @Lob
    @Column(name = "memo")
    private String memo;

    // 감사 필드
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    /**
     * DDL의 user_id 컬럼 매핑
     * - EndUserGuardianMap 으로 관리되므로 insertable=false, updatable=false
     * - 조회용/참고용으로만 사용
     */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    /**
     * 엔드유저 계정 매핑 (1:1)
     * - EndUserGuardianMap.user_id 가 guardian.user_id 와 매핑된다고 가정
     */
    @OneToOne(mappedBy = "guardian", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private EndUserGuardianMap endUserMap;

    @PrePersist
    public void prePersist() {
        // 연락처/이메일 없는 상태에서 수신 설정 true인 경우 방어적으로 false로 변경
        if (preferSms && (phone == null || phone.isBlank())) {
            preferSms = false;
        }
        if (preferEmail && (email == null || email.isBlank())) {
            preferEmail = false;
        }
    }
}
