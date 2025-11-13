// src/main/java/com/wino/academyapi/domain/guardian/entity/Guardian.java
package com.wino.academyapi.domain.guardian.entity;

// ✅ [신규] 매핑 엔티티 import
import com.wino.academyapi.domain.enduser.entity.EndUserGuardianMap;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * DDL: guardian 테이블 매핑
 * - ✅ [수정] 1:1 매핑 (endUserMap) 추가
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "guardian", indexes = {
        @Index(name = "idx_guardian_name", columnList = "name"),
        @Index(name = "idx_guardian_push", columnList = "push_user_key")
        // ✅ [수정] DDL에 idx_guardian_user가 추가되었으므로,
        //    엔티티에도 추가해주는 것이 좋습니다.
        , @Index(name = "idx_guardian_user", columnList = "user_id")
})
public class Guardian {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ... (기존 필드: name, phone, email, ... memo 등) ...
    @Column(name="name", nullable = false, length = 100)
    private String name;
    @Column(name="phone", length = 20)
    private String phone;
    @Column(name="email", length = 160)
    private String email;
    @Column(name="prefer_sms", nullable = false)
    private boolean preferSms;
    @Column(name="prefer_email", nullable = false)
    private boolean preferEmail;
    @Column(name="prefer_push", nullable = false)
    private boolean preferPush;
    @Column(name="push_user_key", length = 255) // DDL 255
    private String pushUserKey;
    @Column(name="postal_code", length = 10)
    private String postalCode;
    @Column(name="address", length = 255)
    private String address;
    @Column(name="detail_address", length = 255)
    private String detailAddress;
    @Lob
    @Column(name="memo")
    private String memo;

    // ... (감사 필드: createdAt, updatedAt, updatedBy) ...
    @CreationTimestamp
    @Column(name="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name="updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name="updated_by")
    private Long updatedBy;

    // ✅ [신규] DDL의 user_id 컬럼 매핑
    // (endUserMap으로 관리되므로, 이 필드는 참고용/단방향 FK용으로만 사용)
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    // ✅ [신규] 엔드유저 계정 매핑 (1:1)
    //
    @OneToOne(mappedBy = "guardian", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private EndUserGuardianMap endUserMap;

    @PrePersist
    public void prePersist() {
        if (preferSms && (phone == null || phone.isBlank())) preferSms = false;
        if (preferEmail && (email == null || email.isBlank())) preferEmail = false;
    }
}