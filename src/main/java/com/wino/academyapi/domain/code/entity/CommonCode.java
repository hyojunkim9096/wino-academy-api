// src/main/java/com/wino/academyapi/domain/code/entity/CommonCode.java
package com.wino.academyapi.domain.code.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "common_code",
        uniqueConstraints = @UniqueConstraint(name = "uk_cc_item", columnNames = {"group_code","code"}),
        indexes = @Index(name="idx_cc_group", columnList="group_code"))
public class CommonCode {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="group_code", nullable=false, length=60)
    private String groupCode;     // FK(논리) - 그룹 코드

    @Column(nullable=false, length=80)
    private String code;          // 예: TABLE, GALLERY

    @Column(nullable=false, length=100)
    private String name;

    @Column(name="sort_order", nullable=false)
    private int sortOrder;

    @Column(nullable=false)
    private boolean enabled;

    @Lob
    @Column(name="meta_json")
    private String metaJson;
}
