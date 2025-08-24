// src/main/java/com/wino/academyapi/domain/securityrule/repository/SecurityRuleDeletedRepository.java
package com.wino.academyapi.domain.securityrule.repository;

import com.wino.academyapi.domain.securityrule.entity.SecurityRuleDeleted;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityRuleDeletedRepository extends JpaRepository<SecurityRuleDeleted, Long> {
    List<SecurityRuleDeleted> findAllByOrderByDeletedAtDesc();
}
