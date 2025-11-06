// src/main/java/com/wino/academyapi/domain/enduser/entity/EndUserStudentMap.java
package com.wino.academyapi.domain.enduser.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * end_user_student_map — 엔드유저 ↔ 학생 1:1 권장 매핑
 *  - PK(user_id), student_id UNIQUE
 *  - 조인 엔티티 없이 단순 키 보관 (양방향 연관 불필요)
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "end_user_student_map", uniqueConstraints = {
        @UniqueConstraint(name = "uq_student_unique_user", columnNames = "student_id")
})
public class EndUserStudentMap {

    /** PK = user_id (end_user.id) */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;
}