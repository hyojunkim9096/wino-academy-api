// src/main/java/com/wino/academyapi/domain/guardian/entity/Guardian.java
package com.wino.academyapi.domain.guardian.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** DDL: guardian 테이블 매핑 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "guardian", indexes = {
        @Index(name = "idx_guardian_name", columnList = "name"),
        @Index(name = "idx_guardian_push", columnList = "push_user_key")
})
public class Guardian {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name="push_user_key", length = 120)
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

    @CreationTimestamp
    @Column(name="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name="updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name="updated_by")
    private Long updatedBy;

    @PrePersist
    public void prePersist() {
        if (preferSms && (phone == null || phone.isBlank())) preferSms = false;
        if (preferEmail && (email == null || email.isBlank())) preferEmail = false;
    }
}