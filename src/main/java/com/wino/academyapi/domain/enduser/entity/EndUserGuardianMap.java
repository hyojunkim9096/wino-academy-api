// src/main/java/com/wino/academyapi/domain/enduser/entity/EndUserGuardianMap.java
package com.wino.academyapi.domain.enduser.entity;

import com.wino.academyapi.domain.guardian.entity.Guardian;
import jakarta.persistence.*;
import lombok.*;

/**
 * 엔드유저 ↔ 보호자 1:1 매핑 (end_user_guardian_map)
 * - DDL 스키마에 따라 PK를 user_id로 사용합니다.
 * - @MapsId를 사용해 user_id를 PK이자 FK로 매핑합니다.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "end_user_guardian_map")
public class EndUserGuardianMap {

    /** PK = user_id (end_user.id) */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * 보호자 엔티티와 1:1 매핑
     * - DDL의 UNIQUE KEY(guardian_id) 제약이 1:1을 보장합니다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_id", nullable = false, unique = true) // DDL
    private Guardian guardian;

    /**
     * 엔드유저 엔티티와 1:1 매핑 (PK 매핑)
     * - @MapsId: 이 관계의 대상(EndUser)의 ID를 이 엔티티의 PK(userId)로 사용합니다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private EndUser user;
}