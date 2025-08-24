// src/main/java/com/wino/academyapi/domain/code/entity/CommonCodeGroup.java
package com.wino.academyapi.domain.code.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "common_code_group",
        uniqueConstraints = @UniqueConstraint(name = "uk_cc_group", columnNames = "group_code"))
public class CommonCodeGroup {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="group_code", nullable=false, length=60)
    private String groupCode;     // 예: BOARD_TYPE

    @Column(nullable=false, length=100)
    private String name;

    @Column(length=200)
    private String description;

    @Column(name="sort_order", nullable=false)
    private int sortOrder;

    @Column(nullable=false)
    private boolean enabled;
}
