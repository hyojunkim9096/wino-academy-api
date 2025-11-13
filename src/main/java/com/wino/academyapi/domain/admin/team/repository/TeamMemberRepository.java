package com.wino.academyapi.domain.admin.team.repository;

import com.wino.academyapi.domain.admin.team.entity.TeamMember;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("""
        select tm from TeamMember tm
         where tm.team.id = :teamId
         order by case when tm.roleInTeam='LEADER' then 0 else 1 end, tm.id
    """)
    List<TeamMember> findAllForTeam(@Param("teamId") Long teamId);

    @Query("""
        select tm from TeamMember tm
         where tm.team.id = :teamId and tm.activeYn='Y' and tm.roleInTeam='LEADER'
    """)
    Optional<TeamMember> findActiveLeader(@Param("teamId") Long teamId);

    @Query("select count(tm) from TeamMember tm where tm.team.id = :teamId and tm.activeYn='Y'")
    long countActive(@Param("teamId") Long teamId);

    boolean existsByTeamIdAndAdminId(Long teamId, Long adminId);

    // ✅ [신규] 특정 adminId가 속한 모든 '활성' 멤버십 조회 (권한 확인용)
    @Query("""
        select tm from TeamMember tm
         where tm.admin.id = :adminId
           and tm.activeYn = 'Y'
    """)
    List<TeamMember> findAllByAdminId(@Param("adminId") Long adminId);

    // ✅ [신규] 특정 팀(teamId)에 속한 모든 활성 멤버의 adminId 목록 조회 (팀장 권한용)
    @Query("""
        select tm.admin.id from TeamMember tm
         where tm.team.id = :teamId
           and tm.activeYn = 'Y'
    """)
    List<Long> findActiveAdminIdsByTeamId(@Param("teamId") Long teamId);
}