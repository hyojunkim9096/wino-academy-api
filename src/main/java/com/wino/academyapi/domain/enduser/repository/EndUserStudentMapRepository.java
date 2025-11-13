// src/main/java/com/wino/academyapi/domain/enduser/repository/EndUserStudentMapRepository.java
package com.wino.academyapi.domain.enduser.repository;

import com.wino.academyapi.domain.enduser.entity.EndUserStudentMap;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

public interface EndUserStudentMapRepository extends JpaRepository<EndUserStudentMap, Long> {

    Optional<EndUserStudentMap> findByStudentId(Long studentId);

    List<EndUserStudentMap> findByStudentIdIn(Collection<Long> studentIds);

    // PK(userId)로 조회는 JpaRepository의 findById로 대체 가능
    // Optional<EndUserStudentMap> findByUserId(Long userId);

    @Transactional //
    void deleteByStudentId(Long studentId);
}