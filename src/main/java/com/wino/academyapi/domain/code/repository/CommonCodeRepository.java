// src/main/java/com/wino/academyapi/domain/code/repository/CommonCodeRepository.java
package com.wino.academyapi.domain.code.repository;

import com.wino.academyapi.domain.code.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {
    List<CommonCode> findByGroupCodeOrderBySortOrderAscCodeAsc(String groupCode);
    Optional<CommonCode> findByGroupCodeAndCode(String groupCode, String code);
    boolean existsByGroupCodeAndCode(String groupCode, String code);
    void deleteByGroupCodeAndCode(String groupCode, String code);
}
