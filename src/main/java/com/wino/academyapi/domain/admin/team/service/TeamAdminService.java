package com.wino.academyapi.domain.admin.team.service;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.admin.team.dto.TeamDtos.*;
import com.wino.academyapi.domain.admin.team.entity.TeamGroup;
import com.wino.academyapi.domain.admin.team.entity.TeamMember;
import com.wino.academyapi.domain.admin.team.repository.TeamGroupRepository;
import com.wino.academyapi.domain.admin.team.repository.TeamMemberRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Team 관리 서비스
 * - 단일 팀장 보장(서비스) + DB 트리거 이중 가드
 * - 쓰기 진입 시 dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote())
 */
@Service
public class TeamAdminService {

    private final TeamGroupRepository teamGroupRepo;
    private final TeamMemberRepository teamMemberRepo;
    private final AdminUserRepository adminUserRepo;
    private final DbSessionVars dbSessionVars; // ⚠️ Bean 주입 (정적 호출 금지)

    public TeamAdminService(TeamGroupRepository teamGroupRepo,
                            TeamMemberRepository teamMemberRepo,
                            AdminUserRepository adminUserRepo,
                            DbSessionVars dbSessionVars) {
        this.teamGroupRepo = teamGroupRepo;
        this.teamMemberRepo = teamMemberRepo;
        this.adminUserRepo = adminUserRepo;
        this.dbSessionVars = dbSessionVars;
    }

    // ===== 조회 =====

    @Transactional(readOnly = true)
    public Page<TeamSummaryRes> search(String status, String workLocation, String keyword, Pageable pageable) {
        // ✅ [N+1 성능 개선] (기존 수정 사항 유지)
        Page<TeamSummaryRes> page = teamGroupRepo.searchWithSummary(
                emptyToNull(status),
                emptyToNull(workLocation),
                emptyToNull(keyword),
                pageable
        );
        return page;
    }

    @Transactional(readOnly = true)
    public TeamDetailRes getDetail(Long id, boolean activeOnly) {
        TeamGroup tg = teamGroupRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("팀이 존재하지 않습니다."));
        long cnt = teamMemberRepo.countActive(tg.getId());
        String leaderName = tg.getLeader() != null ? safeUserName(tg.getLeader()) : null;

        List<TeamMember> rows = teamMemberRepo.findAllForTeam(tg.getId());
        List<TeamMemberRes> members = rows.stream()
                .filter(m -> !activeOnly || "Y".equalsIgnoreCase(m.getActiveYn()))
                .map(this::toMemberRes)
                .collect(Collectors.toList());

        return new TeamDetailRes(
                tg.getId(), tg.getTeamCode(), tg.getTeamName(), tg.getDescription(),
                tg.getWorkLocation(), tg.getStatus(),
                tg.getLeader() != null ? tg.getLeader().getId() : null,
                leaderName, cnt, tg.getCreatedAt(), tg.getUpdatedAt(), members
        );
    }

    // ===== 팀 생성/수정/삭제 =====

    @Transactional
    public Long create(UpsertTeamReq req) {
        injectSessionVars(); // 동일 트랜잭션 커넥션에 @app_user_id/@event_note 설정

        TeamGroup tg = new TeamGroup();
        // ✅ [수정] 생성 시 팀 코드(teamCode) 설정 (필수)
        tg.setTeamCode(Objects.requireNonNull(trim(req.teamCode()), "teamCode 필수"));
        tg.setTeamName(Objects.requireNonNull(trim(req.teamName()), "teamName 필수"));
        tg.setDescription(trim(req.description()));
        tg.setWorkLocation(trim(req.workLocation()));
        tg.setStatus(defaultIfBlank(req.status(), "ACTIVE"));

        AdminUser meRef = currentUserRefOrNull();
        if (meRef != null) {
            tg.setCreatedBy(meRef);
            tg.setUpdatedBy(meRef);
        }

        if (req.leaderAdminId() != null) {
            AdminUser leader = adminUserRepo.findById(req.leaderAdminId())
                    .orElseThrow(() -> new NoSuchElementException("리더 대상 사용자가 존재하지 않습니다."));
            tg.setLeader(leader);
        }

        teamGroupRepo.save(tg);

        // 리더 지정 시 멤버십 반영
        if (tg.getLeader() != null) {
            upsertLeaderMembership(tg, tg.getLeader());
        }

        return tg.getId();
    }

    @Transactional
    public void update(Long id, UpsertTeamReq req) {
        injectSessionVars();

        TeamGroup tg = teamGroupRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("팀이 존재하지 않습니다."));

        // ✅ [수정] 정책 일관성을 위해 teamCode는 수정하지 않습니다.
        // tg.setTeamCode(trim(req.teamCode())); //

        tg.setTeamName(Objects.requireNonNull(trim(req.teamName()), "teamName 필수"));
        tg.setDescription(trim(req.description()));
        tg.setWorkLocation(trim(req.workLocation()));
        tg.setStatus(defaultIfBlank(req.status(), "ACTIVE"));

        AdminUser meRef = currentUserRefOrNull();
        if (meRef != null) tg.setUpdatedBy(meRef);

        Long newLeaderId = req.leaderAdminId();
        Long oldLeaderId = tg.getLeader() != null ? tg.getLeader().getId() : null;

        if (!Objects.equals(newLeaderId, oldLeaderId)) {
            AdminUser newLeader = null;
            if (newLeaderId != null) {
                newLeader = adminUserRepo.findById(newLeaderId)
                        .orElseThrow(() -> new NoSuchElementException("리더 대상 사용자가 존재하지 않습니다."));
            }
            tg.setLeader(newLeader);

            if (newLeader != null) {
                upsertLeaderMembership(tg, newLeader);
            } else {
                // 리더 해제 → 기존 활성 리더가 본인이면 강등
                teamMemberRepo.findActiveLeader(tg.getId()).ifPresent(prev -> {
                    if (tg.getLeader() == null) prev.setRoleInTeam("MEMBER");
                });
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        injectSessionVars();
        TeamGroup tg = teamGroupRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("팀이 존재하지 않습니다."));
        teamGroupRepo.delete(tg); // 멤버는 CASCADE, 이력은 트리거로 보존
    }

    // ===== 멤버 관리 =====

    @Transactional
    public Long addMember(Long teamId, AddMemberReq req) {
        injectSessionVars();

        TeamGroup tg = teamGroupRepo.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("팀이 존재하지 않습니다."));
        AdminUser au = adminUserRepo.findById(req.adminId())
                .orElseThrow(() -> new NoSuchElementException("대상 사용자가 존재하지 않습니다."));

        if (teamMemberRepo.existsByTeamIdAndAdminId(teamId, au.getId())) {
            throw new IllegalStateException("이미 팀에 소속된 사용자입니다.");
        }

        TeamMember tm = new TeamMember();
        tm.setTeam(tg);
        tm.setAdmin(au);
        tm.setRoleInTeam(defaultIfBlank(req.roleInTeam(), "MEMBER"));
        tm.setActiveYn("Y");

        AdminUser meRef = currentUserRefOrNull();
        if (meRef != null) {
            tm.setCreatedBy(meRef);
            tm.setUpdatedBy(meRef);
        }

        teamMemberRepo.save(tm);

        if ("LEADER".equalsIgnoreCase(tm.getRoleInTeam())) {
            teamMemberRepo.findActiveLeader(tg.getId()).ifPresent(prev -> {
                if (!Objects.equals(prev.getAdmin().getId(), au.getId())) prev.setRoleInTeam("MEMBER");
            });
            tg.setLeader(au);
        }

        return tm.getId();
    }

    @Transactional
    public void updateMember(Long teamId, Long memberId, UpdateMemberReq req) {
        injectSessionVars();

        TeamMember tm = teamMemberRepo.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("팀 멤버가 존재하지 않습니다."));
        if (!Objects.equals(tm.getTeam().getId(), teamId)) {
            throw new IllegalArgumentException("팀/멤버 매칭이 올바르지 않습니다.");
        }

        TeamGroup tg = tm.getTeam(); //

        if (req.roleInTeam() != null) {
            String role = req.roleInTeam().toUpperCase();
            tm.setRoleInTeam(role);
            if ("LEADER".equals(role)) {
                teamMemberRepo.findActiveLeader(teamId).ifPresent(prev -> {
                    if (!Objects.equals(prev.getId(), tm.getId())) prev.setRoleInTeam("MEMBER");
                });
                tg.setLeader(tm.getAdmin());
            } else {
                //
                if (tg.getLeader() != null && Objects.equals(tg.getLeader().getId(), tm.getAdmin().getId())) {
                    tg.setLeader(null);
                }
            }
        }

        if (req.activeYn() != null) {
            String v = "Y".equalsIgnoreCase(req.activeYn()) ? "Y" : "N";

            if ("N".equals(v)) {
                //
                tm.setLeftAt(java.time.LocalDateTime.now());

                // ✅ [요청 2]
                //
                if (tg.getLeader() != null && Objects.equals(tg.getLeader().getId(), tm.getAdmin().getId())) {
                    tg.setLeader(null); //
                }
            } else {
                //
                tm.setLeftAt(null);
            }
            tm.setActiveYn(v);
        }
    }

    @Transactional
    public void removeMember(Long teamId, Long memberId) {
        injectSessionVars();

        TeamMember tm = teamMemberRepo.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("팀 멤버가 존재하지 않습니다."));
        if (!Objects.equals(tm.getTeam().getId(), teamId)) {
            throw new IllegalArgumentException("팀/멤버 매칭이 올바르지 않습니다.");
        }

        boolean wasLeader = "LEADER".equalsIgnoreCase(tm.getRoleInTeam());
        Long leaderId = tm.getTeam().getLeader() != null ? tm.getTeam().getLeader().getId() : null;

        teamMemberRepo.delete(tm);

        if (wasLeader && Objects.equals(leaderId, tm.getAdmin().getId())) {
            TeamGroup tg = teamGroupRepo.findById(teamId).orElseThrow();
            tg.setLeader(null);
        }
    }

    @Transactional
    public void setLeader(Long teamId, Long adminId) {
        injectSessionVars();

        TeamGroup tg = teamGroupRepo.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("팀이 존재하지 않습니다."));
        AdminUser au = adminUserRepo.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("사용자가 존재하지 않습니다."));

        // 기존 리더 강등
        teamMemberRepo.findActiveLeader(teamId).ifPresent(prev -> {
            if (!Objects.equals(prev.getAdmin().getId(), adminId)) prev.setRoleInTeam("MEMBER");
        });

        // 멤버십 upsert + 리더 지정
        upsertLeaderMembership(tg, au);
        tg.setLeader(au);
    }

    // ===== 내부 유틸 =====

    /* //  N+1
    private TeamSummaryRes toSummary(TeamGroup tg) {
        ...
    }
    */

    private TeamMemberRes toMemberRes(TeamMember m) {
        return new TeamMemberRes(
                m.getId(),
                m.getAdmin().getId(),
                safeUserName(m.getAdmin()),
                m.getAdmin().getWorkLocation(),
                m.getRoleInTeam(),
                m.getActiveYn(),
                m.getJoinedAt(),
                m.getLeftAt()
        );
    }

    private void upsertLeaderMembership(TeamGroup tg, AdminUser leader) {
        List<TeamMember> all = teamMemberRepo.findAllForTeam(tg.getId());
        TeamMember tm = all.stream()
                .filter(x -> Objects.equals(x.getAdmin().getId(), leader.getId()))
                .findFirst()
                .orElse(null);
        if (tm == null) {
            tm = new TeamMember();
            tm.setTeam(tg);
            tm.setAdmin(leader);
            tm.setActiveYn("Y");
            AdminUser meRef = currentUserRefOrNull();
            if (meRef != null) {
                tm.setCreatedBy(meRef);
                tm.setUpdatedBy(meRef);
            }
        }
        tm.setRoleInTeam("LEADER");
        tm.setActiveYn("Y"); //
        tm.setLeftAt(null); //
        teamMemberRepo.save(tm);
    }

    /** 동일 트랜잭션/커넥션에 @app_user_id, @event_note를 설정 */
    private void injectSessionVars() {
        Long uid = AppUserContext.getUserId(); // ThreadLocal
        String note = AppUserContext.getNote();
        dbSessionVars.setAppVars(uid, note);   // ⚠️ Bean 인스턴스 호출
    }

    private AdminUser currentUserRefOrNull() {
        Long uid = AppUserContext.getUserId();
        if (uid == null || uid <= 0) return null;
        return adminUserRepo.findById(uid).orElse(null);
    }

    private String safeUserName(AdminUser u) { try { return u.getUserName(); } catch (Exception e) { return null; } }
    private String trim(String s) { return s == null ? null : s.trim(); }
    private String defaultIfBlank(String s, String d) { return (s == null || s.trim().isEmpty()) ? d : s.trim(); }
    private String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }
}