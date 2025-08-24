// src/main/java/com/wino/academyapi/domain/auth/repository/RoleMenuByCodeRepository.java
package com.wino.academyapi.domain.auth.repository;

import com.wino.academyapi.domain.auth.entity.RoleMenuByCode;
import com.wino.academyapi.domain.auth.entity.RoleMenuByCodeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuByCodeRepository extends JpaRepository<RoleMenuByCode, RoleMenuByCodeId> {
    List<RoleMenuByCode> findByRoleCode(String roleCode);
    void deleteByRoleCode(String roleCode);
}
