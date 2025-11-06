// src/main/java/com/wino/academyapi/domain/code/entity/CommonCode.java
package com.wino.academyapi.domain.code.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 공통코드 "항목" 엔티티
 *
 * 🔧 이번 수정의 핵심(실DB 스키마와 100% 일치):
 *  1) 컬럼 길이 맞춤
 *     - group_code : VARCHAR(64)
 *     - code       : VARCHAR(64)
 *     - name       : VARCHAR(128)
 *  2) 제약/인덱스 이름 맞춤
 *     - UNIQUE (group_code, code) → 이름: uk_group_code_code
 *     - INDEX (group_code, sort_order) → 이름: idx_item_sort
 *  3) 메타 컬럼 타입 맞춤
 *     - DB는 TINYTEXT → JPA에선 @Lob 미사용, columnDefinition="TINYTEXT" 로 정확히 매핑
 *
 * 참고)
 * - 이미 운영/개발 DB에 FK(fk_common_code_group)가 존재하고, group_code 길이가 64이므로
 *   엔티티 길이를 다르게 두면 Hibernate가 ALTER 를 시도하다가 FK 충돌로 실패합니다.
 * - 관계 매핑(@ManyToOne) 대신 문자열 FK를 유지합니다. (이미 DB에 물리 FK가 존재하고,
 *   논리 매핑까지 추가해 DDL/스키마 변경 시도를 유발하지 않도록 단순 매핑 유지)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "common_code",
        uniqueConstraints = {
                // DB의 제약명과 동일하게 두어 Hibernate가 불필요한 제약 재생성을 시도하지 않도록 함
                @UniqueConstraint(name = "uk_group_code_code", columnNames = {"group_code", "code"})
        },
        indexes = {
                // DB의 인덱스와 동일한 이름/구성
                @Index(name = "idx_item_sort", columnList = "group_code, sort_order")
        }
)
public class CommonCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DB: VARCHAR(64) NOT NULL — 길이 64 로 통일
    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;   // 그룹 코드(FK 대상 컬럼)

    // DB: VARCHAR(64) NOT NULL — 길이 64 로 통일
    @Column(nullable = false, length = 64)
    private String code;        // 예: BOARD_TABLE, BOARD_GALLERY

    // DB: VARCHAR(128) NOT NULL — 길이 128 로 통일
    @Column(nullable = false, length = 128)
    private String name;

    // DB: INT NOT NULL DEFAULT 0
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // DB: TINYINT(1) NOT NULL DEFAULT 1  (Hibernate에서 boolean ↔ tinyint(1))
    @Column(nullable = false)
    private boolean enabled;

    // DB: TINYTEXT NULL — @Lob 사용 시 LONGTEXT 로 가려고 하므로 정확히 지정
    @Column(name = "meta_json", columnDefinition = "TINYTEXT")
    private String metaJson;

    // (선택) 기본값 보정이 필요하면 @PrePersist 로 처리 가능
    @PrePersist
    private void applyDefaults() {
        // sortOrder 기본값 0, enabled 기본값 true 등의 앱 레벨 보정이 필요하면 여기서 적용
        // 예) this.enabled = (this.enabled || true);
    }
}
