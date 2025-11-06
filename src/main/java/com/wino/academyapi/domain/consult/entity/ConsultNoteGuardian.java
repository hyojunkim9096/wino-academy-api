// src/main/java/com/wino/academyapi/domain/consult/entity/ConsultNoteGuardian.java
package com.wino.academyapi.domain.consult.entity;

import com.wino.academyapi.domain.guardian.entity.Guardian;
import jakarta.persistence.*;
import lombok.*;

/** DDL: consult_note_guardian (참석 보호자 스냅샷) */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name="consult_note_guardian", indexes = {
        @Index(name="idx_cng_consult", columnList = "consult_id")
})
public class ConsultNoteGuardian {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상담 PK (CASCADE) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="consult_id", nullable=false,
            foreignKey=@ForeignKey(name="fk_cng_consult"))
    private ConsultNote consult;

    /** 실제 Guardian FK — 삭제/변경 고려하여 NULL 허용 + SET NULL */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="guardian_id",
            foreignKey=@ForeignKey(name="fk_cng_guardian"))
    private Guardian guardian;

    @Column(name="relation_code", length = 32)
    private String relationCode;

    @Column(name="name_snapshot", length = 100)
    private String nameSnapshot;

    @Column(name="phone_snapshot", length = 20)
    private String phoneSnapshot;

    @Column(name="present_yn", nullable = false)
    private boolean presentYn;

    @Column(name="memo", length = 255)
    private String memo;
}