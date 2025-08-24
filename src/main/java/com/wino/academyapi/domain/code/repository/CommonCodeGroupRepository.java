// src/main/java/com/wino/academyapi/domain/code/repository/CommonCodeGroupRepository.java
package com.wino.academyapi.domain.code.repository;

import com.wino.academyapi.domain.code.entity.CommonCodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommonCodeGroupRepository extends JpaRepository<CommonCodeGroup, Long> {
    Optional<CommonCodeGroup> findByGroupCode(String groupCode);
    boolean existsByGroupCode(String groupCode);
    void deleteByGroupCode(String groupCode);
}
