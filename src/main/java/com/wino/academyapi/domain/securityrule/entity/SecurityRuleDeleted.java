// src/main/java/com/wino/academyapi/domain/securityrule/entity/SecurityRuleDeleted.java
package com.wino.academyapi.domain.securityrule.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "security_rule_deleted")
public class SecurityRuleDeleted {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ruleId;
    private String httpMethod;

    @Column(nullable = false)
    private String pattern;

    /** SecurityRuleAccessType.name() 그대로 저장 */
    @Column(nullable = false, length = 50)
    private String accessType;

    @Column(length = 500)
    private String authoritiesCsv;

    private Integer orderIndex;
    private Boolean enabled;
    private String remark;

    @Column(nullable = false)
    private LocalDateTime deletedAt;

    private String deletedBy;
}
