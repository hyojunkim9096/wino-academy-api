// src/main/java/com/wino/academyapi/domain/member/controller/admin/TeamAdminController.java
package com.wino.academyapi.domain.member.controller.admin;

import com.wino.academyapi.domain.member.dto.TeamDtos.*;
import com.wino.academyapi.domain.member.service.TeamAdminService;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Team 관리 API
 * base: /api/admin/teams
 */
@RestController
@RequestMapping("/api/admin/teams")
public class TeamAdminController {

    private final TeamAdminService service;

    public TeamAdminController(TeamAdminService service) {
        this.service = service;
    }

    // 목록
    @GetMapping
    public Page<TeamSummaryRes> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workLocation,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("teamName")));
        return service.search(status, workLocation, keyword, pageable);
    }

    // 상세
    @GetMapping("/{id}")
    public TeamDetailRes detail(@PathVariable Long id,
                                @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.getDetail(id, activeOnly);
    }

    // 생성
    @PostMapping
    public Map<String, Object> create(@RequestBody UpsertTeamReq req) {
        Long id = service.create(req);
        return Map.of("id", id);
    }

    // 수정
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody UpsertTeamReq req) {
        service.update(id, req);
        return Map.of("id", id, "result", "ok");
    }

    // 삭제
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("result", "ok");
    }

    // 멤버 목록(편의)
    @GetMapping("/{id}/members")
    public List<TeamMemberRes> members(@PathVariable Long id,
                                       @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.getDetail(id, activeOnly).members();
    }

    // 멤버 추가
    @PostMapping("/{id}/members")
    public Map<String, Object> addMember(@PathVariable Long id, @RequestBody AddMemberReq req) {
        Long memberId = service.addMember(id, req);
        return Map.of("memberId", memberId);
    }

    // 멤버 수정
    @PutMapping("/{id}/members/{memberId}")
    public Map<String, Object> updateMember(@PathVariable Long id,
                                            @PathVariable Long memberId,
                                            @RequestBody UpdateMemberReq req) {
        service.updateMember(id, memberId, req);
        return Map.of("result", "ok");
    }

    // 멤버 삭제
    @DeleteMapping("/{id}/members/{memberId}")
    public Map<String, Object> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        service.removeMember(id, memberId);
        return Map.of("result", "ok");
    }

    // 팀장 지정
    @PostMapping("/{id}/leader")
    public Map<String, Object> setLeader(@PathVariable Long id, @RequestBody SetLeaderReq req) {
        service.setLeader(id, req.adminId());
        return Map.of("result", "ok");
    }
}