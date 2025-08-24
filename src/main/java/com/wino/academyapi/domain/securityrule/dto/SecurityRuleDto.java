// src/main/java/com/wino/academyapi/domain/securityrule/dto/SecurityRuleDto.java
package com.wino.academyapi.domain.securityrule.dto;

import com.wino.academyapi.domain.securityrule.entity.SecurityRuleAccessType;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityRuleDto {
    private Long id;
    private String httpMethod;
    private String pattern;
    private SecurityRuleAccessType accessType;
    private String authoritiesCsv;
    private Integer orderIndex;
    private Boolean enabled;
    private String remark;
}
