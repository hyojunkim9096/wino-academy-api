// src/main/java/com/wino/academyapi/domain/consult/entity/ConsultNote.java
package com.wino.academyapi.domain.consult.entity;

import com.wino.academyapi.domain.student.entity.Student;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** DDL: consult_note (상담 본문) */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name="consult_note", indexes = {
        @Index(name="idx_cn_student_time", columnList = "student_id, consult_at"),
        @Index(name="idx_cn_writer_time", columnList = "writer_id, consult_at"),
        @Index(name="idx_cn_followup", columnList = "next_followup_at, student_id")
})
public class ConsultNote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학생 FK (CASCADE) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="student_id", nullable=false,
            foreignKey=@ForeignKey(name="fk_cn_student"))
    private Student student;

    /** 작성자 — admin_user_info(id). 엔티티 연결 대신 숫자 보관 */
    @Column(name="writer_id")
    private Long writerId;

    @Column(name="homeroom_ok", nullable = false)
    private boolean homeroomOk;

    @Column(name="consult_method", nullable = false, length = 32)
    private String consultMethod;

    @Column(name="consult_type", nullable = false, length = 32)
    private String consultType;

    @Column(name="title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name="content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(name="action_plan", columnDefinition = "LONGTEXT")
    private String actionPlan;

    @Column(name="consult_at", nullable = false)
    private LocalDateTime consultAt;

    @Column(name="next_followup_at")
    private LocalDateTime nextFollowupAt;

    @Column(name="visibility_role", length = 40)
    private String visibilityRole;

    @Column(name="use_yn", nullable = false)
    private boolean useYn;

    @CreationTimestamp
    @Column(name="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name="updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name="updated_by")
    private Long updatedBy;
}