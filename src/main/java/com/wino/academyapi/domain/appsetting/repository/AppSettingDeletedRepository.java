// src/main/java/com/wino/academyapi/domain/appsetting/repository/AppSettingDeletedRepository.java
package com.wino.academyapi.domain.appsetting.repository;

import com.wino.academyapi.domain.appsetting.entity.AppSettingDeleted;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 삭제 이력 조회/업서트 보조용 리포지토리
 */
public interface AppSettingDeletedRepository extends JpaRepository<AppSettingDeleted, Long> {

    /** 삭제 이력 목록 최신순 */
    List<AppSettingDeleted> findAllByOrderByDeletedAtDesc();

    /** 같은 key 의 가장 최근 삭제 이력 1건 (업서트에 사용) */
    Optional<AppSettingDeleted> findTopByKeyOrderByDeletedAtDesc(String key);
}
