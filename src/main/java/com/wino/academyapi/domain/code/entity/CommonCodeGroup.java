// src/main/java/com/wino/academyapi/domain/code/entity/CommonCodeGroup.java
package com.wino.academyapi.domain.code.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 공통코드 "그룹" 엔티티
 *
 * 🔧 이번 수정의 핵심
 *  1) 길이 불일치로 인한 스키마 변경 시도 차단
 *     - DB: group_code VARCHAR(64) → 엔티티도 length=64 로 통일
 *     - DB: name VARCHAR(128), description VARCHAR(255) 로 통일
 *     - (이전 코드의 length=60/100/200 때문에 Hibernate가 ALTER 시도 → FK 제약으로 실패)
 *  2) 유니크 제약 이름을 DB와 일치
 *     - DB 제약명: uk_group_code → 엔티티 @Table(uniqueConstraints=...) 도 동일 이름
 *
 * 참고) dev 프로필에서 ddl-auto=update 인 경우, 엔티티 정의와 DB 스키마가 다르면
 *       Hibernate가 ALTER 를 시도합니다. FK 가 걸린 컬럼은 ALTER 가 실패하면서
 *       부팅 자체가 막히므로, 반드시 스키마와 엔티티의 정의(길이/타입/제약)를 맞춥니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "common_code_group",
        // ⚠️ DB에 이미 존재하는 유니크 제약명과 동일하게 맞춰두면
        //    update 시 불필요한 제약 생성/변경 시도를 줄일 수 있습니다.
        uniqueConstraints = @UniqueConstraint(name = "uk_group_code", columnNames = "group_code")
)
public class CommonCodeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DB: VARCHAR(64) NOT NULL  — 길이 반드시 64 로 맞춤
    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;   // 예: BOARD_TYPE

    // DB: VARCHAR(128) NOT NULL — 길이 128 로 맞춤
    @Column(nullable = false, length = 128)
    private String name;

    // DB: VARCHAR(255) NULL — 길이 255 로 맞춤
    @Column(length = 255)
    private String description;

    // DB: INT NOT NULL DEFAULT 0
    // 기본값은 DB에서도 잡혀 있지만, 애플리케이션 단에서도 null 방지를 위해 not null 로 둠
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // DB: TINYINT(1) NOT NULL DEFAULT 1
    // Hibernate 에서는 boolean 으로 매핑해도 무방 (MySQLDialect 가 tinyint(1)로 처리)
    @Column(nullable = false)
    private boolean enabled;

    // 선택) 애플리케이션 기본값 보정 — 새 엔티티 생성 시 null 방지
    @PrePersist
    private void applyDefaults() {
        // sortOrder 기본값 0
        // enabled 기본값 true
        // (DB에도 DEFAULT 가 있으나, 코드상에서도 명시적으로 보정해두면 안전)
        if (!this.enabled) {
            // enabled 가 boolean 이라 null 개념이 없지만,
            // 생성 시 명시적으로 false 로 두지 않았다면 true 로 초기화하는 패턴을 원하면 사용
            // 현재 필드는 primitive 이므로 별도 보정 없이도 기본 false 임.
            // 필요시 아래 라인을 활성화:
            // this.enabled = true;
        }
    }
}
