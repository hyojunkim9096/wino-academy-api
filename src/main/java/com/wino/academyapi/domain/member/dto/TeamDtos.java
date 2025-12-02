// src/main/java/com/wino/academyapi/domain/member/dto/TeamDtos.java
package com.wino.academyapi.domain.member.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Team 도메인 DTO 묶음 */
public class TeamDtos {

    // ===== 요청 DTO =====
    public record UpsertTeamReq(
            String teamCode,
            String teamName,
            String description,
            String workLocation,
            String status,        // ACTIVE/INACTIVE
            Long leaderAdminId    // nullable
    ) {}

    public record AddMemberReq(
            Long adminId,
            String roleInTeam     // MEMBER/LEADER
    ) {}

    public record UpdateMemberReq(
            String roleInTeam,    // MEMBER/LEADER
            String activeYn       // Y/N
    ) {}

    public record SetLeaderReq(Long adminId) {}

    // ===== 응답 DTO =====
    public record TeamSummaryRes(
            Long id,
            String teamCode,
            String teamName,
            String description,
            String workLocation,
            String status,
            Long leaderAdminId,
            String leaderName,
            long activeMemberCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record TeamMemberRes(
            Long id,
            Long adminId,
            String userName,
            String workLocation,
            String roleInTeam,
            String activeYn,
            LocalDateTime joinedAt,
            LocalDateTime leftAt
    ) {}

    public record TeamDetailRes(
            Long id,
            String teamCode,
            String teamName,
            String description,
            String workLocation,
            String status,
            Long leaderAdminId,
            String leaderName,
            long activeMemberCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<TeamMemberRes> members
    ) {}
}