// src/main/java/com/wino/academyapi/domain/student/memo/entity/StudentMemo.java
package com.wino.academyapi.domain.student.memo.entity;

import com.wino.academyapi.domain.student.entity.Student;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 학생 메모(1:N)
 * - 최신 메모 1건을 요약에 보여주되, 기록은 누적 보관
 * - 삭제/수정 가능(트리거가 히스토리 테이블에 적재)
 */
@Entity
@Table(name = "student_memo",
        indexes = {
                @Index(name = "idx_sm_student_time", columnList = "student_id, created_at"),
                @Index(name = "idx_sm_pinned", columnList = "student_id, pinned, created_at")
        })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학생 FK */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** 메모 본문 */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 상단 고정 여부(선택) */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    /** 작성/수정 메타 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}