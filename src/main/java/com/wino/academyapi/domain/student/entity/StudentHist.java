// src/main/java/com/wino/academyapi/domain/student/entity/StudentHist.java
package com.wino.academyapi.domain.student.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 학생 변경 이력(student_hist)
 * - INSERT/UPDATE 시 트리거나 프로시저로 적재된다고 가정
 * - ref_id별 version 1..n 순으로 누적
 * - event_by 는 admin_user_info.id (AdminUser.id)와 매칭
 */
@Entity
@Table(name = "student_hist",
        indexes = {
                @Index(name = "ix_student_hist_ev", columnList = "event_type, event_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_student_hist_ref_ver", columnNames = {"ref_id", "version"})
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentHist {

    /** PK(히스토리) */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 student.id */
    @Column(name = "ref_id", nullable = false)
    private Long refId;

    /** ref_id별 1..n 버전 */
    @Column(nullable = false)
    private Integer version;

    /** I/U/D/SNAP */
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    /** 이벤트 시각 */
    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    /** 이벤트 수행자(@app_user_id) — admin_user_info.id */
    @Column(name = "event_by")
    private Long eventBy;
}