// src/main/java/com/wino/academyapi/domain/securityrule/repository/SecurityRuleRepository.java
package com.wino.academyapi.domain.securityrule.repository;

import com.wino.academyapi.domain.securityrule.entity.SecurityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityRuleRepository extends JpaRepository<SecurityRule, Long> {
    List<SecurityRule> findByEnabledTrueOrderByOrderIndexAscIdAsc();
    // enabled=true/false 로 필터링 조회 (비활성=deleted 대용)
    List<SecurityRule> findAllByEnabled(Boolean enabled);
}
