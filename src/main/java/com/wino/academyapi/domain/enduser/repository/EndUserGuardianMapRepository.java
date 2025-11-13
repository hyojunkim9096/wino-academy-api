// src/main/java/com/wino/academyapi/domain/enduser/repository/EndUserGuardianMapRepository.java
package com.wino.academyapi.domain.enduser.repository;

import com.wino.academyapi.domain.enduser.entity.EndUserGuardianMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EndUserGuardianMapRepository extends JpaRepository<EndUserGuardianMap, Long> {

    Optional<EndUserGuardianMap> findByGuardianId(Long guardianId);

    List<EndUserGuardianMap> findByGuardianIdIn(Collection<Long> guardianIds);

    // PK(userId)로 조회는 JpaRepository의 findById로 대체 가능
    // Optional<EndUserGuardianMap> findByUserId(Long userId);

    void deleteByGuardianId(Long guardianId);
}