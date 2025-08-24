// src/main/java/com/wino/academyapi/domain/securityrule/entity/SecurityRule.java
package com.wino.academyapi.domain.securityrule.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "security_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="http_method", length=16)
    private String httpMethod; // null=모든 메서드

    @Column(nullable=false, length=255)
    private String pattern;    // 경로 패턴

    @Enumerated(EnumType.STRING)
    @Column(name="access_type", nullable=false, length=32)
    private SecurityRuleAccessType accessType;

    @Column(name="authorities_csv", length=1000)
    private String authoritiesCsv;

    @Column(name="order_index", nullable=false)
    private Integer orderIndex;

    @Column(nullable=false)
    private Boolean enabled;

    @Column(length=500)
    private String remark;
}
