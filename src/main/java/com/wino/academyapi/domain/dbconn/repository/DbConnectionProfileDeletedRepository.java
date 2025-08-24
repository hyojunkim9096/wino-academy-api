// src/main/java/com/wino/academyapi/domain/dbconn/repository/DbConnectionProfileDeletedRepository.java
package com.wino.academyapi.domain.dbconn.repository;

import com.wino.academyapi.domain.dbconn.entity.DbConnectionProfileDeleted;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * 삭제된 DB 연결 프로필 이력 Repository
 *
 * 목적
 *  - 삭제 직전의 프로필 스냅샷을 조회합니다.
 *  - 화면의 "삭제 이력" 그리드에 사용되며, env 별/통합 정렬을 지원합니다.
 *
 * 정렬 규칙(권장)
 *  - envCode 오름차순 → deletedAt 내림차순 → id 내림차순
 *    (동일 시각 충돌 시 id 역순으로 안정 정렬)
 */
public interface DbConnectionProfileDeletedRepository extends JpaRepository<DbConnectionProfileDeleted, Long> {

    /** 단일 env 의 삭제 이력 (최신순) */
    List<DbConnectionProfileDeleted> findByEnvCodeOrderByDeletedAtDesc(String envCode);

    /** 통합(모든 env) 삭제 이력 — env asc, deletedAt desc, id desc */
    @Query("""
        select d
          from DbConnectionProfileDeleted d
         order by d.envCode asc,
                  d.deletedAt desc,
                  d.id desc
    """)
    List<DbConnectionProfileDeleted> findAllOrdered();

    /** 여러 env 묶음으로 조회 (예: ["dev","prod"]) — env asc, deletedAt desc, id desc */
    @Query("""
        select d
          from DbConnectionProfileDeleted d
         where d.envCode in :envs
         order by d.envCode asc,
                  d.deletedAt desc,
                  d.id desc
    """)
    List<DbConnectionProfileDeleted> findByEnvCodeInOrdered(Collection<String> envs);
}
