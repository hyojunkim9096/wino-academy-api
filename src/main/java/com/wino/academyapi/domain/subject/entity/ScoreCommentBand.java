// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/entity/ScoreCommentBand.java
// ----------------------------------------------------------------------------
// 점수 구간(라벨/min/max/코멘트/알림/정렬) — DDL과 일치
// - 트리거/서비스에서 min<=max, 구간 겹침 방지 권장
// ============================================================================

package com.wino.academyapi.domain.subject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name="score_comment_band",
        indexes = @Index(name="idx_comment_band_sort", columnList="comment_set_id, sort_order")
)
public class ScoreCommentBand {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="comment_set_id", nullable=false)
    private Long commentSetId;             // FK(score_comment_set.id)

    @Column(length=40, nullable=false)
    private String label;                  // A/B/C 등 구간 라벨

    @Column(name="min_score", nullable=false)
    private int minScore;

    @Column(name="max_score", nullable=false)
    private int maxScore;

    @Lob
    @Column(name="comment_template", nullable=false)
    private String commentTemplate;        // 코멘트 템플릿(멀티라인 가능)

    @Column(name="notify_sms",   columnDefinition="TINYINT(1)")
    private boolean notifySms;

    @Column(name="notify_email", columnDefinition="TINYINT(1)")
    private boolean notifyEmail;

    @Column(name="notify_push",  columnDefinition="TINYINT(1)")
    private boolean notifyPush;

    @Column(name="sort_order", nullable=false)
    private int sortOrder;

    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}